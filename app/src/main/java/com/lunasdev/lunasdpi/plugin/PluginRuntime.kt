package com.lunasdev.lunasdpi.plugin

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lunasdev.lunasdpi.BuildConfig
import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.RulesRepository
import com.lunasdev.lunasdpi.data.SettingsRepository
import com.lunasdev.lunasdpi.data.VpnStateRepository
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.plugin.lua.LunaLuaApi
import com.lunasdev.lunasdpi.plugin.lua.PluginEventBus
import com.lunasdev.lunasdpi.plugin.lua.PluginLuaException
import com.lunasdev.lunasdpi.plugin.lua.PluginNativeBridge
import com.lunasdev.lunasdpi.plugin.lua.PluginUiParser
import com.lunasdev.lunasdpi.plugin.lua.SandboxedLua
import com.lunasdev.lunasdpi.vpn.VpnController
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

class PluginRuntime(
    context: Context,
    private val scope: CoroutineScope,
    private val registry: PluginRegistry,
    private val rules: RulesRepository,
    private val vpn: VpnController,
    private val vpnState: VpnStateRepository,
    private val logs: PluginLogStore,
    private val notifier: PluginNotifier,
    private val hosts: HostsStore,
    private val settings: SettingsRepository,
) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private val vms = LinkedHashMap<String, PluginVm>()
    private val vpnControlAt = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun reconcile(records: List<InstalledPluginRecord>) {
        mutex.withLock {
            val enabled = records.filter { record ->
                record.enabled && record.granted.isNotEmpty()
            }
            val keep = enabled.map { it.id }.toSet()
            vms.keys.toList().forEach { id ->
                val next = enabled.find { it.id == id }
                val current = vms[id] ?: return@forEach
                val grantsChanged = next == null || next.granted != current.record.granted
                if (id !in keep || grantsChanged) {
                    unloadLocked(id, callDisable = true)
                }
            }
            enabled.forEach { record ->
                if (record.id !in vms) {
                    loadLocked(record)
                }
            }
        }
    }

    suspend fun settingsPage(pluginId: String): PluginUiPage {
        val vm = mutex.withLock { vms[pluginId] } ?: error("Plugin is not running.")
        if (!vm.granted(PluginPermission.UI_SETTINGS) || vm.manifest.settings == null) {
            error("This plugin has no settings page.")
        }
        val result = vm.call("settings_page") ?: error("settings_page() is missing.")
        return PluginUiParser.parse(result)
    }

    suspend fun settingChanged(pluginId: String, id: String, value: Any) {
        val vm = mutex.withLock { vms[pluginId] } ?: error("Plugin is not running.")
        vm.call("on_setting_changed", LuaValue.valueOf(id), toLua(value))
    }

    suspend fun onVpnPhase(phase: VpnPhase) {
        val name = phase.name.lowercase(Locale.US)
        val snapshot = mutex.withLock { vms.values.toList() }
        snapshot.forEach { vm ->
            runCatching { vm.call("on_vpn_phase", LuaValue.valueOf(name)) }
            runCatching { vm.exec { vm.bridge.events().emit("vpnPhase", LuaValue.valueOf(name)) } }
        }
    }

    fun recentLog(pluginId: String): String = logs.recent(pluginId)

    suspend fun dropOwnedRules(pluginId: String) {
        val prefix = PluginRuleIds.prefix(pluginId)
        rules.update { current -> current.filterNot { it.id.startsWith(prefix) } }
    }

    private fun loadLocked(record: InstalledPluginRecord) {
        val manifest = registry.loadManifest(record.id) ?: run {
            scope.launch { fail(record, "Plugin files are missing.") }
            return
        }
        if (!PluginSecurity.appMeetsMinVersion(BuildConfig.VERSION_NAME, manifest.minAppVersion)) {
            scope.launch { fail(record, "This plugin needs Lunas DPI ${manifest.minAppVersion}.") }
            return
        }
        if (vms.size >= PluginSecurity.MAX_ENABLED) {
            scope.launch { fail(record, "Too many plugins are enabled.") }
            return
        }
        val root = registry.pluginDir(record.id)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "luna-plugin-${record.id}").apply { isDaemon = true }
        }
        try {
            val bridge = HostBridge(record, manifest, root)
            val luna = LunaLuaApi.table(record.id, bridge)
            val globals = SandboxedLua.create(root, luna)
            val vm = PluginVm(record, manifest, root, globals, executor, bridge)
            bridge.attach(vm)
            vm.exec {
                SandboxedLua.loadFile(globals, root, manifest.main)
                val settings = manifest.settings
                if (settings != null) {
                    SandboxedLua.loadFile(globals, root, settings)
                }
            }
            vms[record.id] = vm
            runCatching { vm.call("on_enable") }.onFailure { error ->
                unloadLocked(record.id, callDisable = false)
                throw error
            }
            runCatching { vm.exec { bridge.events().emit("ready") } }
            if (record.lastError.isNotBlank()) {
                scope.launch { registry.upsert(record.copy(lastError = "")) }
            }
        } catch (error: Throwable) {
            executor.shutdownNow()
            scope.launch { fail(record, humanMessage(error)) }
        }
    }

    private fun unloadLocked(pluginId: String, callDisable: Boolean) {
        val vm = vms.remove(pluginId) ?: return
        if (callDisable) {
            runCatching { vm.call("on_disable") }
        }
        vm.bridge.shutdown()
        vm.executor.shutdownNow()
        scope.launch { dropOwnedRules(pluginId) }
    }

    private suspend fun fail(record: InstalledPluginRecord, message: String) {
        logs.append(record.id, "error", message)
        registry.upsert(record.copy(enabled = false, lastError = message.take(240)))
    }

    private fun toLua(value: Any): LuaValue = when (value) {
        is Boolean -> LuaValue.valueOf(value)
        is Number -> LuaValue.valueOf(value.toDouble())
        else -> LuaValue.valueOf(value.toString())
    }

    private fun humanMessage(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }.firstOrNull { it is LuaError || it is PluginLuaException }
            ?: error
        return (cause.message ?: cause.javaClass.simpleName).take(240)
    }

    private inner class PluginVm(
        val record: InstalledPluginRecord,
        val manifest: ValidatedManifest,
        val root: File,
        val globals: org.luaj.vm2.Globals,
        val executor: java.util.concurrent.ExecutorService,
        val bridge: HostBridge,
    ) {
        fun granted(permission: PluginPermission): Boolean = bridge.granted(permission)

        fun call(name: String, vararg args: LuaValue): LuaValue? = exec {
            val fn = globals.get(name)
            if (!fn.isfunction()) {
                null
            } else {
                invokeFn(fn, args)
            }
        }

        fun invoke(fn: LuaValue, vararg args: LuaValue): LuaValue = exec { invokeFn(fn, args) }

        fun fire(fn: LuaValue) {
            executor.execute {
                runCatching { invokeFn(fn, emptyArray()) }
            }
        }

        private fun invokeFn(fn: LuaValue, args: Array<out LuaValue>): LuaValue {
            return when (args.size) {
                0 -> fn.call()
                1 -> fn.call(args[0])
                2 -> fn.call(args[0], args[1])
                else -> fn.invoke(LuaValue.varargsOf(args)).arg1()
            }
        }

        fun <T> exec(block: () -> T): T {
            val future = executor.submit(Callable {
                try {
                    block()
                } catch (error: LuaError) {
                    throw PluginLuaException(error.message ?: "Lua error")
                }
            })
            return try {
                future.get(1_500L, TimeUnit.MILLISECONDS)
            } catch (timeout: TimeoutException) {
                future.cancel(true)
                throw PluginLuaException("Plugin exceeded the 1.5s time budget.")
            } catch (error: java.util.concurrent.ExecutionException) {
                throw error.cause ?: error
            }
        }
    }

    private inner class HostBridge(
        private val record: InstalledPluginRecord,
        private val manifest: ValidatedManifest,
        root: File,
    ) : PluginNativeBridge {
        private val storage = PluginStorage(app, record.id)
        private val i18n = PluginI18n(root, Locale.getDefault().language)
        private val bus = PluginEventBus()
        private val timers = ConcurrentHashMap<Int, Runnable>()
        private val timerSeq = AtomicInteger()
        private var owner: PluginVm? = null

        fun attach(vm: PluginVm) {
            owner = vm
        }

        fun shutdown() {
            timers.values.forEach { runnable -> mainHandler.removeCallbacks(runnable) }
            timers.clear()
            bus.clear()
            hosts.clearPlugin(record.id)
            owner = null
        }

        override fun pluginId(): String = record.id

        override fun pluginName(): String = manifest.name

        override fun pluginAuthor(): String = manifest.author

        override fun pluginVersion(): String = manifest.version

        override fun events(): PluginEventBus = bus

        override fun appConfig(): Map<String, Any> {
            val cfg = runBlocking { settings.current() }
            return mapOf(
                "mode" to cfg.mode.name.lowercase(Locale.US),
                "dns_mode" to cfg.dnsMode.name.lowercase(Locale.US),
                "mtu" to cfg.mtu,
                "ipv6_mode" to cfg.ipv6Mode.name.lowercase(Locale.US),
                "block_quic" to cfg.blockQuic,
                "log_level" to cfg.logLevel,
                "per_app_mode" to cfg.perAppMode.name.lowercase(Locale.US),
                "fragment_size" to cfg.fragmentSize,
                "tcp_fragmentation" to cfg.tcpFragmentation,
                "http_host_case" to cfg.httpHostCase,
                "start_on_boot" to cfg.startOnBoot,
                "auto_reconnect" to cfg.autoReconnect,
            )
        }

        override fun schedule(ms: Long, fn: LuaValue, repeat: Boolean): Int {
            val delay = ms.coerceIn(2_000L, 120_000L)
            if (timers.size >= 4) {
                throw PluginLuaException("A plugin may have at most 4 timers.")
            }
            val id = timerSeq.incrementAndGet()
            val runnable = object : Runnable {
                override fun run() {
                    val vm = owner ?: return
                    vm.fire(fn)
                    if (repeat && timers.containsKey(id)) {
                        mainHandler.postDelayed(this, delay)
                    } else {
                        timers.remove(id)
                    }
                }
            }
            timers[id] = runnable
            mainHandler.postDelayed(runnable, delay)
            return id
        }

        override fun cancelTimer(id: Int) {
            val runnable = timers.remove(id) ?: return
            mainHandler.removeCallbacks(runnable)
        }

        override fun log(level: String, message: String) {
            logs.append(record.id, level, message)
        }

        override fun storage(): PluginStorage = storage

        override fun granted(permission: PluginPermission): Boolean {
            val keys = record.granted.mapNotNull { PluginPermission.fromManifest(it) }.toSet()
            if (permission == PluginPermission.RULES_READ && PluginPermission.RULES_WRITE in keys) {
                return true
            }
            return permission in keys
        }

        override fun locale(): String = Locale.getDefault().language.ifBlank { "en" }

        override fun appVersion(): String = BuildConfig.VERSION_NAME

        override fun translate(key: String, fallback: String): String = i18n.t(key, fallback)

        override fun vpnPhase(): String = vpnState.phase.value.name.lowercase(Locale.US)

        override fun vpnSnapshot(): Map<String, Any> {
            val snap = vpnState.snapshot.value
            return mapOf(
                "phase" to vpnPhase(),
                "packets_processed" to snap.packetsProcessed,
                "packets_modified" to snap.packetsModified,
                "packets_dropped" to snap.packetsDropped,
                "bytes_in" to snap.bytesIn,
                "bytes_out" to snap.bytesOut,
                "dns_queries" to snap.dnsQueries,
                "active_tcp" to snap.activeTcp,
                "active_udp" to snap.activeUdp,
                "engine_alive" to snap.engineAlive,
                "tun_active" to snap.tunActive,
                "uptime_seconds" to snap.uptimeSeconds,
                "strategy" to snap.currentStrategy,
            )
        }

        override fun requestVpnStart() {
            throttleVpn()
            mainHandler.post {
                scope.launch(Dispatchers.Main) { vpn.start() }
            }
        }

        override fun requestVpnStop() {
            throttleVpn()
            mainHandler.post { vpn.stop() }
        }

        override fun listPluginRules(): List<DomainRule> = runBlocking {
            rules.current().filter { PluginRuleIds.owns(record.id, it.id) }
        }

        override fun upsertPluginRule(rule: DomainRule) {
            if (!PluginRuleIds.owns(record.id, rule.id)) {
                throw PluginLuaException("Plugins may only write their own rules.")
            }
            runBlocking { rules.upsert(rule) }
        }

        override fun deletePluginRule(id: String) {
            if (!PluginRuleIds.owns(record.id, id)) {
                throw PluginLuaException("Plugins may only delete their own rules.")
            }
            runBlocking { rules.delete(id) }
        }

        override fun notify(title: String, text: String) {
            notifier.show(record.id, manifest.name, title, text)
        }

        override fun setHosts(entries: List<HostEntry>) {
            hosts.replacePlugin(record.id, entries)
        }

        override fun listHosts(): List<HostEntry> = hosts.pluginEntries(record.id)

        override fun clearHosts() {
            hosts.clearPlugin(record.id)
        }

        private fun throttleVpn() {
            val now = System.currentTimeMillis()
            val previous = vpnControlAt.put(record.id, now) ?: 0L
            if (now - previous < 15_000L) {
                throw PluginLuaException("VPN control is rate-limited.")
            }
        }
    }
}
