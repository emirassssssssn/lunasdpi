package com.lunasdev.lunasdpi.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lunasdev.lunasdpi.LunaApplication
import com.lunasdev.lunasdpi.plugin.InstalledPlugin
import com.lunasdev.lunasdpi.plugin.PluginUiPage
import com.lunasdev.lunasdpi.plugin.UnpackedPlugin
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunaApplication
    private val host = app.plugins

    val plugins: StateFlow<List<InstalledPlugin>> = host.registry.records
        .map { records ->
            records.mapNotNull { record ->
                val manifest = host.registry.loadManifest(record.id) ?: return@mapNotNull null
                InstalledPlugin(record = record, manifest = manifest)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending: StateFlow<UnpackedPlugin?> = host.pending
    val banner: StateFlow<String?> = host.message

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _page = MutableStateFlow<PluginUiPage?>(null)
    val settingsPage: StateFlow<PluginUiPage?> = _page.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var watchingSettingsId: String? = null
    private var reloadJob: Job? = null

    init {
        viewModelScope.launch {
            host.runtime.uiReload.collect { pluginId ->
                if (pluginId == watchingSettingsId) {
                    reloadJob?.cancel()
                    reloadJob = viewModelScope.launch {
                        delay(80)
                        loadSettings(pluginId)
                    }
                }
            }
        }
    }

    fun pluginDir(id: String): File = host.registry.pluginDir(id)

    fun log(id: String): String = host.runtime.recentLog(id)

    fun consumeBanner() = host.consumeMessage()

    fun consumeError() {
        _error.value = null
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { host.stageUri(uri) }
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun cancelImport() = host.cancelImport()

    fun confirmImport() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { host.confirmImport() }
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean, granted: List<String>? = null) {
        viewModelScope.launch {
            runCatching { host.setEnabled(id, enabled, granted) }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun uninstall(id: String) {
        viewModelScope.launch {
            runCatching { host.uninstall(id) }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun reload(id: String) {
        viewModelScope.launch {
            runCatching { host.reload(id) }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    fun clearLog(id: String) {
        host.clearLog(id)
    }

    fun loadSettings(id: String) {
        watchingSettingsId = id
        viewModelScope.launch {
            _busy.value = true
            runCatching { host.runtime.settingsPage(id) }
                .onSuccess { page -> _page.value = page }
                .onFailure { error ->
                    _page.value = null
                    _error.value = error.message
                }
            _busy.value = false
        }
    }

    fun changeSetting(pluginId: String, id: String, value: Any, reload: Boolean) {
        viewModelScope.launch {
            runCatching {
                host.runtime.settingChanged(pluginId, id, value)
                if (reload) {
                    _page.value = host.runtime.settingsPage(pluginId)
                }
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }
}
