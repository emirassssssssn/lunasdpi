package com.lunasdev.lunasdpi.plugin

import java.io.File

data class UnpackedPlugin(
    val manifest: ValidatedManifest,
    val files: Map<String, ByteArray>,
    val sha256: String,
    val sourceName: String = "",
)

object PluginPackageImporter {
    fun unpack(zipBytes: ByteArray): UnpackedPlugin {
        val entries = stripCommonRoot(PluginSecurity.zipEntries(zipBytes))
        val files = LinkedHashMap<String, ByteArray>(entries.size)
        for ((name, data) in entries) {
            if (files.put(name, data) != null) {
                error("Duplicate path in plugin: $name")
            }
        }
        val manifestBytes = files["manifest.json"] ?: error("manifest.json is required at the plugin root.")
        val manifest = PluginManifestParser.parse(String(manifestBytes, Charsets.UTF_8), files.keys)
        val iconPath = manifest.icon
        if (iconPath != null && iconPath.endsWith(".svg")) {
            val svg = String(files.getValue(iconPath), Charsets.UTF_8)
            if (!PluginSecurity.svgIsSafe(svg)) {
                error("Icon SVG contains disallowed markup.")
            }
        }
        return UnpackedPlugin(
            manifest = manifest,
            files = files,
            sha256 = PluginSecurity.sha256(zipBytes),
        )
    }

    fun installTo(root: File, unpacked: UnpackedPlugin) {
        if (root.exists()) {
            root.deleteRecursively()
        }
        root.mkdirs()
        unpacked.files.forEach { (relative, data) ->
            PluginSecurity.writeSafe(root, relative, data)
        }
    }

    internal fun stripCommonRoot(entries: List<Pair<String, ByteArray>>): List<Pair<String, ByteArray>> {
        if (entries.any { it.first == "manifest.json" }) {
            return entries
        }
        val prefix = entries.map { it.first.substringBefore('/', "") }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return entries
        val stripped = entries.mapNotNull { (name, data) ->
            when {
                name == prefix -> null
                name.startsWith("$prefix/") -> name.removePrefix("$prefix/") to data
                else -> name to data
            }
        }
        return if (stripped.any { it.first == "manifest.json" }) stripped else entries
    }
}
