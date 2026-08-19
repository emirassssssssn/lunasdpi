package com.lunasdev.lunasdpi.plugin

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PluginSecurity {
    const val MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L
    const val MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L
    const val MAX_FILES = 64
    const val MAX_LUA_BYTES = 128 * 1024
    const val MAX_PATH = 180
    const val MAX_INSTALLED = 24
    const val MAX_ENABLED = 8
    private val LUA_BYTECODE = byteArrayOf(0x1b, 'L'.code.toByte(), 'u'.code.toByte(), 'a'.code.toByte())
    private val ALLOWED_EXT = setOf("lua", "json", "svg", "png", "md", "txt")
    private val ID_REGEX = Regex("^[a-z][a-z0-9._-]{1,62}$")
    val VERSION_REGEX = Regex("^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$")
    private val RELATIVE_FILE = Regex("^[a-zA-Z0-9._-]+(/[a-zA-Z0-9._-]+)*$")

    fun validateId(id: String): String? {
        if (!ID_REGEX.matches(id)) {
            return "Plugin id must be a lowercase reverse-domain name (example: community.focus.list)."
        }
        if (id.startsWith("com.lunasdev.") || id == "luna" || id.startsWith("org.luaj")) {
            return "This plugin id is reserved."
        }
        return null
    }

    fun appMeetsMinVersion(appVersion: String, minVersion: String): Boolean {
        val app = parseVersion(appVersion)
        val min = parseVersion(minVersion)
        for (i in 0 until 3) {
            if (app[i] != min[i]) return app[i] > min[i]
        }
        return true
    }

    private fun parseVersion(raw: String): IntArray {
        val parts = raw.trim().split('.')
        return IntArray(3) { index -> parts.getOrNull(index)?.toIntOrNull()?.coerceIn(0, 999) ?: 0 }
    }

    fun validateHomepage(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (!lower.startsWith("https://github.com/")) {
            return "Homepage must be an https://github.com/ link, or omitted."
        }
        if (lower.contains("..") || lower.contains('@') || ' ' in trimmed) {
            return "Homepage URL is not allowed."
        }
        return null
    }

    fun validateRelativePath(path: String): Boolean {
        if (path.length > MAX_PATH) return false
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains("..") || path.contains('\\') || path.contains('\u0000')) return false
        return RELATIVE_FILE.matches(path)
    }

    fun allowedFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in ALLOWED_EXT
    }

    fun isLuaBytecode(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == LUA_BYTECODE[0] &&
            bytes[1] == LUA_BYTECODE[1] &&
            bytes[2] == LUA_BYTECODE[2] &&
            bytes[3] == LUA_BYTECODE[3]
    }

    fun svgIsSafe(raw: String): Boolean {
        val lower = raw.lowercase(Locale.US)
        val banned = listOf(
            "<script",
            "foreignobject",
            "javascript:",
            "onload=",
            "onerror=",
            "onclick=",
            "<iframe",
            "<embed",
            "<object",
            "xlink:href",
            "data:text/html",
            "<use",
        )
        return banned.none { token -> lower.contains(token) }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun zipEntries(bytes: ByteArray): List<Pair<String, ByteArray>> {
        if (bytes.size > MAX_ARCHIVE_BYTES) {
            error("Plugin archive is larger than 2 MB.")
        }
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
            error("Not a ZIP plugin package.")
        }
        val out = ArrayList<Pair<String, ByteArray>>(16)
        var total = 0L
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (out.size >= MAX_FILES) {
                    error("Plugin has too many files.")
                }
                val name = normalizeZipName(entry.name)
                if (!validateRelativePath(name) || !allowedFile(name)) {
                    error("Plugin contains a disallowed path: $name")
                }
                if (entry.size > MAX_UNCOMPRESSED_BYTES) {
                    error("A plugin file is too large.")
                }
                val data = zip.readBytes()
                total += data.size
                if (total > MAX_UNCOMPRESSED_BYTES) {
                    error("Uncompressed plugin size exceeds 2 MB.")
                }
                if (name.endsWith(".lua") && (data.size > MAX_LUA_BYTES || isLuaBytecode(data))) {
                    error("Lua files must be text source, not bytecode, and under 128 KB.")
                }
                out.add(name to data)
                zip.closeEntry()
            }
        }
        if (out.isEmpty()) {
            error("Plugin archive is empty.")
        }
        return out
    }

    fun writeSafe(root: File, relative: String, data: ByteArray) {
        if (!validateRelativePath(relative)) {
            error("Refusing to write $relative")
        }
        val dest = File(root, relative).canonicalFile
        val base = root.canonicalFile
        if (dest != base && !dest.path.startsWith(base.path + File.separator)) {
            error("Path escapes plugin directory.")
        }
        dest.parentFile?.mkdirs()
        dest.writeBytes(data)
    }

    private fun normalizeZipName(raw: String): String {
        var name = raw.replace('\\', '/')
        while (name.startsWith("./")) {
            name = name.removePrefix("./")
        }
        val slash = name.indexOf('/')
        if (slash > 0 && !name.substring(0, slash).contains('.')) {
            val rest = name.substring(slash + 1)
            if (rest.isNotEmpty() && !rest.startsWith("manifest.json") &&
                File(rest).name != name.substringAfterLast('/')
            ) {
                // Keep as-is; folder prefix is allowed (my-plugin/manifest.json).
            }
        }
        return name.trimStart('/')
    }
}
