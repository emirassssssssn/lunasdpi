package com.lunasdev.lunasdpi.plugin

import java.io.File
import org.json.JSONObject

class PluginI18n(root: File, locale: String) {
    private val values: Map<String, String> = load(root, locale)

    fun t(key: String, fallback: String): String {
        val safe = key.trim().take(80)
        if (safe.isEmpty()) return fallback.take(200)
        return values[safe] ?: fallback.take(200)
    }

    companion object {
        private fun load(root: File, locale: String): Map<String, String> {
            val lang = locale.lowercase().take(8).ifBlank { "en" }
            val merged = LinkedHashMap<String, String>()
            merged.putAll(read(File(root, "locale/en.json")))
            if (lang != "en") {
                merged.putAll(read(File(root, "locale/$lang.json")))
            }
            return merged
        }

        private fun read(file: File): Map<String, String> {
            if (!file.isFile || file.length() > 32_768) return emptyMap()
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return emptyMap()
            val out = LinkedHashMap<String, String>()
            val keys = json.keys()
            var count = 0
            while (keys.hasNext() && count < 200) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (key.length <= 80 && value.isNotEmpty()) {
                    out[key] = value.take(200)
                    count += 1
                }
            }
            return out
        }
    }
}
