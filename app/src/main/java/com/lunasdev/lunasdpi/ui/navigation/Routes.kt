package com.lunasdev.lunasdpi.ui.navigation

object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Settings = "settings"
    const val Dpi = "settings/dpi"
    const val Dns = "settings/dns"
    const val Vpn = "settings/vpn"
    const val Advanced = "settings/advanced"
    const val Rules = "rules"
    const val EditRule = "rules/edit?id={id}"
    const val QuickAdd = "rules/quick"
    const val Diagnostics = "diagnostics"
    const val Privacy = "privacy"
    const val Apps = "apps"
    const val DiscordAutoStart = "settings/discord"
    const val Plugins = "plugins"
    const val PluginImport = "plugins/import"
    const val PluginDetail = "plugins/detail?id={id}"
    const val PluginSettings = "plugins/settings?id={id}"

    fun editRule(id: String?): String = if (id.isNullOrBlank()) "rules/edit?id=new" else "rules/edit?id=$id"

    fun pluginDetail(id: String): String = "plugins/detail?id=$id"

    fun pluginSettings(id: String): String = "plugins/settings?id=$id"
}
