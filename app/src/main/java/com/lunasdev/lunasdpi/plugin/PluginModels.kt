package com.lunasdev.lunasdpi.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLUGIN_API_LEVEL = 1

enum class PluginPermission {
    STORAGE,
    UI_SETTINGS,
    RULES_READ,
    RULES_WRITE,
    VPN_READ,
    VPN_CONTROL,
    NOTIFY,
    HOSTS_WRITE,
    APP_READ,
    ;

    fun manifestKey(): String = when (this) {
        STORAGE -> "storage"
        UI_SETTINGS -> "ui.settings"
        RULES_READ -> "rules.read"
        RULES_WRITE -> "rules.write"
        VPN_READ -> "vpn.read"
        VPN_CONTROL -> "vpn.control"
        NOTIFY -> "notify"
        HOSTS_WRITE -> "hosts.write"
        APP_READ -> "app.read"
    }

    companion object {
        fun fromManifest(raw: String): PluginPermission? {
            val key = raw.trim().lowercase().replace('-', '.')
            return when (key) {
                "storage" -> STORAGE
                "ui.settings", "ui_settings", "settings" -> UI_SETTINGS
                "rules.read", "rules_read" -> RULES_READ
                "rules.write", "rules_write" -> RULES_WRITE
                "vpn.read", "vpn_read" -> VPN_READ
                "vpn.control", "vpn_control" -> VPN_CONTROL
                "notify", "notifications" -> NOTIFY
                "hosts.write", "hosts_write", "hosts" -> HOSTS_WRITE
                "app.read", "app_read", "config.read" -> APP_READ
                else -> null
            }
        }
    }
}

object PluginRuleIds {
    fun prefix(pluginId: String): String = "p:$pluginId:"

    fun owns(pluginId: String, ruleId: String): Boolean = ruleId.startsWith(prefix(pluginId))

    fun isPluginOwned(ruleId: String): Boolean = ruleId.startsWith("p:")
}

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String = "",
    @SerialName("api_level") val apiLevel: Int = 1,
    @SerialName("min_app_version") val minAppVersion: String = "1.0.0",
    val main: String = "main.lua",
    val settings: String? = null,
    val icon: String? = null,
    val homepage: String? = null,
    val permissions: List<String> = emptyList(),
)

data class ValidatedManifest(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val apiLevel: Int,
    val minAppVersion: String,
    val main: String,
    val settings: String?,
    val icon: String?,
    val homepage: String?,
    val permissions: List<PluginPermission>,
)

@Serializable
data class InstalledPluginRecord(
    val id: String,
    val enabled: Boolean = false,
    val granted: List<String> = emptyList(),
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sha256: String = "",
    val sourceName: String = "",
    val lastError: String = "",
)

data class InstalledPlugin(
    val record: InstalledPluginRecord,
    val manifest: ValidatedManifest,
)

sealed class PluginUiItem {
    data class Note(val text: String) : PluginUiItem()
    data class Heading(val text: String, val level: Int) : PluginUiItem()
    data class Divider(val unused: Boolean = true) : PluginUiItem()
    data class Spacer(val unused: Boolean = true) : PluginUiItem()
    data class Badge(val text: String, val tone: String) : PluginUiItem()
    data class Code(val text: String) : PluginUiItem()
    data class Alert(val text: String, val tone: String) : PluginUiItem()
    data class KeyValue(val label: String, val value: String) : PluginUiItem()
    data class Progress(val title: String, val value: Float) : PluginUiItem()
    data class Link(val text: String, val url: String) : PluginUiItem()
    data class Switch(val id: String, val title: String, val body: String, val value: Boolean) : PluginUiItem()
    data class Checkbox(val id: String, val title: String, val body: String, val value: Boolean) : PluginUiItem()
    data class TextField(
        val id: String,
        val title: String,
        val value: String,
        val placeholder: String,
        val multiline: Boolean,
    ) : PluginUiItem()
    data class NumberField(
        val id: String,
        val title: String,
        val value: Float,
        val min: Float,
        val max: Float,
    ) : PluginUiItem()
    data class Select(
        val id: String,
        val title: String,
        val options: List<String>,
        val value: String,
    ) : PluginUiItem()
    data class Slider(
        val id: String,
        val title: String,
        val value: Float,
        val min: Float,
        val max: Float,
    ) : PluginUiItem()
    data class Button(val id: String, val title: String, val destructive: Boolean) : PluginUiItem()
}

data class PluginUiSection(
    val title: String,
    val description: String = "",
    val items: List<PluginUiItem>,
)

data class PluginUiPage(
    val title: String,
    val description: String = "",
    val sections: List<PluginUiSection>,
)
