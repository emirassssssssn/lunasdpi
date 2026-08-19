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
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.lunasdev.lunasdpi.MainActivity
import com.lunasdev.lunasdpi.R
import java.util.concurrent.atomic.AtomicBoolean

class DiscordWatchService : Service() {
    @Volatile
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running.set(true)
        ensureChannel(this)
        startWatchForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            inForeground = false
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startWatchForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        inForeground = false
        running.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startWatchForeground() {
        if (inForeground) {
            return
        }
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
    }

    companion object {
        const val ACTION_STOP = "com.lunasdev.lunasdpi.action.STOP_DISCORD_WATCH"
        const val CHANNEL_ID = "luna_dpi_discord_watch"
        const val NOTIFICATION_ID = 44
        private val running = AtomicBoolean(false)

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.discord_watch_channel),
                    NotificationManager.IMPORTANCE_MIN,
                )
                channel.setShowBadge(false)
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        fun start(context: Context): Boolean {
            if (!running.compareAndSet(false, true)) {
                return true
            }
            val started = runCatching {
                val intent = Intent(context, DiscordWatchService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)
            if (!started) {
                running.set(false)
            }
            return started
        }

        fun stop(context: Context) {
            running.set(false)
            runCatching { context.stopService(Intent(context, DiscordWatchService::class.java)) }
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
