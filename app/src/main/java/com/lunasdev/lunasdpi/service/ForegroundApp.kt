package com.lunasdev.lunasdpi.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import com.lunasdev.lunasdpi.data.DiscordClients

object ForegroundApp {
    fun usageGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageSettings(context: Context) {
        val flagged = Intent.FLAG_ACTIVITY_NEW_TASK
        val opened = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(flagged)
                },
            )
        }.isSuccess
        if (!opened) {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(flagged),
            )
        }
    }

    fun currentPackage(context: Context): String? {
        val fromWindows = AppLaunchWatcher.latestPackage()
        if (!fromWindows.isNullOrBlank() && !DiscordClients.isTransientUi(fromWindows)) {
            return fromWindows
        }
        if (usageGranted(context)) {
            usagePackage(context)?.let { return it }
        }
        return fromWindows
    }

    private fun usagePackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(end - EVENT_WINDOW_MS, end) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var current: String? = null
        val resume = if (Build.VERSION.SDK_INT >= 29) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        val pause = if (Build.VERSION.SDK_INT >= 29) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg.isNullOrBlank()) {
                continue
            }
            when (event.eventType) {
                resume -> current = pkg
                pause -> if (pkg == current) {
                    current = null
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_STOPPED && pkg == current) {
                        current = null
                    }
                }
            }
        }
        return current
    }

    private const val EVENT_WINDOW_MS = 10 * 60 * 1000L
}
