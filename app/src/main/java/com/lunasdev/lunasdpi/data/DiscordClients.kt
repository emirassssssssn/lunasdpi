package com.lunasdev.lunasdpi.data

object DiscordClients {
    val PACKAGES: Set<String> = setOf(
        "com.discord",
        "app.revenge",
        "app.kettu",
        "app.bunny",
        "app.vendetta",
        "dev.beefers.vendetta",
        "com.aliucord",
    )

    private val MANAGERS: Set<String> = setOf(
        "app.revenge.manager",
        "cocobo1.pupu.manager",
    )

    private val HINTS: List<String> = listOf(
        "discord",
        "kettu",
        "revenge",
        "bunny",
        "vendetta",
        "aliucord",
    )

    fun matches(packageName: String): Boolean {
        val pkg = normalize(packageName)
        if (pkg.isEmpty() || isManager(pkg)) {
            return false
        }
        if (pkg in PACKAGES) {
            return true
        }
        return pkg.startsWith("com.discord.")
    }

    fun shouldWatch(packageName: String, selectedPackage: String): Boolean {
        val pkg = normalize(packageName)
        if (pkg.isEmpty() || isManager(pkg)) {
            return false
        }
        val selected = normalize(selectedPackage)
        if (selected.isNotEmpty()) {
            return pkg == selected
        }
        return matches(pkg)
    }

    fun looksLikeClient(packageName: String, label: String): Boolean {
        val pkg = normalize(packageName)
        if (pkg.isEmpty() || isManager(pkg)) {
            return false
        }
        if (matches(pkg)) {
            return true
        }
        val haystack = "$pkg ${label.lowercase()}"
        return HINTS.any { haystack.contains(it) }
    }

    fun isManager(packageName: String): Boolean {
        val pkg = normalize(packageName)
        return pkg in MANAGERS || pkg.endsWith(".manager")
    }

    fun isTransientUi(packageName: String): Boolean {
        val pkg = normalize(packageName)
        if (pkg.isEmpty()) {
            return true
        }
        if (pkg == "com.lunasdev.lunasdpi") {
            return true
        }
        return TRANSIENT_HINTS.any { pkg.contains(it) }
    }

    fun classifyForeground(packages: Collection<String>, selectedPackage: String): ForegroundKind {
        if (packages.any { shouldWatch(it, selectedPackage) }) {
            return ForegroundKind.Discord
        }
        val remaining = packages.filter { !isTransientUi(it) }
        return if (remaining.isEmpty()) ForegroundKind.Transient else ForegroundKind.Other
    }

    private fun normalize(packageName: String): String = packageName.trim().lowercase()

    private val TRANSIENT_HINTS: List<String> = listOf(
        "systemui",
        "permissioncontroller",
        "packageinstaller",
        "inputmethod",
        ".ime",
        "honeyboard",
        "swiftkey",
        "touchtype",
        "keyboard",
        "intentresolver",
        "screenshot",
        "quickstep",
        "edgepanel",
        "freeform",
        "navigationbar",
    )
}

enum class ForegroundKind {
    Discord,
    Transient,
    Other,
}
