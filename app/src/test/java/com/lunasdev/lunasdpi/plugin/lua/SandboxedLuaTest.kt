package com.lunasdev.lunasdpi.plugin.lua

import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.data.model.DpiMode
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class SandboxedLuaTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun stripsOsIoLoadAndJava() {
        val root = folder.root
        File(root, "main.lua").writeText("return true")
        val globals = SandboxedLua.create(root, LuaTable())
        listOf("os", "io", "debug", "package", "load", "dofile", "java", "luajava").forEach { name ->
            assertThat(globals.get(name).isnil()).isTrue()
        }
        assertThat(globals.get("string").get("dump").isnil()).isTrue()
    }

    @Test
    fun requireOnlyLoadsModulesFolder() {
        val root = folder.root
        File(root, "secret.lua").writeText("return 'nope'")
        File(root, "modules").mkdirs()
        File(root, "modules/ok.lua").writeText("return 'yes'")
        val globals = SandboxedLua.create(root, LuaTable())
        val ok = globals.get("require").call(LuaValue.valueOf("ok"))
        assertThat(ok.tojstring()).isEqualTo("yes")
        assertThrows(LuaError::class.java) {
            globals.get("require").call(LuaValue.valueOf("secret"))
        }
        assertThrows(LuaError::class.java) {
            globals.get("require").call(LuaValue.valueOf("../secret"))
        }
    }

    @Test
    fun luaToRulePrefixesAndRejectsReservedName() {
        val table = LuaTable()
        table.set("id", "focus")
        table.set("name", "Focus list")
        table.set("domains", LuaTable().apply { set(1, "example.com") })
        val rule = LunaLuaApi.luaToRule("community.focus.list", table)
        assertThat(rule.id).isEqualTo("p:community.focus.list:focus")
        assertThat(rule.strategy).isEqualTo(DpiMode.AUTOMATIC)
        table.set("id", "p:evil.plugin:x")
        val stolen = LunaLuaApi.luaToRule("community.focus.list", table)
        assertThat(stolen.id.startsWith("p:community.focus.list:")).isTrue()
        table.set("name", "Discord")
        assertThrows(LuaError::class.java) {
            LunaLuaApi.luaToRule("community.focus.list", table)
        }
    }

    @Test
    fun luaToHostsValidatesIpv4() {
        val table = LuaTable()
        val row = LuaTable()
        row.set("host", "growtopia1.com")
        row.set("ip", "192.168.1.10")
        table.set(1, row)
        val entries = LunaLuaApi.luaToHosts(table)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].host).isEqualTo("growtopia1.com")
        assertThat(entries[0].ipv4).isEqualTo("192.168.1.10")
        row.set("ip", "10.7.0.1")
        assertThrows(LuaError::class.java) {
            LunaLuaApi.luaToHosts(table)
        }
    }
}
