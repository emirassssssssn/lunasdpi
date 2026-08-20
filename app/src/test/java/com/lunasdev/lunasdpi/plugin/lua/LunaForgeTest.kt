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

class LunaForgeTest {
    @Test
    fun scheduleRulesetBloomValidatorAndUi() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        assertThat(luna.get("features").get("forge").toboolean()).isTrue()
        assertThat(luna.get("forge").get("Schedule").istable()).isTrue()
        assertThat(luna.get("kit").get("Ruleset").istable()).isTrue()

        val rules = luna.get("Ruleset").get("new").call()
        rules.get("glob").call(rules, LuaValue.valueOf("*.ads.com"))
        rules.get("domain").call(rules, LuaValue.valueOf("tracker.example.com"))
        assertThat(rules.get("test").call(rules, LuaValue.valueOf("x.ads.com")).toboolean()).isTrue()
        assertThat(rules.get("test").call(rules, LuaValue.valueOf("ok.example.com")).toboolean()).isFalse()
        assertThat(rules.get("why").call(rules, LuaValue.valueOf("tracker.example.com")).tojstring()).contains("domain")

        val bloom = luna.get("Bloom").get("new").call()
        bloom.get("add").call(bloom, LuaValue.valueOf("evil.com"))
        assertThat(bloom.get("has").call(bloom, LuaValue.valueOf("evil.com")).toboolean()).isTrue()

        val v = luna.get("Validator").get("new").call()
        v.get("required").call(v, LuaValue.valueOf("host"))
        v.get("domain").call(v, LuaValue.valueOf("host"))
        val bad = LuaTable()
        val report = v.get("run").call(v, bad).checktable()
        assertThat(report.get("ok").toboolean()).isFalse()
        val good = LuaTable()
        good.set("host", "example.com")
        assertThat(v.get("run").call(v, good).get("ok").toboolean()).isTrue()

        val w = luna.get("Weighted").get("new").call()
        w.get("add").call(w, LuaValue.valueOf("a"), LuaValue.valueOf(1))
        assertThat(w.get("pick").call().tojstring()).isEqualTo("a")

        val spark = luna.get("Spark").get("of").call(
            LuaTable().also {
                it.set(1, LuaValue.valueOf(1))
                it.set(2, LuaValue.valueOf(8))
                it.set(3, LuaValue.valueOf(3))
            },
        )
        assertThat(spark.tojstring()).isNotEmpty()

        val ptr = LuaTable()
        luna.get("JsonPtr").get("set").call(ptr, LuaValue.valueOf("/meta/n"), LuaValue.valueOf(4))
        assertThat(luna.get("JsonPtr").get("get").call(ptr, LuaValue.valueOf("/meta/n")).toint()).isEqualTo(4)

        val ini = luna.get("Ini").get("parse").call(LuaValue.valueOf("[mod]\non=true"))
        assertThat(luna.get("Ini").get("get").call(ini, LuaValue.valueOf("mod.on")).tojstring()).isEqualTo("true")

        val flow = luna.get("Workflow").get("new").call(
            LuaTable().also {
                it.set(1, "setup")
                it.set(2, "rules")
                it.set(3, "done")
            },
        )
        assertThat(flow.get("name").call().tojstring()).isEqualTo("setup")
        assertThat(flow.get("next").call().tojstring()).isEqualTo("rules")

        val circuit = luna.get("Circuit").get("new").call(LuaValue.valueOf(2), LuaValue.valueOf(60_000))
        assertThat(circuit.get("allow").call().toboolean()).isTrue()
        circuit.get("fail").call()
        circuit.get("fail").call()
        assertThat(circuit.get("state").call().tojstring()).isEqualTo("open")
        assertThat(circuit.get("allow").call().toboolean()).isFalse()

