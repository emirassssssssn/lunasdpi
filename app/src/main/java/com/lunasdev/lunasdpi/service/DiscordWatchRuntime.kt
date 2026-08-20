package com.lunasdev.lunasdpi.service

import android.os.Handler
import android.os.Looper
import com.lunasdev.lunasdpi.LunaApplication
import com.lunasdev.lunasdpi.data.DiscordClients
import com.lunasdev.lunasdpi.data.ForegroundKind
import com.lunasdev.lunasdpi.data.model.DpiConfig
import com.lunasdev.lunasdpi.data.model.VpnPhase
import kotlinx.coroutines.launch

object DiscordWatchRuntime {
    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var selectedPackage: String = ""
        private set

    @Volatile
    var autoStopOnLeave: Boolean = true
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        stopScheduled = false
        val app = host ?: return@Runnable
        app.vpnController.stop(fromWatcher = true)
    }

    @Volatile
    private var host: LunaApplication? = null

    @Volatile
    private var stopScheduled = false

    fun attach(app: LunaApplication) {
        host = app
    }

    fun applyConfig(config: DpiConfig) {
        enabled = config.autoStartOnDiscord
        selectedPackage = config.autoStartPackage
        autoStopOnLeave = config.autoStopOnDiscordLeave
        if (!enabled) {
            cancelStop()
        }
    }

    fun enableUntilConfig() {
        enabled = true
    }

    fun onForeground(packageName: String?) {
        if (!enabled) {
            return
        }
        val app = host ?: return
        if (packageName.isNullOrBlank()) {
            return
        }
        val kind = DiscordClients.classifyForeground(listOf(packageName), selectedPackage)
        when (kind) {
            ForegroundKind.Discord -> {
                cancelStop()
                val phase = app.vpnState.phase.value
                if (phase == VpnPhase.CONNECTED ||
                    phase == VpnPhase.CONNECTING ||
                    phase == VpnPhase.REQUESTING_PERMISSION ||
                    phase == VpnPhase.STOPPING
                ) {
                    return
                }
                app.applicationScope.launch {
                    AppLaunchWatcher.startProtectionIfNeeded(app)
                }
            }
            ForegroundKind.Transient -> Unit
            ForegroundKind.Other -> {
                if (autoStopOnLeave && !stopScheduled) {
                    stopScheduled = true
                    handler.postDelayed(stopRunnable, STOP_GRACE_MS)
                }
            }
        }
    }

    private fun cancelStop() {
        stopScheduled = false
        handler.removeCallbacks(stopRunnable)
    }

    private const val STOP_GRACE_MS = 2_500L
}
