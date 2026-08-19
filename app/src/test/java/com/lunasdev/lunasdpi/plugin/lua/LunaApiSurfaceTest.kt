package com.lunasdev.lunasdpi.plugin.lua

import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.PluginStorage
import java.util.IdentityHashMap
import org.junit.Test
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LunaApiSurfaceTest {
    @Test
    fun exposesALargeDiscordJsStyleSurface() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val functions = countFunctions(luna)
        assertThat(functions).isAtLeast(200)
        listOf("user", "Client", "permissions", "Intents", "events", "storage", "rules", "hosts", "vpn", "ui", "Collection", "string", "table", "json", "Events", "PageBuilder", "EmbedBuilder", "RuleBuilder", "HostsBuilder", "REST").forEach { name ->
            assertThat(luna.get(name).istable()).isTrue()
        }
        val client = luna.get("Client")
        assertThat(countDirectFunctions(client)).isAtLeast(200)
        assertThat(client.raweq(luna.get("client"))).isTrue()
        val col = luna.get("Collection").get("new").call()
        col.get("set").call(col, LuaValue.valueOf("a"), LuaValue.valueOf("1"))
        assertThat(col.get("get").call(col, LuaValue.valueOf("a")).tojstring()).isEqualTo("1")
        assertThat(luna.get("string").get("trim").call(LuaValue.valueOf("  x  ")).tojstring()).isEqualTo("x")
        assertThat(client.get("trim").call(LuaValue.valueOf("  x  ")).tojstring()).isEqualTo("x")
        assertThat(luna.get("ui").get("alert").isfunction()).isTrue()
        assertThat(luna.get("ui").get("textarea").isfunction()).isTrue()
        val embed = luna.get("EmbedBuilder").get("new").call()
        embed.get("setTitle").call(embed, LuaValue.valueOf("Hosts"))
        val page = embed.get("build").call(embed)
        assertThat(page.get("title").tojstring()).isEqualTo("Hosts")
        assertThat(luna.get("fmt").get("bytes").call(LuaValue.valueOf(2048)).tojstring()).contains("KB")
        assertThat(client.get("isDomain").call(LuaValue.valueOf("example.com")).toboolean()).isTrue()
    }

    private fun countDirectFunctions(value: LuaValue): Int {
        if (!value.istable()) return 0
        val table = value.checktable()
        return table.keys().count { key -> table.get(key) is LuaFunction }
    }

    private fun countFunctions(root: LuaValue): Int {
        val seen = IdentityHashMap<LuaValue, Boolean>()
        var count = 0
        fun walk(value: LuaValue, depth: Int) {
            if (depth > 3) return
            when {
                value is LuaFunction -> count += 1
                value.istable() -> {
                    if (seen.put(value, true) != null) return
                    val table = value.checktable()
                    table.keys().forEach { key -> walk(table.get(key), depth + 1) }
                }
            }
        }
        walk(root, 0)
        return count
    }

    private class FakeBridge : PluginNativeBridge {
        private val bus = PluginEventBus()
        override fun log(level: String, message: String) = Unit
        override fun storage(): PluginStorage = error("unused")
        override fun granted(permission: PluginPermission): Boolean = true
        override fun locale(): String = "en"
        override fun appVersion(): String = "1.0.0"
        override fun translate(key: String, fallback: String): String = fallback
        override fun vpnPhase(): String = "disconnected"
        override fun vpnSnapshot(): Map<String, Any> = emptyMap()
        override fun requestVpnStart() = Unit
        override fun requestVpnStop() = Unit
        override fun listPluginRules(): List<DomainRule> = emptyList()
        override fun upsertPluginRule(rule: DomainRule) = Unit
        override fun deletePluginRule(id: String) = Unit
        override fun notify(title: String, text: String) = Unit
        override fun setHosts(entries: List<HostEntry>) = Unit
        override fun listHosts(): List<HostEntry> = emptyList()
        override fun clearHosts() = Unit
        override fun pluginId(): String = "community.hosts.file"
        override fun pluginName(): String = "Hosts"
        override fun pluginAuthor(): String = "Lunas"
        override fun pluginVersion(): String = "1.0.0"
        override fun events(): PluginEventBus = bus
        override fun schedule(ms: Long, fn: LuaValue, repeat: Boolean): Int = 1
        override fun cancelTimer(id: Int) = Unit
        override fun appConfig(): Map<String, Any> = mapOf("mode" to "automatic", "mtu" to 1500)
    }
}
