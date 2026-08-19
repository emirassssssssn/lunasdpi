package com.lunasdev.lunasdpi.vpn

import androidx.annotation.Keep
import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.model.DnsMode
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.DpiConfig

class NativeEngine(
    private val protect: (Int) -> Boolean,
) {
    @Keep
    fun protectSocket(fd: Int): Boolean = protect(fd)

    @Keep
    fun resolveDns(query: ByteArray): ByteArray? = DnsOverHttps.resolve(query)

    fun start(
        tunFd: Int,
        config: DpiConfig,
        rules: List<DomainRule>,
        systemDns: List<String>,
        hosts: List<HostEntry> = emptyList(),
    ): Boolean {
        val activeRules = enabledRules(rules)
        val bridge = NativeBridgeConfig(
            mode = config.mode.nativeOrdinal(),
            tcpFragmentation = config.tcpFragmentation,
            fragmentSize = config.fragmentSize,
            httpHostCase = config.httpHostCase,
            httpSpacing = config.httpSpacing,
            httpMethodSpacing = config.httpMethodSpacing,
            persistentFragment = config.persistentFragment,
            blockQuic = config.blockQuic,
            dnsMode = config.dnsMode.nativeOrdinal(),
            customDns = when (config.dnsMode) {
                DnsMode.CUSTOM -> config.customDns.toTypedArray()
                DnsMode.AUTOMATIC -> {
                    if (config.customDns.isNotEmpty()) {
                        config.customDns.toTypedArray()
                    } else {
                        DpiConfig.AUTOMATIC_RESOLVERS.toTypedArray()
                    }
                }
                DnsMode.SYSTEM -> config.customDns.toTypedArray()
            },
            systemDns = systemDns.toTypedArray(),
            ipv6Mode = config.ipv6Mode.nativeOrdinal(),
            logLevel = config.logLevel,
            mtu = config.mtu,
            tunDnsIpv4 = TUN_DNS,
            ruleNames = activeRules.map { it.name }.toTypedArray(),
            ruleEnabled = BooleanArray(activeRules.size) { true },
            ruleStrategies = IntArray(activeRules.size) { activeRules[it].strategy.nativeOrdinal() },
            ruleDomains = activeRules.map { it.domains.joinToString("\n") }.toTypedArray(),
            ruleFrag = BooleanArray(activeRules.size) { activeRules[it].tcpFragmentation },
            ruleFragSize = IntArray(activeRules.size) { activeRules[it].fragmentSize },
            ruleHostCase = BooleanArray(activeRules.size) { activeRules[it].httpHostCase },
            ruleSpacing = BooleanArray(activeRules.size) { activeRules[it].httpSpacing },
            ruleMethodSpacing = BooleanArray(activeRules.size) { activeRules[it].httpMethodSpacing },
            hostNames = hosts.map { it.host }.toTypedArray(),
            hostIps = hosts.map { it.ipv4 }.toTypedArray(),
        )
        return nativeStart(tunFd, bridge)
    }

    fun updateHosts(hosts: List<HostEntry>) {
        nativeUpdateHosts(
            hosts.map { it.host }.toTypedArray(),
            hosts.map { it.ipv4 }.toTypedArray(),
        )
    }

    fun updateRules(rules: List<DomainRule>) {
        val activeRules = enabledRules(rules)
        nativeUpdateRules(
            NativeBridgeConfig(
                mode = 0,
                tcpFragmentation = false,
                fragmentSize = 2,
                httpHostCase = false,
                httpSpacing = false,
                httpMethodSpacing = false,
                persistentFragment = false,
                blockQuic = false,
                dnsMode = 0,
                customDns = emptyArray(),
                systemDns = emptyArray(),
                ipv6Mode = 0,
                logLevel = 0,
                mtu = 1500,
                tunDnsIpv4 = TUN_DNS,
                ruleNames = activeRules.map { it.name }.toTypedArray(),
                ruleEnabled = BooleanArray(activeRules.size) { true },
                ruleStrategies = IntArray(activeRules.size) { activeRules[it].strategy.nativeOrdinal() },
                ruleDomains = activeRules.map { it.domains.joinToString("\n") }.toTypedArray(),
                ruleFrag = BooleanArray(activeRules.size) { activeRules[it].tcpFragmentation },
                ruleFragSize = IntArray(activeRules.size) { activeRules[it].fragmentSize },
                ruleHostCase = BooleanArray(activeRules.size) { activeRules[it].httpHostCase },
                ruleSpacing = BooleanArray(activeRules.size) { activeRules[it].httpSpacing },
                ruleMethodSpacing = BooleanArray(activeRules.size) { activeRules[it].httpMethodSpacing },
            ),
        )
    }

    fun stop() = nativeStop()

    fun onNetworkChanged() = nativeNetworkChanged()

    fun stats(): EngineStats = nativeGetStats()

    private fun enabledRules(rules: List<DomainRule>): List<DomainRule> =
        rules.filter { rule -> rule.enabled && rule.domains.isNotEmpty() }

    private external fun nativeStart(tunFd: Int, config: NativeBridgeConfig): Boolean
    private external fun nativeUpdateRules(config: NativeBridgeConfig)
    private external fun nativeUpdateHosts(hostNames: Array<String>, hostIps: Array<String>)
    private external fun nativeStop()
    private external fun nativeNetworkChanged()
    private external fun nativeGetStats(): EngineStats

    companion object {
        const val TUN_ADDRESS = "10.7.0.2"
        const val TUN_IPV6 = "fd00:7:7:7::2"
        const val TUN_DNS = "10.7.0.1"

        init {
            System.loadLibrary("luna_engine")
        }

        fun selfTest(): String = nativeSelfTest()

        @JvmStatic
        private external fun nativeSelfTest(): String
    }
}
