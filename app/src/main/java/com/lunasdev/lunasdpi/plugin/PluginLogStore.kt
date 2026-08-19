package com.lunasdev.lunasdpi.plugin

import java.io.File
import java.util.Locale

class PluginLogStore(private val root: File) {
    fun append(pluginId: String, level: String, message: String) {
        if (PluginSecurity.validateId(pluginId) != null) return
        val line = "${System.currentTimeMillis()} ${level.uppercase(Locale.US)} ${message.replace('\n', ' ').take(500)}\n"
        val file = fileFor(pluginId)
        file.parentFile?.mkdirs()
        file.appendText(line)
        if (file.length() > 24_576) {
            val kept = file.readText().takeLast(16_384)
            file.writeText(kept)
        }
    }

    fun recent(pluginId: String): String {
        if (PluginSecurity.validateId(pluginId) != null) return ""
        val file = fileFor(pluginId)
        if (!file.isFile) return ""
        return file.readText().takeLast(8_192)
    }

    fun clear(pluginId: String) {
        if (PluginSecurity.validateId(pluginId) != null) return
        fileFor(pluginId).delete()
    }

    private fun fileFor(pluginId: String): File = File(root, "$pluginId.log")
}
