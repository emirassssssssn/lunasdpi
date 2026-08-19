package com.lunasdev.lunasdpi.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledDiscordApp(
    val packageName: String,
    val label: String,
    val detected: Boolean,
)

object DiscordAppScanner {
    fun detected(pm: PackageManager): List<InstalledDiscordApp> {
        return launchable(pm).filter { it.detected }
    }

    fun launchable(pm: PackageManager): List<InstalledDiscordApp> {
        val seen = linkedSetOf<String>()
        val apps = mutableListOf<InstalledDiscordApp>()
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        installed.forEach { info ->
            val pkg = info.packageName ?: return@forEach
            if (!seen.add(pkg)) {
                return@forEach
            }
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && pm.getLaunchIntentForPackage(pkg) == null) {
                return@forEach
            }
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            apps += InstalledDiscordApp(
                packageName = pkg,
                label = label,
                detected = DiscordClients.looksLikeClient(pkg, label),
            )
        }
        return apps.sortedWith(
            compareByDescending<InstalledDiscordApp> { it.detected }
                .thenBy { it.label.lowercase() },
        )
    }
}
