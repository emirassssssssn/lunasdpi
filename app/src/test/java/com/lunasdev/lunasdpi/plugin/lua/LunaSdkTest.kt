package com.lunasdev.lunasdpi.plugin.lua

import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.PluginStorage
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction

class LunaSdkTest {
    @Test
    fun collectionsAndParsersWorkWithColonCalls() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val list = luna.get("List").get("new").call()
        list.get("push").call(list, LuaValue.valueOf("alpha"))
        list.get("push").call(list, LuaValue.valueOf("beta"))
        assertThat(list.get("size").call().toint()).isEqualTo(2)
        assertThat(list.get("get").call(list, LuaValue.valueOf(1)).tojstring()).isEqualTo("alpha")
        assertThat(list.get("join").call(list, LuaValue.valueOf("|")).tojstring()).isEqualTo("alpha|beta")

        val set = luna.get("Set").get("from").call(list.get("to_table").call())
        set.get("add").call(set, LuaValue.valueOf("gamma"))
        assertThat(set.get("has").call(set, LuaValue.valueOf("beta")).toboolean()).isTrue()

        val hosts = luna.get("DomainSet").get("new").call()
        hosts.get("add").call(hosts, LuaValue.valueOf("*.example.com"))
        assertThat(hosts.get("test").call(hosts, LuaValue.valueOf("a.example.com")).toboolean()).isTrue()
        assertThat(hosts.get("test").call(hosts, LuaValue.valueOf("other.com")).toboolean()).isFalse()

        val cidr = luna.get("CidrSet").get("new").call()
        cidr.get("add").call(cidr, LuaValue.valueOf("10.0.0.0/8"))
        assertThat(cidr.get("contains").call(cidr, LuaValue.valueOf("10.1.2.3")).toboolean()).isTrue()
        assertThat(cidr.get("contains").call(cidr, LuaValue.valueOf("11.0.0.1")).toboolean()).isFalse()
    }

    @Test
    fun textNetAndReactiveTypes() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val url = luna.get("URL").get("parse").call(LuaValue.valueOf("https://github.com/you/repo?tab=readme"))
        assertThat(url.get("host").tojstring()).isEqualTo("github.com")
        assertThat(url.get("ok").call().toboolean()).isTrue()
        assertThat(url.get("is_github").call().toboolean()).isTrue()
        assertThat(url.get("query").get("tab").tojstring()).isEqualTo("readme")

        val vars = LuaTable()
        vars.set("name", "Luna")
        assertThat(
            luna.get("Template").get("render").call(LuaValue.valueOf("hi {name}"), vars).tojstring(),
        ).isEqualTo("hi Luna")

        assertThat(luna.get("Interval").get("parse").call(LuaValue.valueOf("2m")).toint()).isEqualTo(120)
        assertThat(luna.get("Fuzzy").get("ratio").call(LuaValue.valueOf("abc"), LuaValue.valueOf("abc")).todouble())
            .isEqualTo(1.0)
        assertThat(luna.get("Csv").get("parse").call(LuaValue.valueOf("a,b\n1,2")).get(1).get(1).tojstring())
            .isEqualTo("a")
        assertThat(luna.get("string").get("camel").call(LuaValue.valueOf("hello-world")).tojstring())
            .isEqualTo("helloWorld")

        val nums = LuaTable()
        nums.set(1, LuaValue.valueOf(2))
        nums.set(2, LuaValue.valueOf(4))
        assertThat(luna.get("table").get("sum").call(nums).todouble()).isEqualTo(6.0)

        val store = luna.get("Store").get("new").call()
        var seen = ""
        store.get("subscribe").call(store, object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                seen = arg.get("key").tojstring()
                return LuaValue.NIL
            }
        })
        store.get("set").call(store, LuaValue.valueOf("k"), LuaValue.valueOf("v"))
        assertThat(seen).isEqualTo("k")
        assertThat(store.get("get").call(store, LuaValue.valueOf("k")).tojstring()).isEqualTo("v")

        val result = luna.get("Result").get("ok").call(LuaValue.valueOf(3))
        assertThat(result.get("is_ok").call().toboolean()).isTrue()
        assertThat(result.get("unwrap").call().toint()).isEqualTo(3)
    }

    @Test
    fun formBuildersEmitPagesAndClientFactoriesAreColonSafe() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val form = luna.get("FormBuilder").get("new").call(LuaValue.valueOf("Focus"))
        val spec = LuaTable()
        spec.set("text", "Hello")
        form.get("note").call(form, spec)
        val page = form.get("build").call()
        assertThat(page.get("type").tojstring()).isEqualTo("page")
        assertThat(page.get("title").tojstring()).isEqualTo("Focus")
        assertThat(page.get("sections").get(1).get("items").get(1).istable()).isTrue()

        val client = luna.get("Client")
        val list = client.get("newList").call()
        list.get("push").call(list, LuaValue.valueOf("x"))
        assertThat(list.get("first").call().tojstring()).isEqualTo("x")
        assertThat(client.get("parseUrl").call(LuaValue.valueOf("https://github.com/a/b")).get("host").tojstring())
            .isEqualTo("github.com")
        assertThat(luna.get("features").get("sdk").toboolean()).isTrue()
        assertThat(luna.get("systems").get("network").toboolean()).isFalse()
        assertThat(luna.get("Constants").get("MAX_RULES").toint()).isEqualTo(32)
        assertThat(luna.get("Enums").get("VpnPhase").get("connected").tojstring()).isEqualTo("connected")
        assertThat(luna.get("sdk").get("List").istable()).isTrue()
    }

    @Test
    fun sandboxFlagsStayClosed() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val systems = luna.get("systems")
        assertThat(systems.get("tun").toboolean()).isFalse()
        assertThat(systems.get("tls").toboolean()).isFalse()
        assertThat(systems.get("shell").toboolean()).isFalse()
        assertThat(systems.get("java").toboolean()).isFalse()
        assertThat(luna.get("List").get("new").isfunction()).isTrue()
        val memo = luna.get("Memo").get("new").call()
        val compute = object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.valueOf("once")
        }
        memo.get("compute").call(memo, LuaValue.valueOf("a"), compute)
        assertThat(memo.get("get").call(memo, LuaValue.valueOf("a")).tojstring()).isEqualTo("once")
        memo.get("compute").call(memo, LuaValue.valueOf("a"), object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.valueOf("twice")
        })
        assertThat(memo.get("get").call(memo, LuaValue.valueOf("a")).tojstring()).isEqualTo("once")
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
