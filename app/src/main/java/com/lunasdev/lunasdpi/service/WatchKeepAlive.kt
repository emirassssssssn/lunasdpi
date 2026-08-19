package com.lunasdev.lunasdpi.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.lunasdev.lunasdpi.LunaApplication
import kotlinx.coroutines.launch

object WatchKeepAlive {
    const val ACTION_RESTART = "com.lunasdev.lunasdpi.action.RESTART_DISCORD_WATCH"
    private const val REQUEST_CODE = 47
    private const val WATCHDOG_MS = 120_000L
    private const val RESTART_MS = 1_000L

    fun schedule(context: Context) {
        setAlarm(context, WATCHDOG_MS)
    }

    fun scheduleSoon(context: Context) {
        setAlarm(context, RESTART_MS)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(pending(context)) }
    }

    private fun setAlarm(context: Context, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = SystemClock.elapsedRealtime() + delayMs
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context))
        }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, WatchRestartReceiver::class.java).setAction(ACTION_RESTART)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class WatchRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != WatchKeepAlive.ACTION_RESTART) {
            return
        }
        val app = context.applicationContext as? LunaApplication ?: return
        val pending = goAsync()
        app.applicationScope.launch {
            try {
                if (app.settings.current().autoStartOnDiscord) {
                    DiscordWatchService.start(app)
                    WatchKeepAlive.schedule(app)
                } else {
                    WatchKeepAlive.cancel(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
