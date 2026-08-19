package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.DpiMode
import com.lunasdev.lunasdpi.plugin.PLUGIN_API_LEVEL
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.PluginRuleIds
import com.lunasdev.lunasdpi.plugin.PluginStorage
import java.util.UUID
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class PluginLuaException(message: String) : Exception(message)

interface PluginNativeBridge {
    fun log(level: String, message: String)
    fun storage(): PluginStorage
    fun granted(permission: PluginPermission): Boolean
    fun locale(): String
    fun appVersion(): String
    fun translate(key: String, fallback: String): String
    fun vpnPhase(): String
    fun vpnSnapshot(): Map<String, Any>
    fun requestVpnStart()
    fun requestVpnStop()
    fun listPluginRules(): List<DomainRule>
    fun upsertPluginRule(rule: DomainRule)
    fun deletePluginRule(id: String)
    fun notify(title: String, text: String)
    fun setHosts(entries: List<HostEntry>)
    fun listHosts(): List<HostEntry>
    fun clearHosts()
    fun pluginId(): String
    fun pluginName(): String
    fun pluginAuthor(): String
    fun pluginVersion(): String
    fun events(): PluginEventBus
    fun schedule(ms: Long, fn: LuaValue, repeat: Boolean): Int
    fun cancelTimer(id: Int)
    fun appConfig(): Map<String, Any>
}

internal object LunaLuaApi {
    fun table(pluginId: String, bridge: PluginNativeBridge): LuaTable {
        val luna = LuaTable()
        luna.set("API_LEVEL", PLUGIN_API_LEVEL)
        luna.set("version", PLUGIN_API_LEVEL)
        LunaStdLib.install(luna)
        LunaManagers.install(luna, pluginId, bridge)
        luna.set("ui", LunaUiApi.table())
        luna.set("Collection", LunaCollection.type())
        LunaBuilders.install(luna, bridge)
        LunaClient.install(luna, pluginId, bridge)
        luna.set("Intents", luna.get("permissions"))
        luna.set("User", luna.get("user"))
        return luna
    }

    internal fun luaToRule(pluginId: String, table: LuaTable): DomainRule {
        val localId = table.get("id").optjstring(UUID.randomUUID().toString()).trim()
        val id = if (PluginRuleIds.owns(pluginId, localId)) {
            localId.take(80)
        } else {
            PluginRuleIds.prefix(pluginId) + localId.filter { it.isLetterOrDigit() || it == '-' }.take(36)
        }
        val name = table.get("name").optjstring("Plugin rule").trim().take(40)
        if (name.equals("Discord", ignoreCase = true)) {
            throw LuaError("That rule name is reserved.")
        }
        val enabled = table.get("enabled").optboolean(true)
        val domainsRaw = table.get("domains")
        val domains = ArrayList<String>()
        if (domainsRaw.istable()) {
            var i = 1
            while (true) {
                val item = domainsRaw.get(i)
                if (item.isnil()) break
                val pattern = DomainValidator.normalize(item.tojstring())
                if (!DomainValidator.isValidPattern(pattern)) {
                    throw LuaError("Invalid domain: ${item.tojstring()}")
                }
                domains.add(pattern)
                i += 1
                if (i > 32) throw LuaError("Too many domains on one rule.")
            }
        }
        val strategy = DpiMode.fromStorage(table.get("strategy").optjstring("automatic"))
        return DomainRule(
            id = id,
            name = name.ifBlank { "Plugin rule" },
            enabled = enabled,
            domains = domains.distinct(),
            strategy = strategy,
        )
    }

    internal fun ruleToLua(rule: DomainRule): LuaTable {
        val table = LuaTable()
        table.set("id", rule.id)
        table.set("name", rule.name)
        table.set("enabled", LuaValue.valueOf(rule.enabled))
        table.set("strategy", rule.strategy.name.lowercase())
        val domains = LuaTable()
        rule.domains.forEachIndexed { index, domain -> domains.set(index + 1, domain) }
        table.set("domains", domains)
        return table
    }

    internal fun luaToHosts(table: LuaTable): List<HostEntry> {
        val entries = LinkedHashMap<String, HostEntry>()
        var i = 1
        while (i <= HostsFile.MAX_PER_PLUGIN) {
            val item = table.get(i)
            if (item.isnil()) break
            val row = item.checktable()
            val hostRaw = firstString(row, "host", "hostname")
            val ipRaw = firstString(row, "ip", "ipv4")
            val host = DomainValidator.normalize(hostRaw)
            if (!DomainValidator.isValidPattern(host)) {
                throw LuaError("Invalid hostname: $hostRaw")
            }
            val packed = HostsFile.parseIpv4(ipRaw) ?: throw LuaError("Invalid IPv4 address: $ipRaw")
            if (!HostsFile.isAllowedIpv4(packed)) {
                throw LuaError("That IP cannot be used as a hosts target: $ipRaw")
            }
            entries[host] = HostEntry(host = host, ipv4 = HostsFile.formatIpv4(packed))
            i += 1
        }
        if (!table.get(i).isnil()) {
            throw LuaError("A plugin may map at most ${HostsFile.MAX_PER_PLUGIN} hosts.")
        }
        return entries.values.toList()
    }

    private fun firstString(table: LuaTable, vararg keys: String): String {
        for (key in keys) {
            val value = table.get(key)
            if (!value.isnil()) return value.tojstring()
        }
        return ""
    }
}
