package com.lunasdev.lunasdpi.plugin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lunasdev.lunasdpi.R
import java.util.concurrent.ConcurrentHashMap

class PluginNotifier(context: Context) {
    private val app = context.applicationContext
    private val lastShown = ConcurrentHashMap<String, Long>()
    private val hourCount = ConcurrentHashMap<String, Pair<Long, Int>>()

    fun show(pluginId: String, pluginName: String, title: String, text: String) {
        if (!allow(pluginId)) return
        ensureChannel()
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { pluginName }.take(40))
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(120)))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        val id = (pluginId.hashCode() and 0x7fffffff) + 7000
        runCatching { NotificationManagerCompat.from(app).notify(id, notification) }
    }

    private fun allow(pluginId: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastShown.put(pluginId, now) ?: 0L
        if (now - previous < 30_000L) return false
        val bucket = hourCount[pluginId]
        if (bucket == null || now - bucket.first > 60 * 60 * 1000L) {
            hourCount[pluginId] = now to 1
            return true
        }
        if (bucket.second >= 8) return false
        hourCount[pluginId] = bucket.first to (bucket.second + 1)
        return true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                app.getString(R.string.plugins_notify_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = app.getString(R.string.plugins_notify_channel_desc)
            },
        )
    }

    companion object {
        const val CHANNEL = "luna_plugins"
    }
}
