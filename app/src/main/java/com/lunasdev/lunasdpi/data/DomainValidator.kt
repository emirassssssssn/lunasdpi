package com.lunasdev.lunasdpi.data

object DomainValidator {
    fun normalize(raw: String): String {
        return raw.trim().lowercase().trimEnd('.')
    }

    fun isValidPattern(raw: String): Boolean {
        val n = normalize(raw)
        if (n.isEmpty() || n.length > 253) return false
        if (n.contains("://") || n.contains('/') || n.contains(':') || n.contains('?')) return false
        val rest = if (n.startsWith("*.")) {
            n.substring(2).also { if (it.isEmpty() || it.contains('*')) return false }
        } else {
            if (n.contains('*')) return false
            n
        }
        val labels = rest.split('.')
        if (labels.size < 2) return false
        return labels.all { validLabel(it) }
    }

    fun rejectReason(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Domain is empty"
        if (trimmed.contains("://") || trimmed.startsWith("http:") || trimmed.startsWith("https:")) {
            return "Enter a domain, not a URL"
        }
        if (trimmed.contains('/')) return "Paths are not allowed"
        if (!isValidPattern(trimmed)) return "Invalid domain"
        return null
    }

    private fun validLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 63) return false
        if (label.startsWith('-') || label.endsWith('-')) return false
        return label.all { it.isLetterOrDigit() || it == '-' }
    }
}