        val steps = LuaTable()
        steps.set("type", "steps")
        val labels = LuaTable(); labels.set(1, "A"); labels.set(2, "B")
        steps.set("labels", labels)
        steps.set("current", 2)
        val fold = LuaTable()
        fold.set("type", "fold")
        fold.set("title", "Why")
        fold.set("body", "Because")
        val score = LuaTable()
        score.set("type", "score")
        score.set("label", "Coverage")
        score.set("value", 0.5)
        val section = LuaTable()
        section.set("type", "section")
        section.set("title", "Forge")
        val items = LuaTable()
        items.set(1, steps)
        items.set(2, fold)
        items.set(3, score)
        section.set("items", items)
        val page = LuaTable()
        page.set("title", "UI")
        val sections = LuaTable(); sections.set(1, section)
        page.set("sections", sections)
        val parsed = PluginUiParser.parse(page)
        assertThat(parsed.sections[0].items[0]).isInstanceOf(PluginUiItem.Steps::class.java)
        assertThat(parsed.sections[0].items[1]).isInstanceOf(PluginUiItem.Fold::class.java)
        assertThat(parsed.sections[0].items[2]).isInstanceOf(PluginUiItem.Score::class.java)
    }

    @Test
    fun listGroupAndMachineWildcard() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val list = luna.get("List").get("of").call(
            LuaValue.valueOf("a"),
            LuaValue.valueOf("b"),
            LuaValue.valueOf("a"),
        )
        val groups = list.get("group_by").call(
            list,
            object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue = arg
            },
        )
        assertThat(groups.get("a").get("size").call().toint()).isEqualTo(2)
        assertThat(list.get("unique").call().get("size").call().toint()).isEqualTo(2)

        val machine = luna.get("Machine").get("new").call(LuaValue.valueOf("idle"))
        machine.get("on").invoke(
            LuaValue.varargsOf(
                arrayOf(
                    machine,
                    LuaValue.valueOf("*"),
                    LuaValue.valueOf("panic"),
                    LuaValue.valueOf("off"),
                ),
            ),
        )
        assertThat(machine.get("send").call(machine, LuaValue.valueOf("panic")).toboolean()).isTrue()
        assertThat(machine.get("state").call().tojstring()).isEqualTo("off")
        assertThat(machine.get("history").call().get(1).tojstring()).isEqualTo("idle")
    }

    @Test
    fun healthLedgerRankerTokens() {
        val luna = LunaLuaApi.table("community.hosts.file", FakeBridge())
        val health = luna.get("Health").get("new").call()
        health.get("ok").invoke(
            LuaValue.varargsOf(
                arrayOf(health, LuaValue.valueOf("dns"), LuaValue.TRUE, LuaValue.valueOf("up")),
            ),
        )
        assertThat(health.get("all").call().toboolean()).isTrue()
        assertThat(health.get("worst").call().tojstring()).isEqualTo("success")

        val ledger = luna.get("Ledger").get("new").call()
        ledger.get("credit").call(ledger, LuaValue.valueOf("ads"), LuaValue.valueOf(3))
        assertThat(ledger.get("debit").call(ledger, LuaValue.valueOf("ads"), LuaValue.valueOf(1)).toboolean()).isTrue()
        assertThat(ledger.get("balance").call(ledger, LuaValue.valueOf("ads")).toint()).isEqualTo(2)

        val ranker = luna.get("Ranker").get("new").call()
        ranker.get("set").call(ranker, LuaValue.valueOf("a.com"), LuaValue.valueOf(2))
        ranker.get("bump").call(ranker, LuaValue.valueOf("b.com"), LuaValue.valueOf(5))
        assertThat(ranker.get("top").call(ranker, LuaValue.valueOf(1)).get(1).get("id").tojstring()).isEqualTo("b.com")

        val hosts = luna.get("Tokens").get("hosts").call(LuaValue.valueOf("0.0.0.0 ads.example.com\n# skip\n"))
        assertThat(hosts.get(1).tojstring()).isEqualTo("ads.example.com")

        val uf = luna.get("UnionFind").get("new").call()
        uf.get("union").call(uf, LuaValue.valueOf("a"), LuaValue.valueOf("b"))
        assertThat(uf.get("same").call(uf, LuaValue.valueOf("a"), LuaValue.valueOf("b")).toboolean()).isTrue()
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
        override fun appConfig(): Map<String, Any> = emptyMap()
    }
}
