package com.lunasdev.lunasdpi.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lunasdev.lunasdpi.LunaApplication
import com.lunasdev.lunasdpi.MainActivity
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.DiscordClients
import com.lunasdev.lunasdpi.data.ForegroundKind
import com.lunasdev.lunasdpi.data.model.VpnPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppLaunchWatcher : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var configJob: Job? = null

    @Volatile
    private var watchEnabled = false

    @Volatile
    private var selectedPackage = ""

    @Volatile
    private var autoStopOnLeave = true

    @Volatile
    private var selfForeground = false

    private val inspectDebounced = Runnable {
        val pkg = pendingPackage ?: return@Runnable
        pendingPackage = null
        inspectPackage(pkg)
    }

    @Volatile
    private var pendingPackage: String? = null

    private val stopRunnable = Runnable {
        val app = applicationContext as? LunaApplication ?: return@Runnable
        app.vpnController.stop(fromWatcher = true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!DiscordWatchService.start(this)) {
            startKeepAliveForeground()
            selfForeground = true
        }
        val app = applicationContext as? LunaApplication ?: return
        configJob?.cancel()
        var lastWatchSync: Boolean? = null
        configJob = app.applicationScope.launch {
            app.settings.config.collect { config ->
                watchEnabled = config.autoStartOnDiscord
                selectedPackage = config.autoStartPackage
                autoStopOnLeave = config.autoStopOnDiscordLeave
                if (lastWatchSync != config.autoStartOnDiscord) {
                    lastWatchSync = config.autoStartOnDiscord
                    DiscordWatchService.sync(app, config.autoStartOnDiscord)
                    if (config.autoStartOnDiscord) {
                        WatchKeepAlive.schedule(app)
                    } else {
                        WatchKeepAlive.cancel(app)
                    }
                }
                if (!config.autoStartOnDiscord) {
                    handler.removeCallbacks(stopRunnable)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg == packageName) {
            return
        }
        if (uiVisible && !DiscordClients.shouldWatch(pkg, selectedPackage)) {
            return
        }
        pendingPackage = pkg
        handler.removeCallbacks(inspectDebounced)
        handler.post(inspectDebounced)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        keepWatchAlive()
        return true
    }

    override fun onDestroy() {
        handler.removeCallbacks(inspectDebounced)
        handler.removeCallbacks(stopRunnable)
        configJob?.cancel()
        configJob = null
        if (selfForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            selfForeground = false
        }
        keepWatchAlive()
        super.onDestroy()
    }

    private fun keepWatchAlive() {
        if (!watchEnabled) {
            return
        }
        DiscordWatchService.start(this)
        WatchKeepAlive.scheduleSoon(this)
    }

    private fun inspectPackage(packageName: String) {
        if (!watchEnabled) {
            return
        }
        when (DiscordClients.classifyForeground(listOf(packageName), selectedPackage)) {
            ForegroundKind.Discord -> {
                handler.removeCallbacks(stopRunnable)
                val app = applicationContext as? LunaApplication ?: return
                val phase = app.vpnState.phase.value
                if (phase == VpnPhase.CONNECTED ||
                    phase == VpnPhase.CONNECTING ||
                    phase == VpnPhase.REQUESTING_PERMISSION ||
                    phase == VpnPhase.STOPPING
                ) {
                    return
                }
                app.applicationScope.launch {
                    startProtectionIfNeeded(app)
                }
            }
            ForegroundKind.Transient -> Unit
            ForegroundKind.Other -> {
                if (autoStopOnLeave) {
                    handler.removeCallbacks(stopRunnable)
                    handler.postDelayed(stopRunnable, STOP_GRACE_MS)
                }
            }
        }
    }

    private fun startKeepAliveForeground() {
        DiscordWatchService.ensureChannel(this)
        val notification = DiscordWatchService.buildNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                DiscordWatchService.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(DiscordWatchService.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val EXTRA_START_PROTECTION = "com.lunasdev.lunasdpi.extra.START_PROTECTION"
        private const val CHANNEL_ID = "luna_dpi_autostart"
        private const val NOTIFICATION_ID = 43
        private const val START_DEBOUNCE_MS = 200L
        private const val STOP_GRACE_MS = 700L

        @Volatile
        private var uiVisible = false

        @Volatile
        private var lastAttemptMs = 0L

        fun setUiVisible(visible: Boolean) {
            uiVisible = visible
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AppLaunchWatcher::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val flattened = expected.flattenToString()
            val shortName = expected.flattenToShortString()
            return enabled.split(':').any { raw ->
                val token = raw.trim()
                token.equals(flattened, ignoreCase = true) ||
                    token.equals(shortName, ignoreCase = true) ||
                    ComponentName.unflattenFromString(token)?.equals(expected) == true
            }
        }

        fun openSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val opened = runCatching {
                    context.startActivity(
                        Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(
                                Intent.EXTRA_COMPONENT_NAME,
                                ComponentName(context, AppLaunchWatcher::class.java),
                            )
                        },
                    )
                }.isSuccess
                if (opened) {
                    return
                }
            }
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        suspend fun startProtectionIfNeeded(app: LunaApplication) {
            when (app.vpnState.phase.value) {
                VpnPhase.CONNECTED, VpnPhase.CONNECTING, VpnPhase.REQUESTING_PERMISSION, VpnPhase.STOPPING -> return
                VpnPhase.DISCONNECTED, VpnPhase.ERROR -> Unit
            }
            val now = System.currentTimeMillis()
            if (now - lastAttemptMs < START_DEBOUNCE_MS) {
                return
            }
            lastAttemptMs = now
            if (app.vpnController.hasVpnPermission()) {
                runCatching { app.vpnController.start(fromWatcher = true) }
                return
            }
            runCatching { app.vpnController.start(fromWatcher = true) }
            ProtectionStartRequest.arm()
            val launched = runCatching {
                app.startActivity(
                    Intent(app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(EXTRA_START_PROTECTION, true),
                )
            }.isSuccess
            if (!launched) {
                notifyPermissionNeeded(app)
            }
        }

        private fun notifyPermissionNeeded(app: LunaApplication) {
            createChannel(app)
            val launch = PendingIntent.getActivity(
                app,
                2,
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_START_PROTECTION, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(app.getString(R.string.discord_autostart_notification_title))
                .setContentText(app.getString(R.string.discord_autostart_notification_text))
                .setContentIntent(launch)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
        }

        private fun createChannel(app: LunaApplication) {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    app.getString(R.string.discord_autostart_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}

object ProtectionStartRequest {
    val pending = MutableStateFlow(false)

    fun arm() {
        pending.value = true
    }

    fun consume(): Boolean = pending.compareAndSet(expect = true, update = false)
}
