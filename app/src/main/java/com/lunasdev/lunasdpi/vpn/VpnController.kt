package com.lunasdev.lunasdpi.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import com.lunasdev.lunasdpi.data.SettingsRepository
import com.lunasdev.lunasdpi.data.VpnStateRepository
import com.lunasdev.lunasdpi.data.model.UserFacingError
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.service.DpiVpnService
import kotlinx.coroutines.flow.StateFlow

class VpnController(
    private val context: Context,
    private val state: VpnStateRepository,
    private val settings: SettingsRepository,
) {
    val phase: StateFlow<VpnPhase> = state.phase

    fun prepareIntent(): Intent? = VpnService.prepare(context)

    fun hasVpnPermission(): Boolean = prepareIntent() == null

    @Volatile
    var startedByWatcher: Boolean = false
        private set

    @Volatile
    private var lastManualStopAt = 0L

    @Volatile
    private var pendingWatcherPermission = false

    suspend fun start(fromWatcher: Boolean = false) {
        if (fromWatcher && System.currentTimeMillis() - lastManualStopAt < MANUAL_STOP_GRACE_MS) {
            return
        }
        state.clearError()
        val prepare = prepareIntent()
        if (prepare != null) {
            pendingWatcherPermission = fromWatcher || pendingWatcherPermission
            state.setPhase(VpnPhase.REQUESTING_PERMISSION)
            return
        }
        val watcher = fromWatcher || pendingWatcherPermission
        pendingWatcherPermission = false
        startedByWatcher = watcher
        state.setPhase(VpnPhase.CONNECTING)
        val intent = Intent(context, DpiVpnService::class.java).setAction(DpiVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(fromWatcher: Boolean = false) {
        if (fromWatcher && !startedByWatcher) {
            return
        }
        if (!fromWatcher) {
            lastManualStopAt = System.currentTimeMillis()
        }
        startedByWatcher = false
        state.setPhase(VpnPhase.STOPPING)
        context.startService(Intent(context, DpiVpnService::class.java).setAction(DpiVpnService.ACTION_STOP))
    }

    fun onPermissionDenied() {
        state.setError(
            UserFacingError(
                title = "VPN could not be started",
                message = "Android did not allow the VPN interface to be established.\n\nTry granting VPN permission again.",
                technicalDetails = "VpnService.prepare() was cancelled by the user",
            ),
        )
    }

    fun systemDnsServers(): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: Network = cm.activeNetwork ?: return emptyList()
        val caps: NetworkCapabilities = cm.getNetworkCapabilities(network) ?: return emptyList()
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return emptyList()
        }
        val lp = cm.getLinkProperties(network) ?: return emptyList()
        return lp.dnsServers.mapNotNull { it.hostAddress }
    }

    companion object {
        const val LOCAL_DISCLAIMER =
            "No remote VPN server is used. Traffic is processed locally on this device."
        private const val MANUAL_STOP_GRACE_MS = 12_000L
    }
}
