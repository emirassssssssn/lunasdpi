package com.lunasdev.lunasdpi.data

data class HostsParseResult(
    val entries: List<HostEntry>,
    val errors: List<String>,
)

object HostsFile {
    const val MAX_TEXT_CHARS = 32_768
    const val MAX_PER_PLUGIN = 256
    const val MAX_MERGED = 1_024

    fun parse(raw: String): HostsParseResult {
        val entries = LinkedHashMap<String, HostEntry>()
        val errors = ArrayList<String>()
        raw.lineSequence().forEachIndexed { index, line ->
            val lineNo = index + 1
            val stripped = line.substringBefore('#').trim()
            if (stripped.isEmpty()) {
                return@forEachIndexed
            }
            val parts = stripped.split(WHITESPACE)
            if (parts.size < 2) {
                errors.add("Line $lineNo: expected an IPv4 address then a hostname")
                return@forEachIndexed
            }
            val ipText = parts[0]
            if (ipText.contains(':')) {
                return@forEachIndexed
            }
            val packed = parseIpv4(ipText)
            if (packed == null) {
                errors.add("Line $lineNo: invalid IPv4 address")
                return@forEachIndexed
            }
            if (!isAllowedIpv4(packed)) {
                errors.add("Line $lineNo: that IP cannot be used as a hosts target")
                return@forEachIndexed
            }
            val dotted = formatIpv4(packed)
            for (i in 1 until parts.size) {
                val host = DomainValidator.normalize(parts[i])
                if (!DomainValidator.isValidPattern(host)) {
                    errors.add("Line $lineNo: invalid hostname")
                    continue
                }
                if (host !in entries && entries.size >= MAX_PER_PLUGIN) {
                    errors.add("Line $lineNo: a plugin may map at most $MAX_PER_PLUGIN hosts")
                    break
                }
                entries[host] = HostEntry(host = host, ipv4 = dotted)
            }
        }
        return HostsParseResult(entries = entries.values.toList(), errors = errors)
    }

    fun parseIpv4(text: String): Int? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val token = parts[i]
            if (token.isEmpty() || token.length > 3 || token.any { !it.isDigit() }) return null
            val value = token.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[i] = value
        }
        return (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
    }

    fun isAllowedIpv4(packed: Int): Boolean {
        val a = packed ushr 24 and 0xFF
        val b = packed ushr 16 and 0xFF
        val c = packed ushr 8 and 0xFF
        return when {
            a == 0 -> false
            a == 10 && b == 7 && c == 0 -> false
            a == 169 && b == 254 -> false
            a in 224..255 -> false
            else -> true
        }
    }

    fun formatIpv4(packed: Int): String {
        val a = packed ushr 24 and 0xFF
        val b = packed ushr 16 and 0xFF
        val c = packed ushr 8 and 0xFF
        val d = packed and 0xFF
        return "$a.$b.$c.$d"
    }

    private val WHITESPACE = Regex("\\s+")
}
