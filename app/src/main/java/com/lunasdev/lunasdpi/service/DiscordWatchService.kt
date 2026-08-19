package com.lunasdev.lunasdpi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.lunasdev.lunasdpi.MainActivity
import com.lunasdev.lunasdpi.R
import java.util.concurrent.atomic.AtomicBoolean

class DiscordWatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val keepAlive = object : Runnable {
        override fun run() {
            startWatchForeground()
            WatchKeepAlive.schedule(this@DiscordWatchService)
            handler.postDelayed(this, PING_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        userStop.set(false)
        running.set(true)
        ensureChannel(this)
        startWatchForeground()
        handler.removeCallbacks(keepAlive)
        handler.postDelayed(keepAlive, PING_MS)
        WatchKeepAlive.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userStop.set(true)
            handler.removeCallbacks(keepAlive)
            WatchKeepAlive.cancel(this)
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        userStop.set(false)
        running.set(true)
        startWatchForeground()
        WatchKeepAlive.schedule(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (userStop.get()) {
            return
        }
        startWatchForeground()
        WatchKeepAlive.scheduleSoon(this)
        runCatching {
            val restart = Intent(applicationContext, DiscordWatchService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                applicationContext.startForegroundService(restart)
            } else {
                applicationContext.startService(restart)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(keepAlive)
        val restart = !userStop.get()
        running.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        if (restart) {
            WatchKeepAlive.scheduleSoon(this)
        }
    }

    private fun startWatchForeground() {
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.lunasdev.lunasdpi.action.STOP_DISCORD_WATCH"
        const val CHANNEL_ID = "luna_dpi_discord_watch_v2"
        const val NOTIFICATION_ID = 44
        private const val LEGACY_CHANNEL_ID = "luna_dpi_discord_watch"
        private const val PING_MS = 45_000L
        private val running = AtomicBoolean(false)
        private val userStop = AtomicBoolean(false)

        fun isRunning(): Boolean = running.get()

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(NotificationManager::class.java)
                runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.discord_watch_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
                channel.setShowBadge(false)
                channel.setSound(null, null)
                channel.enableVibration(false)
                manager.createNotificationChannel(channel)
            }
        }

        fun buildNotification(context: Context): Notification {
            val launch = PendingIntent.getActivity(
                context,
                4,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.discord_watch_notification_title))
                .setContentText(context.getString(R.string.discord_watch_notification_text))
                .setContentIntent(launch)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }

        fun start(context: Context): Boolean {
            userStop.set(false)
            val app = context.applicationContext
            val started = runCatching {
                val intent = Intent(app, DiscordWatchService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
                true
            }.getOrDefault(false)
            if (started) {
                running.set(true)
                WatchKeepAlive.schedule(app)
            }
            return started
        }

        fun stop(context: Context) {
            userStop.set(true)
            running.set(false)
            val app = context.applicationContext
            WatchKeepAlive.cancel(app)
            runCatching { app.stopService(Intent(app, DiscordWatchService::class.java)) }
        }

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) start(context) else stop(context)
        }
    }
}

object BatteryExemption {
    fun isIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun request(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
