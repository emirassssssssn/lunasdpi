package com.lunasdev.lunasdpi.plugin

import kotlinx.serialization.json.Json

object PluginManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(raw: String, files: Set<String>): ValidatedManifest {
        val parsed = json.decodeFromString(PluginManifest.serializer(), raw)
        val idError = PluginSecurity.validateId(parsed.id)
        if (idError != null) error(idError)
        val name = parsed.name.trim()
        val author = parsed.author.trim()
        val description = parsed.description.trim()
        if (name.isEmpty() || name.length > 40) error("Plugin name must be 1–40 characters.")
        if (author.isEmpty() || author.length > 40) error("Author must be 1–40 characters.")
        if (description.length > 280) error("Description is too long.")
        if (!PluginSecurity.VERSION_REGEX.matches(parsed.version.trim())) {
            error("Version must look like 1.0.0.")
        }
        if (parsed.apiLevel !in 1..PLUGIN_API_LEVEL) {
            error("Unsupported plugin api_level ${parsed.apiLevel}. This app speaks 1–$PLUGIN_API_LEVEL.")
        }
        val main = parsed.main.trim().ifBlank { "main.lua" }
        if (!PluginSecurity.validateRelativePath(main) || !main.endsWith(".lua") || main !in files) {
            error("main.lua is missing or not a safe path.")
        }
        val settings = parsed.settings?.trim()?.takeIf { it.isNotEmpty() }
        if (settings != null) {
            if (!PluginSecurity.validateRelativePath(settings) || !settings.endsWith(".lua") || settings !in files) {
                error("settings.lua path is invalid.")
            }
        }
        val icon = parsed.icon?.trim()?.takeIf { it.isNotEmpty() }
        if (icon != null) {
            if (!PluginSecurity.validateRelativePath(icon) || icon !in files) {
                error("Icon path is invalid.")
            }
            if (!icon.endsWith(".svg") && !icon.endsWith(".png")) {
                error("Icon must be SVG or PNG.")
            }
        }
        val homeError = PluginSecurity.validateHomepage(parsed.homepage)
        if (homeError != null) error(homeError)
        if (parsed.permissions.size > 12) error("Too many permissions.")
        val permissions = parsed.permissions.map { item ->
            PluginPermission.fromManifest(item) ?: error("Unknown permission: $item")
        }.toMutableList()
        if (settings != null && PluginPermission.UI_SETTINGS !in permissions) {
            permissions.add(PluginPermission.UI_SETTINGS)
        }
        if (PluginPermission.RULES_WRITE in permissions && PluginPermission.RULES_READ !in permissions) {
            permissions.add(PluginPermission.RULES_READ)
        }
        return ValidatedManifest(
            id = parsed.id,
            name = name,
            author = author,
            version = parsed.version.trim(),
            description = description,
            apiLevel = parsed.apiLevel,
            minAppVersion = parsed.minAppVersion.trim().ifBlank { "1.0.0" },
            main = main,
            settings = settings,
            icon = icon,
            homepage = parsed.homepage?.trim()?.takeIf { it.isNotEmpty() },
            permissions = permissions.distinct(),
        )
    }
}
