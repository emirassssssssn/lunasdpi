package com.lunasdev.lunasdpi.plugin

import android.content.Context
import java.io.File
import org.json.JSONObject

class PluginStorage(context: Context, private val pluginId: String) {
    private val file: File = File(File(context.applicationContext.filesDir, "plugin-data"), "$pluginId.json")

    companion object {
        const val MAX_VALUE_CHARS = 32_768
        const val MAX_KEYS = PluginLimits.MAX_STORAGE_KEYS
    }

    @Synchronized
    fun get(key: String): String? {
        val safe = sanitizeKey(key) ?: return null
        val json = read()
        if (!json.has(safe) || json.isNull(safe)) return null
        return json.optString(safe)
    }

    @Synchronized
    fun set(key: String, value: String) {
        val safe = sanitizeKey(key) ?: return
        if (value.length > MAX_VALUE_CHARS) return
        val json = read()
        if (json.length() >= MAX_KEYS && !json.has(safe)) return
        json.put(safe, value)
        persist(json)
    }

    @Synchronized
    fun remove(key: String) {
        val safe = sanitizeKey(key) ?: return
        val json = read()
        json.remove(safe)
        persist(json)
    }

    @Synchronized
    fun has(key: String): Boolean {
        val safe = sanitizeKey(key) ?: return false
        val json = read()
        return json.has(safe) && !json.isNull(safe)
    }

    @Synchronized
    fun keys(): List<String> {
        val json = read()
        val out = ArrayList<String>(json.length())
        val iter = json.keys()
        while (iter.hasNext()) {
            out.add(iter.next())
        }
        return out.sorted()
    }

    @Synchronized
    fun size(): Int = read().length()

    @Synchronized
    fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun read(): JSONObject {
        if (!file.isFile) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun persist(json: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    private fun sanitizeKey(key: String): String? {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) return null
        if (!trimmed.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }) return null
        return trimmed
    }
}
