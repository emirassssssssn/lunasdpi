package com.lunasdev.lunasdpi.plugin.lua

import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.PluginStorage
import com.lunasdev.lunasdpi.plugin.PluginUiItem
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

class LunaKitTest {
    @Test
    fun machinePipelineExprAndQuery() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val machine = luna.get("Machine").get("new").call(LuaValue.valueOf("idle"))
        machine.get("on").invoke(
            LuaValue.varargsOf(
                arrayOf(
                    machine,
                    LuaValue.valueOf("idle"),
                    LuaValue.valueOf("start"),
                    LuaValue.valueOf("on"),
                ),
            ),
        )
        assertThat(machine.get("send").call(machine, LuaValue.valueOf("start")).toboolean()).isTrue()
        assertThat(machine.get("state").call().tojstring()).isEqualTo("on")

        val pipe = luna.get("Pipeline").get("new").call()
        pipe.get("use").call(pipe, object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.valueOf(arg.toint() + 1)
        })
        assertThat(pipe.get("run").call(pipe, LuaValue.valueOf(3)).toint()).isEqualTo(4)

        val env = LuaTable()
        env.set("n", 12)
        env.set("on", 1)
        assertThat(luna.get("Expr").get("bool").call(LuaValue.valueOf("n > 10 and on"), env).toboolean()).isTrue()

        val rows = LuaTable()
        val a = LuaTable(); a.set("name", "b"); rows.set(1, a)
        val b = LuaTable(); b.set("name", "a"); rows.set(2, b)
        val q = luna.get("TableQuery").get("from").call(rows)
        q.get("sort").call(q, LuaValue.valueOf("name"))
        assertThat(q.get("first").call().get("name").tojstring()).isEqualTo("a")
    }

    @Test
    fun routerPolicyDashboardAndJsonPath() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val router = luna.get("Router").get("new").call()
        router.get("add").call(router, LuaValue.valueOf("/hosts/:name"), object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = arg.get("name")
        })
        assertThat(router.get("match").call(router, LuaValue.valueOf("/hosts/grow")).tojstring()).isEqualTo("grow")

        val policy = luna.get("Policy").get("new").call()
        policy.get("allow").call(policy, LuaValue.valueOf("*.example.com"))
        policy.get("deny").call(policy, LuaValue.valueOf("bad.example.com"))
        assertThat(policy.get("test").call(policy, LuaValue.valueOf("ok.example.com")).toboolean()).isTrue()
        assertThat(policy.get("test").call(policy, LuaValue.valueOf("bad.example.com")).toboolean()).isFalse()

        val root = LuaTable()
        luna.get("JsonPath").get("set").call(root, LuaValue.valueOf("meta.count"), LuaValue.valueOf(2))
        assertThat(luna.get("JsonPath").get("get").call(root, LuaValue.valueOf("meta.count")).toint()).isEqualTo(2)

        val dash = luna.get("Dashboard").get("new").call(LuaValue.valueOf("Status"))
        val spec = LuaTable()
        spec.set("label", "Rules")
        spec.set("value", "3")
        dash.get("stat").call(dash, spec)
        val page = dash.get("build").call()
        assertThat(page.get("title").tojstring()).isEqualTo("Status")
        val parsed = PluginUiParser.parse(page)
        assertThat(parsed.sections[0].items[0]).isInstanceOf(PluginUiItem.Stat::class.java)
    }

    @Test
    fun listExtrasAndNewUiNodes() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val list = luna.get("List").get("of").call(LuaValue.valueOf(1), LuaValue.valueOf(2), LuaValue.valueOf(3))
        assertThat(list.get("sum").call().todouble()).isEqualTo(6.0)
        assertThat(list.get("take").call(list, LuaValue.valueOf(2)).get("size").call().toint()).isEqualTo(2)

        val item = LuaTable()
        item.set("type", "list_item")
        item.set("title", "example.com")
        item.set("body", "blocked")
        item.set("trailing", "on")
        val empty = LuaTable()
        empty.set("type", "empty")
        empty.set("text", "None")
        val chips = LuaTable()
        chips.set("type", "chips")
        val labels = LuaTable(); labels.set(1, "dns"); chips.set("labels", labels)
        val section = LuaTable()
        section.set("type", "section")
        section.set("title", "Box")
        val items = LuaTable()
        items.set(1, item)
        items.set(2, empty)
        items.set(3, chips)
        section.set("items", items)
        val page = LuaTable()
        page.set("type", "page")
        page.set("title", "UI")
        val sections = LuaTable(); sections.set(1, section)
        page.set("sections", sections)
        val parsed = PluginUiParser.parse(page)
        assertThat(parsed.sections[0].items[0]).isInstanceOf(PluginUiItem.ListItem::class.java)
        assertThat(parsed.sections[0].items[1]).isInstanceOf(PluginUiItem.Empty::class.java)
        assertThat(parsed.sections[0].items[2]).isInstanceOf(PluginUiItem.Chips::class.java)

        assertThat(luna.get("features").get("kit").toboolean()).isTrue()
        assertThat(luna.get("kit").get("Machine").istable()).isTrue()
        assertThat(luna.get("Constants").get("MAX_UI_ITEMS").toint()).isEqualTo(64)
    }

    @Test
    fun matchbookAndSearch() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val book = luna.get("Matchbook").get("new").call()
        book.get("when").call(book, LuaValue.valueOf("*.com"), LuaValue.valueOf("web"))
        assertThat(book.get("match").call(book, LuaValue.valueOf("a.com")).tojstring()).isEqualTo("web")
        val idx = luna.get("SearchIndex").get("new").call()
        idx.get("add").call(idx, LuaValue.valueOf("1"), LuaValue.valueOf("growtopia hosts"))
        assertThat(idx.get("search").call(idx, LuaValue.valueOf("grow")).get(1).tojstring()).isEqualTo("1")
        luna.get("Once")
        luna.get("Throttle").get("new").call()
        val once = luna.get("Once").get("new").call(object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf("x")
        })
        assertThat(once.get("run").call().tojstring()).isEqualTo("x")
        assertThat(once.get("run").call().tojstring()).isEqualTo("x")
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
