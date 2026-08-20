package com.lunasdev.lunasdpi.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLUGIN_API_LEVEL = 2

object PluginLimits {
    const val MAX_RULES = 32
    const val MAX_TIMERS = 8
    const val MIN_TIMER_MS = 1_000L
    const val MAX_TIMER_MS = 120_000L
    const val MAX_STORAGE_KEYS = 96
    const val MAX_ASSET_CHARS = 128 * 1024
    const val MAX_I18N_CHARS = 400
    const val MAX_MODULES = 32
    const val MAX_UI_SECTIONS = 12
    const val MAX_UI_ITEMS = 64
    const val VPN_CONTROL_MS = 15_000L
}

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
    data class Switch(
        val id: String,
        val title: String,
        val body: String,
        val value: Boolean,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class Checkbox(
        val id: String,
        val title: String,
        val body: String,
        val value: Boolean,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class TextField(
        val id: String,
        val title: String,
        val value: String,
        val placeholder: String,
        val multiline: Boolean,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class NumberField(
        val id: String,
        val title: String,
        val value: Float,
        val min: Float,
        val max: Float,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class Select(
        val id: String,
        val title: String,
        val options: List<String>,
        val value: String,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class Slider(
        val id: String,
        val title: String,
        val value: Float,
        val min: Float,
        val max: Float,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class Button(
        val id: String,
        val title: String,
        val destructive: Boolean,
        val enabled: Boolean = true,
    ) : PluginUiItem()
    data class Stat(
        val label: String,
        val value: String,
        val hint: String,
        val tone: String,
    ) : PluginUiItem()
    data class ListItem(
        val title: String,
        val body: String,
        val trailing: String,
        val tone: String,
    ) : PluginUiItem()
    data class Empty(
        val text: String,
        val hint: String,
    ) : PluginUiItem()
    data class Chips(
        val labels: List<String>,
    ) : PluginUiItem()
    data class Quote(
        val text: String,
        val cite: String,
    ) : PluginUiItem()
    data class Fold(
        val title: String,
        val body: String,
        val open: Boolean,
    ) : PluginUiItem()
    data class Steps(
        val labels: List<String>,
        val current: Int,
    ) : PluginUiItem()
    data class Timeline(
        val events: List<String>,
    ) : PluginUiItem()
    data class Score(
        val label: String,
        val value: Float,
        val max: Float,
    ) : PluginUiItem()
    data class Compare(
        val leftLabel: String,
        val left: String,
        val rightLabel: String,
        val right: String,
    ) : PluginUiItem()
    data class Faq(
        val question: String,
        val answer: String,
    ) : PluginUiItem()
    data class Status(
        val text: String,
        val tone: String,
        val detail: String,
    ) : PluginUiItem()
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
