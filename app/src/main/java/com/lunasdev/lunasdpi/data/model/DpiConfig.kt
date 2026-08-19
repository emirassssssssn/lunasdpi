package com.lunasdev.lunasdpi.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DpiConfig(
    val enabled: Boolean = true,
    val mode: DpiMode = DpiMode.AUTOMATIC,
    val tcpFragmentation: Boolean = true,
    val fragmentSize: Int = 2,
    val httpHostCase: Boolean = true,
    val httpSpacing: Boolean = false,
    val httpMethodSpacing: Boolean = false,
    val persistentFragment: Boolean = false,
    val dnsMode: DnsMode = DnsMode.AUTOMATIC,
    val customDns: List<String> = emptyList(),
    val ipv6Mode: Ipv6Mode = Ipv6Mode.BLOCK,
    val blockQuic: Boolean = true,
    val settingsRevision: Int = 0,
    val mtu: Int = 1500,
    val startOnBoot: Boolean = false,
    val autoStartOnDiscord: Boolean = true,
    val autoStartPackage: String = "",
    val autoStopOnDiscordLeave: Boolean = true,
    val autoReconnect: Boolean = true,
    val notificationSilent: Boolean = false,
    val logLevel: Int = 2,
    val perAppMode: PerAppMode = PerAppMode.ALL,
    val perAppPackages: List<String> = emptyList(),
) {
    fun validated(): DpiConfig {
        val size = fragmentSize.coerceIn(MIN_FRAGMENT, MAX_FRAGMENT)
        val safeMtu = mtu.coerceIn(MIN_MTU, MAX_MTU)
        val dns = customDns.map { it.trim() }.filter { it.isNotEmpty() }
        val level = logLevel.coerceIn(0, 3)
        return copy(fragmentSize = size, mtu = safeMtu, customDns = dns, logLevel = level)
    }

    fun migrated(): DpiConfig {
        if (settingsRevision >= CURRENT_REVISION) {
            return validated()
        }
        return copy(
            ipv6Mode = Ipv6Mode.BLOCK,
            blockQuic = true,
            settingsRevision = CURRENT_REVISION,
        ).validated()
    }

    companion object {
        const val MIN_FRAGMENT = 1
        const val MAX_FRAGMENT = 256
        const val MIN_MTU = 576
        const val MAX_MTU = 1500
        const val CURRENT_REVISION = 3
        val AUTOMATIC_RESOLVERS: List<String> = listOf("8.8.8.8", "9.9.9.9", "1.1.1.1", "77.88.8.8")
    }
}
