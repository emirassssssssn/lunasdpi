package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.plugin.PluginLimits
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

internal object LunaDebug {
    fun install(luna: LuaTable, bridge: PluginNativeBridge) {
        val debug = LuaFn.module(
            "inspect" to LuaFn.o { LuaValue.valueOf(inspect(it, 0)) },
            "dump" to LuaFn.o { LuaValue.valueOf(inspect(it, 0)) },
            "assert" to LuaFn.t { cond, message ->
                if (!cond.toboolean()) {
                    throw LuaError(message.optjstring("assertion failed"))
                }
                cond
            },
            "expect" to LuaFn.t { value, type ->
                val want = type.checkjstring().lowercase()
                if (!matchesType(value, want)) {
                    throw LuaError("expected $want, got ${value.typename()}")
                }
                value
            },
            "fail" to LuaFn.o { throw LuaError(it.optjstring("fail")) },
            "traceback" to LuaFn.z { LuaValue.valueOf("plugin") },
            "time" to LuaFn.o { fn ->
                val start = System.nanoTime()
                fn.checkfunction().call()
                LuaValue.valueOf((System.nanoTime() - start) / 1_000_000.0)
            },
            "snapshot" to LuaFn.z { LuaFn.fromJava(bridge.debugSnapshot()) },
            "reload" to LuaFn.z {
                bridge.requestSelfReload()
                LuaValue.TRUE
            },
            "log" to LuaFn.o {
                bridge.log("debug", inspect(it, 0).take(500))
                LuaValue.TRUE
            },
        )
        luna.set("debug", debug)
        luna.set("dev", debug)
        luna.set("schema", schemaTable())
    }

    private fun schemaTable(): LuaTable = LuaFn.module(
        "check" to LuaFn.t { value, spec ->
            checkTable(value.checktable(), spec.checktable(), "value")
            LuaValue.TRUE
        },
        "is" to LuaFn.t { value, spec ->
            runCatching {
                checkTable(value.checktable(), spec.checktable(), "value")
                LuaValue.TRUE
            }.getOrElse { LuaValue.FALSE }
        },
        "type" to LuaFn.t { value, type ->
            LuaValue.valueOf(matchesType(value, type.checkjstring()))
        },
        "rule" to LuaFn.o { payload ->
            LunaLuaApi.luaToRule("schema.check", payload.checktable())
            LuaValue.TRUE
        },
        "hosts_line" to LuaFn.o {
            val line = it.checkjstring().substringBefore('#').trim()
            if (line.isEmpty()) return@o LuaValue.TRUE
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) throw LuaError("hosts line needs ip and hostname")
            if (HostsFile.parseIpv4(parts[0]) == null) throw LuaError("invalid ipv4")
            val host = DomainValidator.normalize(parts[1])
            if (!DomainValidator.isValidPattern(host)) throw LuaError("invalid hostname")
            LuaValue.TRUE
        },
        "domain" to LuaFn.o {
            val host = DomainValidator.normalize(it.checkjstring())
            LuaValue.valueOf(DomainValidator.isValidPattern(host))
        },
        "ipv4" to LuaFn.o { LuaValue.valueOf(HostsFile.parseIpv4(it.checkjstring()) != null) },
    )

    private fun checkTable(value: LuaTable, spec: LuaTable, path: String) {
        spec.keys().take(32).forEach { key ->
            val name = key.tojstring()
            val want = spec.get(key).tojstring().trim()
            val optional = want.startsWith("?")
            val type = want.removePrefix("?").ifBlank { "any" }
            val got = value.get(name)
            if (got.isnil()) {
                if (!optional) throw LuaError("missing $path.$name ($type)")
            } else if (!matchesType(got, type)) {
                throw LuaError("$path.$name expected $type, got ${got.typename()}")
            }
        }
    }

    private fun matchesType(value: LuaValue, type: String): Boolean {
        return when (type.lowercase()) {
            "any", "" -> true
            "string", "str" -> value.isstring()
            "number", "int", "float" -> value.isnumber()
            "boolean", "bool" -> value.isboolean()
            "table" -> value.istable()
            "function", "fn" -> value.isfunction()
            "ipv4" -> value.isstring() && HostsFile.parseIpv4(value.tojstring()) != null
            "domain" -> value.isstring() && DomainValidator.isValidPattern(DomainValidator.normalize(value.tojstring()))
            else -> value.typename() == type
        }
    }

    private fun inspect(value: LuaValue, depth: Int): String {
        if (depth > 3) return "..."
        return when {
            value.isnil() -> "nil"
            value.isboolean() -> value.toboolean().toString()
            value.isnumber() -> value.tojstring()
            value.isstring() -> "\"" + value.tojstring().take(120).replace("\"", "\\\"") + "\""
            value.isfunction() -> "function"
            value.istable() -> {
                val table = value.checktable()
                val parts = ArrayList<String>()
                table.keys().take(24).forEach { key ->
                    parts.add("${key.tojstring()}=${inspect(table.get(key), depth + 1)}")
                }
                "{" + parts.joinToString(", ").take(PluginLimits.MAX_I18N_CHARS) + "}"
            }
            else -> value.typename()
        }.take(800)
    }
}
