package com.lunasdev.lunasdpi.plugin

import java.io.File
import org.luaj.vm2.LuaError

class PluginPackageFs(private val root: File) {
    fun read(path: String): String? {
        val file = resolve(path) ?: return null
        if (!file.isFile) return null
        if (file.length() > PluginLimits.MAX_ASSET_CHARS) {
            throw LuaError("Package file is too large.")
        }
        if (!readable(path)) {
            throw LuaError("That file type cannot be read from Lua.")
        }
        return file.readText()
    }

    fun exists(path: String): Boolean {
        val file = resolve(path) ?: return false
        return file.isFile && readable(path)
    }

    fun list(dir: String): List<String> {
        val base = if (dir.isBlank()) {
            root.canonicalFile
        } else {
            resolve(dir) ?: return emptyList()
        }
        if (!base.exists()) return emptyList()
        val files = if (base.isFile) {
            listOf(base)
        } else {
            base.walkTopDown().maxDepth(6).filter { it.isFile }.take(PluginSecurity.MAX_FILES).toList()
        }
        val rootPath = root.canonicalFile.toPath()
        return files.mapNotNull { file ->
            val relative = rootPath.relativize(file.toPath()).toString().replace('\\', '/')
            relative.takeIf { readable(it) }
        }.sorted()
    }

    private fun resolve(path: String): File? {
        val trimmed = path.trim().trimStart('/')
        if (trimmed.isEmpty() || !PluginSecurity.validateRelativePath(trimmed)) {
            throw LuaError("Unsafe package path.")
        }
        val file = File(root, trimmed).canonicalFile
        val base = root.canonicalFile
        if (file != base && !file.toPath().startsWith(base.toPath())) {
            throw LuaError("Unsafe package path.")
        }
        return file
    }

    private fun readable(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in READABLE_EXT
    }

    companion object {
        val READABLE_EXT = setOf("lua", "json", "md", "txt", "svg", "csv")
    }
}
