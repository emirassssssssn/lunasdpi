package com.lunasdev.lunasdpi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.lunasdev.lunasdpi.LunaApplication
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_PRESENT &&
            action != Intent.ACTION_USER_UNLOCKED &&
            action != ACTION_QUICKBOOT
        ) {
            return
        }
        val app = context.applicationContext as? LunaApplication ?: return
        val pending = goAsync()
        app.applicationScope.launch {
            try {
                val config = app.settings.current()
                if (config.autoStartOnDiscord) {
                    DiscordWatchService.start(app)
                    WatchKeepAlive.schedule(app)
                }
                if (!isBootAction(action) || !config.startOnBoot) {
                    return@launch
                }
                if (VpnService.prepare(app) != null) {
                    return@launch
                }
                val service = Intent(app, DpiVpnService::class.java).setAction(DpiVpnService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) {
                    app.startForegroundService(service)
                } else {
                    app.startService(service)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"

        private fun isBootAction(action: String): Boolean {
            return action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == ACTION_QUICKBOOT
        }
    }
}
