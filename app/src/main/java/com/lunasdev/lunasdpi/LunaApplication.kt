package com.lunasdev.lunasdpi

import android.app.Application
import com.lunasdev.lunasdpi.data.RulesRepository
import com.lunasdev.lunasdpi.data.SettingsRepository
import com.lunasdev.lunasdpi.data.VpnStateRepository
import com.lunasdev.lunasdpi.data.model.DpiConfig
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.plugin.HostsStore
import com.lunasdev.lunasdpi.plugin.PluginHost
import com.lunasdev.lunasdpi.plugin.PluginRegistry
import com.lunasdev.lunasdpi.service.AppLaunchWatcher
import com.lunasdev.lunasdpi.service.DiscordWatchService
import com.lunasdev.lunasdpi.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(app: Application, scope: CoroutineScope) {
    val settings = SettingsRepository(app)
    val rules = RulesRepository(app)
    val vpnState = VpnStateRepository()
    val vpnController = VpnController(app, vpnState, settings)
    val hosts = HostsStore()
    val plugins = PluginHost(app, scope, PluginRegistry(app), rules, vpnController, vpnState, hosts, settings)
    val configState: StateFlow<DpiConfig> = settings.config.stateIn(
        scope,
        SharingStarted.Eagerly,
        DpiConfig(),
    )
    val rulesState: StateFlow<List<DomainRule>> = rules.rules.stateIn(
        scope,
        SharingStarted.Eagerly,
        emptyList(),
    )
}

class LunaApplication : Application() {
    lateinit var container: AppContainer
        private set
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings get() = container.settings
    val rules get() = container.rules
    val vpnState get() = container.vpnState
    val vpnController get() = container.vpnController
    val hosts get() = container.hosts
    val plugins get() = container.plugins
    val configState get() = container.configState
    val rulesState get() = container.rulesState

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)
        container.plugins.start()
        applicationScope.launch {
            configState
                .map { it.autoStartOnDiscord }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled && AppLaunchWatcher.isEnabled(this@LunaApplication)) {
                        DiscordWatchService.start(this@LunaApplication)
                    } else if (!enabled) {
                        DiscordWatchService.stop(this@LunaApplication)
                    }
                }
        }
    }
}
