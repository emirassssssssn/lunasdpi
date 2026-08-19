package com.lunasdev.lunasdpi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.lunasdev.lunasdpi.LunaApplication
import com.lunasdev.lunasdpi.MainActivity
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.EngineSnapshot
import com.lunasdev.lunasdpi.data.model.Ipv6Mode
import com.lunasdev.lunasdpi.data.model.PerAppMode
import com.lunasdev.lunasdpi.data.model.UserFacingError
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.vpn.NativeEngine
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DpiVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var engine: NativeEngine? = null
    private var statsJob: Job? = null
    private var rulesJob: Job? = null
    private var hostsJob: Job? = null
    private var startedAt = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEngine("Stopped by user")
                return START_NOT_STICKY
            }
            else -> startEngine()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopEngine(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        app().vpnState.setError(
            UserFacingError(
                title = "VPN permission revoked",
                message = "Android revoked the VPN interface.\n\nStart protection again and grant permission if asked.",
                technicalDetails = "VpnService.onRevoke()",
            ),
        )
        stopEngine("VPN permission revoked")
        super.onRevoke()
    }

    private fun startEngine() {
        val state = app().vpnState
        state.clearError()
        state.setPhase(VpnPhase.CONNECTING)
        startForegroundInternal()
        scope.launch {
            try {
                val config = app().settings.current().validated()
                val rules = app().rules.current()
                val builder = Builder()
                    .setSession(getString(R.string.app_name))
                    .setMtu(config.mtu)
                    .addAddress(NativeEngine.TUN_ADDRESS, 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(NativeEngine.TUN_DNS)
                    .setBlocking(false)
                if (config.ipv6Mode == Ipv6Mode.BLOCK) {
                    runCatching { builder.addAddress(NativeEngine.TUN_IPV6, 128) }
                    runCatching { builder.addRoute(InetAddress.getByName("::"), 0) }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    builder.setMetered(false)
                }
                when (config.perAppMode) {
                    PerAppMode.ALL -> Unit
                    PerAppMode.SELECTED -> {
                        config.perAppPackages.forEach { pkg ->
                            runCatching { builder.addAllowedApplication(pkg) }
                        }
                    }
                    PerAppMode.EXCLUDED -> {
                        config.perAppPackages.forEach { pkg ->
                            runCatching { builder.addDisallowedApplication(pkg) }
                        }
                        runCatching { builder.addDisallowedApplication(packageName) }
                    }
                }
                if (config.perAppMode != PerAppMode.EXCLUDED) {
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
                val established = builder.establish()
                if (established == null) {
                    state.setError(
                        UserFacingError(
                            title = "VPN could not be started",
                            message = "Android did not allow the VPN interface to be established.\n\nTry granting VPN permission again.",
                            technicalDetails = "Builder.establish() returned null",
                        ),
                    )
                    stopSelf()
                    return@launch
                }
                tun = established
                val fd = established.detachFd()
                tun = null
                val native = NativeEngine { socketFd -> protect(socketFd) }
                val systemDns = app().vpnController.systemDnsServers()
                    .mapNotNull { raw ->
                        runCatching { InetAddress.getByName(raw).hostAddress }.getOrNull()
                    }
                    .filter { it.contains('.') }
                if (!native.start(fd, config, rules, systemDns, app().hosts.current())) {
                    state.setError(
                        UserFacingError(
                            title = "Engine failed to start",
                            message = "The local processing engine could not start. Check diagnostics for details.",
                            technicalDetails = native.stats().lastError.ifBlank { "nativeStart returned false" },
                        ),
                    )
                    native.stop()
                    stopSelf()
                    return@launch
                }
                engine = native
                startedAt = System.currentTimeMillis()
                registerNetworkCallback(native)
                watchRules(native)
                watchHosts(native)
                state.setPhase(VpnPhase.CONNECTED)
                statsJob = scope.launch {
                    while (isActive) {
                        val stats = native.stats()
                        val uptime = (System.currentTimeMillis() - startedAt) / 1000L
                        state.setSnapshot(
                            EngineSnapshot(
                                packetsProcessed = stats.packetsProcessed,
                                packetsModified = stats.packetsModified,
                                packetsDropped = stats.packetsDropped,
                                bytesIn = stats.bytesIn,
                                bytesOut = stats.bytesOut,
                                dnsQueries = stats.dnsQueries,
                                activeTcp = stats.activeTcp,
                                activeUdp = stats.activeUdp,
                                nativeErrors = stats.nativeErrors,
                                lastError = stats.lastError,
                                currentStrategy = stats.currentStrategy,
                                engineAlive = stats.engineAlive,
                                tunActive = true,
                                uptimeSeconds = uptime,
                            ),
                        )
                        delay(1000)
                    }
                }
            } catch (t: Throwable) {
                state.setError(
                    UserFacingError(
                        title = "VPN could not be started",
                        message = "The local VPN interface failed while starting.",
                        technicalDetails = t.stackTraceToString(),
                    ),
                )
                stopSelf()
            }
        }
    }

    private fun stopEngine(reason: String?) {
        rulesJob?.cancel()
        rulesJob = null
        hostsJob?.cancel()
        hostsJob = null
        statsJob?.cancel()
        statsJob = null
        unregisterNetworkCallback()
        runCatching { engine?.stop() }
        engine = null
        runCatching { tun?.close() }
        tun = null
        app().vpnState.setSnapshot(EngineSnapshot())
        if (app().vpnState.phase.value != VpnPhase.ERROR) {
            app().vpnState.setPhase(VpnPhase.DISCONNECTED)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (reason != null) {
            android.util.Log.i(TAG, reason)
        }
    }

    private fun watchRules(native: NativeEngine) {
        rulesJob?.cancel()
        rulesJob = scope.launch {
            var skipInitial = true
            app().rules.rules.distinctUntilChanged().collect { list ->
                if (skipInitial) {
                    skipInitial = false
                    return@collect
                }
                native.updateRules(list)
            }
        }
    }

    private fun watchHosts(native: NativeEngine) {
        hostsJob?.cancel()
        hostsJob = scope.launch {
            var skipInitial = true
            app().hosts.mappings.collect { list ->
                if (skipInitial) {
                    skipInitial = false
                    return@collect
                }
                native.updateHosts(list)
            }
        }
    }

    private fun registerNetworkCallback(native: NativeEngine) {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                native.onNetworkChanged()
            }

            override fun onLost(network: Network) {
                native.onNetworkChanged()
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { cm.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun startForegroundInternal() {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DpiVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(launch)
            .setOngoing(true)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun app(): LunaApplication = application as LunaApplication

    companion object {
        const val ACTION_START = "com.lunasdev.lunasdpi.action.START"
        const val ACTION_STOP = "com.lunasdev.lunasdpi.action.STOP"
        private const val CHANNEL_ID = "luna_dpi_vpn"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "LunasDpiVpn"
    }
}
