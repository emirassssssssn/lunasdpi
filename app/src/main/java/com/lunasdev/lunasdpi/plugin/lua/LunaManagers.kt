package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.plugin.PLUGIN_API_LEVEL
import com.lunasdev.lunasdpi.plugin.PluginLimits
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.PluginRuleIds
import com.lunasdev.lunasdpi.plugin.PluginStorage
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

internal object LunaManagers {
    fun install(luna: LuaTable, pluginId: String, bridge: PluginNativeBridge) {
        luna.set("user", userTable(bridge))
        luna.set("permissions", permissionsTable(bridge))
        luna.set("events", eventsTable(bridge))
        luna.set("storage", storageTable(bridge))
        luna.set("app", appTable(bridge))
        luna.set("i18n", i18nTable(bridge))
        luna.set("clock", clockTable(bridge))
        luna.set("log", logTable(bridge))
        luna.set("notify", notifyTable(bridge))
        luna.set("vpn", vpnTable(bridge))
        luna.set("rules", rulesTable(pluginId, bridge))
        luna.set("hosts", hostsTable(bridge))
        luna.set("fs", fsTable(bridge))
    }

    private fun requirePerm(bridge: PluginNativeBridge, permission: PluginPermission) {
        if (!bridge.granted(permission)) {
            throw LuaError("Permission denied: ${permission.manifestKey()}")
        }
    }

    private fun userTable(bridge: PluginNativeBridge): LuaTable = LuaFn.module(
        "id" to LuaFn.z { LuaValue.valueOf(bridge.pluginId()) },
        "name" to LuaFn.z { LuaValue.valueOf(bridge.pluginName()) },
        "author" to LuaFn.z { LuaValue.valueOf(bridge.pluginAuthor()) },
        "version" to LuaFn.z { LuaValue.valueOf(bridge.pluginVersion()) },
        "tag" to LuaFn.z { LuaValue.valueOf("${bridge.pluginName()}@${bridge.pluginVersion()}") },
        "locale" to LuaFn.z { LuaValue.valueOf(bridge.locale()) },
        "api_level" to LuaFn.z { LuaValue.valueOf(PLUGIN_API_LEVEL) },
        "toJSON" to LuaFn.z {
            LuaFn.fromJava(
                mapOf(
                    "id" to bridge.pluginId(),
                    "name" to bridge.pluginName(),
                    "author" to bridge.pluginAuthor(),
                    "version" to bridge.pluginVersion(),
                ),
            )
        },
        "permissions" to LuaFn.z { permissionsTable(bridge) },
        "has_permission" to LuaFn.o {
            val perm = PluginPermission.fromManifest(it.tojstring()) ?: return@o LuaValue.FALSE
            LuaValue.valueOf(bridge.granted(perm))
        },
        "hasPermission" to LuaFn.o {
            val perm = PluginPermission.fromManifest(it.tojstring()) ?: return@o LuaValue.FALSE
            LuaValue.valueOf(bridge.granted(perm))
        },
        "apiLevel" to LuaFn.z { LuaValue.valueOf(PLUGIN_API_LEVEL) },
    )

    private fun permissionsTable(bridge: PluginNativeBridge): LuaTable {
        val table = LuaFn.module(
            "has" to LuaFn.o {
                val perm = PluginPermission.fromManifest(it.tojstring()) ?: return@o LuaValue.FALSE
                LuaValue.valueOf(bridge.granted(perm))
            },
            "toArray" to LuaFn.z {
                LuaFn.fromJava(PluginPermission.entries.filter { bridge.granted(it) }.map { it.manifestKey() })
            },
            "bitfield" to LuaFn.z {
                var bits = 0
                PluginPermission.entries.forEachIndexed { index, perm ->
                    if (bridge.granted(perm)) bits = bits or (1 shl index)
                }
                LuaValue.valueOf(bits)
            },
            "missing" to LuaFn.o {
                val perm = PluginPermission.fromManifest(it.tojstring()) ?: return@o LuaValue.TRUE
                LuaValue.valueOf(!bridge.granted(perm))
            },
            "any" to LuaFn.o {
                LuaFn.stringList(it, 16).any { key ->
                    PluginPermission.fromManifest(key)?.let { perm -> bridge.granted(perm) } == true
                }.let { LuaValue.valueOf(it) }
            },
            "all" to LuaFn.o {
                val keys = LuaFn.stringList(it, 16)
                LuaValue.valueOf(
                    keys.isNotEmpty() && keys.all { key ->
                        PluginPermission.fromManifest(key)?.let { perm -> bridge.granted(perm) } == true
                    },
                )
            },
        )
        PluginPermission.entries.forEach { perm ->
            table.set(perm.manifestKey(), LuaValue.valueOf(bridge.granted(perm)))
            table.set(perm.name.lowercase(), LuaValue.valueOf(bridge.granted(perm)))
        }
        return table
    }

    private fun eventsTable(bridge: PluginNativeBridge): LuaTable = LuaFn.module(
        "VPN_PHASE" to LuaValue.valueOf("vpnPhase"),
        "READY" to LuaValue.valueOf("ready"),
        "SETTING_CHANGED" to LuaValue.valueOf("settingChanged"),
        "VPN_CONNECTED" to LuaValue.valueOf("vpnConnected"),
        "VPN_DISCONNECTED" to LuaValue.valueOf("vpnDisconnected"),
        "ERROR" to LuaValue.valueOf("error"),
        "on" to LuaFn.t { name, fn ->
            bridge.events().on(name.checkjstring(), fn.checkfunction())
            LuaValue.TRUE
        },
        "once" to LuaFn.t { name, fn ->
            bridge.events().once(name.checkjstring(), fn.checkfunction())
            LuaValue.TRUE
        },
        "off" to LuaFn.t { name, fn ->
            bridge.events().off(name.checkjstring(), if (fn.isfunction()) fn else null)
            LuaValue.TRUE
        },
        "removeAllListeners" to LuaFn.o {
            bridge.events().off(it.checkjstring(), null)
            LuaValue.TRUE
        },
        "listenerCount" to LuaFn.o { LuaValue.valueOf(bridge.events().listenerCount(it.checkjstring())) },
        "eventNames" to LuaFn.z { LuaFn.fromJava(bridge.events().names()) },
    )

    private fun storageTable(bridge: PluginNativeBridge): LuaTable {
        val table = LuaFn.module(
            "get" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                bridge.storage().get(it.checkjstring())?.let { value -> LuaValue.valueOf(value) } ?: LuaValue.NIL
            },
            "set" to LuaFn.t { key, value ->
                requirePerm(bridge, PluginPermission.STORAGE)
                val text = value.tojstring()
                if (text.length > PluginStorage.MAX_VALUE_CHARS) throw LuaError("Storage value is too large.")
                bridge.storage().set(key.checkjstring(), text)
                LuaValue.TRUE
            },
            "remove" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                bridge.storage().remove(it.checkjstring())
                LuaValue.TRUE
            },
            "delete" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                bridge.storage().remove(it.checkjstring())
                LuaValue.TRUE
            },
            "has" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                LuaValue.valueOf(bridge.storage().has(it.checkjstring()))
            },
            "keys" to LuaFn.z {
                requirePerm(bridge, PluginPermission.STORAGE)
                LuaFn.fromJava(bridge.storage().keys())
            },
            "size" to LuaFn.z {
                requirePerm(bridge, PluginPermission.STORAGE)
                LuaValue.valueOf(bridge.storage().size())
            },
            "length" to LuaFn.z {
                requirePerm(bridge, PluginPermission.STORAGE)
                LuaValue.valueOf(bridge.storage().size())
            },
            "get_number" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                val raw = bridge.storage().get(it.checkjstring()) ?: return@o LuaValue.NIL
                raw.toDoubleOrNull()?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            },
            "set_number" to LuaFn.t { key, value ->
                requirePerm(bridge, PluginPermission.STORAGE)
                bridge.storage().set(key.checkjstring(), value.todouble().toString())
                LuaValue.TRUE
            },
            "get_bool" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                when (bridge.storage().get(it.checkjstring())) {
                    "1", "true", "yes" -> LuaValue.TRUE
                    "0", "false", "no" -> LuaValue.FALSE
                    else -> LuaValue.NIL
                }
            },
            "set_bool" to LuaFn.t { key, value ->
                requirePerm(bridge, PluginPermission.STORAGE)
                bridge.storage().set(key.checkjstring(), if (value.toboolean()) "1" else "0")
                LuaValue.TRUE
            },
            "get_json" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                val raw = bridge.storage().get(it.checkjstring()) ?: return@o LuaValue.NIL
                runCatching { jsonObjectToLua(JSONObject(raw)) }.getOrElse { LuaValue.NIL }
            },
            "set_json" to LuaFn.t { key, value ->
                requirePerm(bridge, PluginPermission.STORAGE)
                val encoded = LunaStdLibEncode.encode(value)
                if (encoded.length > PluginStorage.MAX_VALUE_CHARS) throw LuaError("Storage value is too large.")
                bridge.storage().set(key.checkjstring(), encoded)
                LuaValue.TRUE
            },
            "incr" to LuaFn.t { key, value ->
                requirePerm(bridge, PluginPermission.STORAGE)
                val current = bridge.storage().get(key.checkjstring())?.toDoubleOrNull() ?: 0.0
                val next = current + value.optdouble(1.0)
                bridge.storage().set(key.checkjstring(), next.toString())
                LuaValue.valueOf(next)
            },
            "clear" to LuaFn.z {
                requirePerm(bridge, PluginPermission.STORAGE)
                bridge.storage().clear()
                LuaValue.TRUE
            },
            "mget" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                val keys = LuaFn.stringList(it, 32)
                val out = LuaTable()
                keys.forEach { key ->
                    bridge.storage().get(key)?.let { value -> out.set(key, value) }
                }
                out
            },
            "mset" to LuaFn.o {
                requirePerm(bridge, PluginPermission.STORAGE)
                val table = it.checktable()
                table.keys().take(32).forEach { key ->
                    val text = table.get(key).tojstring()
                    if (text.length > PluginStorage.MAX_VALUE_CHARS) throw LuaError("Storage value is too large.")
                    bridge.storage().set(key.tojstring(), text)
                }
                LuaValue.TRUE
            },
        )
        table.set("getJSON", table.get("get_json"))
        table.set("setJSON", table.get("set_json"))
        table.set("getNumber", table.get("get_number"))
        table.set("setNumber", table.get("set_number"))
        table.set("getBool", table.get("get_bool"))
        table.set("setBool", table.get("set_bool"))
        table.set("multiGet", table.get("mget"))
        table.set("multiSet", table.get("mset"))
        return table
    }

    private fun appTable(bridge: PluginNativeBridge): LuaTable = LuaFn.module(
        "version" to LuaFn.z { LuaValue.valueOf(bridge.appVersion()) },
        "api_level" to LuaFn.z { LuaValue.valueOf(PLUGIN_API_LEVEL) },
        "locale" to LuaFn.z { LuaValue.valueOf(bridge.locale()) },
        "name" to LuaFn.z { LuaValue.valueOf("Lunas DPI") },
        "id" to LuaFn.z { LuaValue.valueOf(bridge.pluginId()) },
        "config" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaFn.fromJava(bridge.appConfig())
        },
        "mode" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf(bridge.appConfig()["mode"]?.toString() ?: "automatic")
        },
        "dns_mode" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf(bridge.appConfig()["dns_mode"]?.toString() ?: "automatic")
        },
        "mtu" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf((bridge.appConfig()["mtu"] as? Number)?.toInt() ?: 1500)
        },
        "ipv6_mode" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf(bridge.appConfig()["ipv6_mode"]?.toString() ?: "block")
        },
        "block_quic" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf(bridge.appConfig()["block_quic"] == true)
        },
        "log_level" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf((bridge.appConfig()["log_level"] as? Number)?.toInt() ?: 2)
        },
        "per_app_mode" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaValue.valueOf(bridge.appConfig()["per_app_mode"]?.toString() ?: "all")
        },
        "toJSON" to LuaFn.z {
            requirePerm(bridge, PluginPermission.APP_READ)
            LuaFn.fromJava(bridge.appConfig())
        },
    )

    private fun i18nTable(bridge: PluginNativeBridge): LuaTable = LuaFn.module(
        "locale" to LuaFn.z { LuaValue.valueOf(bridge.locale()) },
        "language" to LuaFn.z { LuaValue.valueOf(bridge.locale().take(2)) },
        "t" to LuaFn.v { args ->
            val key = args.arg(1).checkjstring()
            val fallback = if (args.arg(2).isnil()) key else args.arg(2).tojstring()
            LuaValue.valueOf(interpolate(bridge.translate(key, fallback), args.arg(3)))
        },
        "has" to LuaFn.o {
            val key = it.checkjstring()
            LuaValue.valueOf(bridge.translate(key, "") != "")
        },
        "translate" to LuaFn.v { args ->
            val key = args.arg(1).checkjstring()
            val fallback = if (args.arg(2).isnil()) key else args.arg(2).tojstring()
            LuaValue.valueOf(interpolate(bridge.translate(key, fallback), args.arg(3)))
        },
    )

    private fun clockTable(bridge: PluginNativeBridge): LuaTable = LuaFn.module(
        "now" to LuaFn.z { LuaValue.valueOf((System.currentTimeMillis() / 1000L).toDouble()) },
        "now_ms" to LuaFn.z { LuaValue.valueOf(System.currentTimeMillis().toDouble()) },
        "iso" to LuaFn.z { LuaValue.valueOf(java.time.Instant.now().toString()) },
        "setTimeout" to LuaFn.t { ms, fn ->
            LuaValue.valueOf(bridge.schedule(ms.optdouble(2000.0).toLong(), fn.checkfunction(), repeat = false))
        },
        "setInterval" to LuaFn.t { ms, fn ->
            LuaValue.valueOf(bridge.schedule(ms.optdouble(5000.0).toLong(), fn.checkfunction(), repeat = true))
        },
        "clearTimeout" to LuaFn.o {
            bridge.cancelTimer(it.toint())
            LuaValue.TRUE
        },
        "clearInterval" to LuaFn.o {
            bridge.cancelTimer(it.toint())
            LuaValue.TRUE
        },
        "after" to LuaFn.t { ms, fn ->
            LuaValue.valueOf(bridge.schedule(ms.optdouble(2000.0).toLong(), fn.checkfunction(), repeat = false))
        },
        "count" to LuaFn.z { LuaValue.valueOf(bridge.timerCount()) },
        "remaining" to LuaFn.z { LuaValue.valueOf((PluginLimits.MAX_TIMERS - bridge.timerCount()).coerceAtLeast(0)) },
    )

    private fun logTable(bridge: PluginNativeBridge): LuaTable {
        val table = LuaTable()
        listOf("debug", "info", "warn", "error").forEach { level ->
            table.set(level, LuaFn.o {
                bridge.log(level, it.tojstring().take(500))
                LuaValue.NIL
            })
        }
        table.set("log", LuaFn.t { level, message ->
            bridge.log(level.tojstring(), message.tojstring().take(500))
            LuaValue.NIL
        })
        table.set("print", LuaFn.o {
            bridge.log("info", it.tojstring().take(500))
            LuaValue.NIL
        })
        table.set("recent", LuaFn.z { LuaValue.valueOf(bridge.recentLog()) })
        table.set("clear", LuaFn.z {
            bridge.clearLog()
            LuaValue.TRUE
        })
        return table
    }

    private fun notifyTable(bridge: PluginNativeBridge): LuaTable {
        fun show(title: String, text: String): LuaValue {
            requirePerm(bridge, PluginPermission.NOTIFY)
            return LuaValue.valueOf(bridge.notifyAllowed().also { allowed ->
                if (allowed) bridge.notify(title.take(40), text.take(120))
            })
        }
        return LuaFn.module(
            "show" to LuaFn.t { title, text -> show(title.tojstring(), text.tojstring()) },
            "info" to LuaFn.t { title, text -> show(title.tojstring(), text.tojstring()) },
            "success" to LuaFn.t { title, text -> show(title.tojstring(), text.tojstring()) },
            "warn" to LuaFn.t { title, text -> show(title.tojstring(), text.tojstring()) },
            "error" to LuaFn.t { title, text -> show(title.tojstring(), text.tojstring()) },
            "allowed" to LuaFn.z {
                requirePerm(bridge, PluginPermission.NOTIFY)
                LuaValue.valueOf(bridge.notifyAllowed())
            },
            "canShow" to LuaFn.z {
                requirePerm(bridge, PluginPermission.NOTIFY)
                LuaValue.valueOf(bridge.notifyAllowed())
            },
            "cooldown_ms" to LuaFn.z {
                requirePerm(bridge, PluginPermission.NOTIFY)
                LuaValue.valueOf(bridge.notifyCooldownMs().toDouble())
            },
            "remaining" to LuaFn.z {
                requirePerm(bridge, PluginPermission.NOTIFY)
                LuaValue.valueOf(bridge.notifyRemainingHour())
            },
        )
    }

    private fun vpnTable(bridge: PluginNativeBridge): LuaTable {
        fun snap(): LuaTable {
            requirePerm(bridge, PluginPermission.VPN_READ)
            return LuaFn.fromJava(bridge.vpnSnapshot()).checktable()
        }
        val table = LuaFn.module(
            "state" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnPhase())
            },
            "phase" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnPhase())
            },
            "is_active" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnPhase() == "connected")
            },
            "connected" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnPhase() == "connected")
            },
            "snapshot" to LuaFn.z { snap() },
            "stats" to LuaFn.z { snap() },
            "fetch" to LuaFn.z { snap() },
            "uptime" to LuaFn.z {
                val value = bridge.vpnSnapshot()["uptime_seconds"]
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaFn.fromJava(value ?: 0)
            },
            "alive" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnSnapshot()["engine_alive"] == true)
            },
            "tun" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnSnapshot()["tun_active"] == true)
            },
            "packets" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaFn.fromJava(bridge.vpnSnapshot()["packets_processed"] ?: 0)
            },
            "dropped" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaFn.fromJava(bridge.vpnSnapshot()["packets_dropped"] ?: 0)
            },
            "bytes_in" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaFn.fromJava(bridge.vpnSnapshot()["bytes_in"] ?: 0)
            },
            "bytes_out" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaFn.fromJava(bridge.vpnSnapshot()["bytes_out"] ?: 0)
            },
            "dns_queries" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaFn.fromJava(bridge.vpnSnapshot()["dns_queries"] ?: 0)
            },
            "strategy" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_READ)
                LuaValue.valueOf(bridge.vpnSnapshot()["strategy"]?.toString() ?: "automatic")
            },
            "request_start" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                bridge.requestVpnStart()
                LuaValue.TRUE
            },
            "request_stop" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                bridge.requestVpnStop()
                LuaValue.TRUE
            },
            "start" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                bridge.requestVpnStart()
                LuaValue.TRUE
            },
            "stop" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                bridge.requestVpnStop()
                LuaValue.TRUE
            },
            "connect" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                bridge.requestVpnStart()
                LuaValue.TRUE
            },
            "disconnect" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                bridge.requestVpnStop()
                LuaValue.TRUE
            },
            "can_control" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                LuaValue.valueOf(bridge.vpnControlAllowed())
            },
            "control_cooldown_ms" to LuaFn.z {
                requirePerm(bridge, PluginPermission.VPN_CONTROL)
                LuaValue.valueOf(bridge.vpnControlCooldownMs().toDouble())
            },
        )
        table.set("isActive", table.get("is_active"))
        table.set("requestStart", table.get("request_start"))
        table.set("requestStop", table.get("request_stop"))
        table.set("bytesIn", table.get("bytes_in"))
        table.set("bytesOut", table.get("bytes_out"))
        table.set("dnsQueries", table.get("dns_queries"))
        table.set("canControl", table.get("can_control"))
        table.set("controlCooldownMs", table.get("control_cooldown_ms"))
        return table
    }

    private fun rulesTable(pluginId: String, bridge: PluginNativeBridge): LuaTable {
        fun list(): List<DomainRule> {
            requirePerm(bridge, PluginPermission.RULES_READ)
            return bridge.listPluginRules()
        }
        fun structure(rule: DomainRule): LuaTable {
            val table = LunaLuaApi.ruleToLua(rule)
            table.set("edit", LuaFn.o { patch ->
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val merged = LunaLuaApi.luaToRule(pluginId, patch.checktable()).let { next ->
                    if (next.id != rule.id) next.copy(id = rule.id) else next
                }
                bridge.upsertPluginRule(merged)
                structure(merged)
            })
            table.set("delete", LuaFn.z {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                bridge.deletePluginRule(rule.id)
                LuaValue.TRUE
            })
            table.set("enable", LuaFn.z {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                bridge.upsertPluginRule(rule.copy(enabled = true))
                LuaValue.TRUE
            })
            table.set("disable", LuaFn.z {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                bridge.upsertPluginRule(rule.copy(enabled = false))
                LuaValue.TRUE
            })
            table.set("toJSON", LuaFn.z { LunaLuaApi.ruleToLua(rule) })
            return table
        }
        val cache = LuaFn.module(
            "size" to LuaFn.z { LuaValue.valueOf(list().size) },
            "get" to LuaFn.o {
                val id = it.checkjstring()
                list().find { rule -> rule.id == id || rule.id.endsWith(":$id") }?.let { structure(it) } ?: LuaValue.NIL
            },
            "find" to LuaFn.o {
                val needle = it.checkjstring()
                list().find { rule -> rule.name.equals(needle, ignoreCase = true) || rule.id.endsWith(needle) }
                    ?.let { structure(it) } ?: LuaValue.NIL
            },
            "array" to LuaFn.z { LuaFn.fromJava(list().map { structure(it) }) },
        )
        val table = LuaFn.module(
            "list" to LuaFn.z {
                val out = LuaTable()
                list().forEachIndexed { index, rule -> out.set(index + 1, structure(rule)) }
                out
            },
            "fetch" to LuaFn.z {
                val out = LuaTable()
                list().forEachIndexed { index, rule -> out.set(index + 1, structure(rule)) }
                out
            },
            "cache" to cache,
            "count" to LuaFn.z { LuaValue.valueOf(list().size) },
            "has" to LuaFn.o {
                val id = it.checkjstring()
                LuaValue.valueOf(list().any { rule -> rule.id == id || rule.id.endsWith(":$id") })
            },
            "get" to LuaFn.o {
                val id = it.checkjstring()
                list().find { rule -> rule.id == id || rule.id.endsWith(":$id") }?.let { structure(it) } ?: LuaValue.NIL
            },
            "resolve" to LuaFn.o {
                val id = it.checkjstring()
                list().find { rule -> rule.id == id || rule.id.endsWith(":$id") }?.let { structure(it) } ?: LuaValue.NIL
            },
            "create" to LuaFn.o {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val rule = LunaLuaApi.luaToRule(pluginId, it.checktable())
                val existing = bridge.listPluginRules()
                if (existing.none { it.id == rule.id } && existing.size >= PluginLimits.MAX_RULES) {
                    throw LuaError("A plugin may own at most ${PluginLimits.MAX_RULES} rules.")
                }
                bridge.upsertPluginRule(rule)
                structure(rule)
            },
            "upsert" to LuaFn.o {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val rule = LunaLuaApi.luaToRule(pluginId, it.checktable())
                val existing = bridge.listPluginRules()
                if (existing.none { it.id == rule.id } && existing.size >= PluginLimits.MAX_RULES) {
                    throw LuaError("A plugin may own at most ${PluginLimits.MAX_RULES} rules.")
                }
                bridge.upsertPluginRule(rule)
                LuaValue.valueOf(rule.id)
            },
            "edit" to LuaFn.t { id, patch ->
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val current = list().find { rule -> rule.id == id.checkjstring() || rule.id.endsWith(":" + id.checkjstring()) }
                    ?: throw LuaError("Rule not found.")
                val next = LunaLuaApi.luaToRule(pluginId, patch.checktable()).copy(id = current.id)
                bridge.upsertPluginRule(next)
                structure(next)
            },
            "delete" to LuaFn.o {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val raw = it.checkjstring()
                val id = if (PluginRuleIds.owns(pluginId, raw)) raw else PluginRuleIds.prefix(pluginId) + raw
                if (!PluginRuleIds.owns(pluginId, id)) throw LuaError("Plugins may only delete their own rules.")
                bridge.deletePluginRule(id)
                LuaValue.TRUE
            },
            "clear" to LuaFn.z {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                bridge.listPluginRules().forEach { rule -> bridge.deletePluginRule(rule.id) }
                LuaValue.TRUE
            },
            "create_many" to LuaFn.o {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val input = it.checktable()
                val created = LuaTable()
                var i = 1
                var n = 0
                while (i <= PluginLimits.MAX_RULES) {
                    val row = input.get(i)
                    if (row.isnil()) break
                    val rule = LunaLuaApi.luaToRule(pluginId, row.checktable())
                    val existing = bridge.listPluginRules()
                    if (existing.none { it.id == rule.id } && existing.size >= PluginLimits.MAX_RULES) {
                        throw LuaError("A plugin may own at most ${PluginLimits.MAX_RULES} rules.")
                    }
                    bridge.upsertPluginRule(rule)
                    n += 1
                    created.set(n, structure(rule))
                    i += 1
                }
                created
            },
            "enable" to LuaFn.o {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val current = list().find { rule -> rule.id.endsWith(it.checkjstring()) } ?: throw LuaError("Rule not found.")
                bridge.upsertPluginRule(current.copy(enabled = true))
                LuaValue.TRUE
            },
            "disable" to LuaFn.o {
                requirePerm(bridge, PluginPermission.RULES_WRITE)
                val current = list().find { rule -> rule.id.endsWith(it.checkjstring()) } ?: throw LuaError("Rule not found.")
                bridge.upsertPluginRule(current.copy(enabled = false))
                LuaValue.TRUE
            },
        )
        table.set("createMany", table.get("create_many"))
        return table
    }

    private fun hostsTable(bridge: PluginNativeBridge): LuaTable {
        fun requireHosts() = requirePerm(bridge, PluginPermission.HOSTS_WRITE)
        fun structure(entry: HostEntry): LuaTable {
            val table = LuaTable()
            table.set("host", entry.host)
            table.set("hostname", entry.host)
            table.set("ip", entry.ipv4)
            table.set("ipv4", entry.ipv4)
            table.set("delete", LuaFn.z {
                requireHosts()
                bridge.setHosts(bridge.listHosts().filter { it.host != entry.host })
                LuaValue.TRUE
            })
            table.set("toJSON", LuaFn.z {
                LuaFn.fromJava(mapOf("host" to entry.host, "ip" to entry.ipv4))
            })
            return table
        }
        val table = LuaFn.module(
            "set_text" to LuaFn.o {
                requireHosts()
                val raw = it.checkjstring()
                if (raw.length > HostsFile.MAX_TEXT_CHARS) throw LuaError("Hosts file is too large.")
                val parsed = HostsFile.parse(raw)
                bridge.setHosts(parsed.entries)
                LuaFn.fromJava(mapOf("applied" to parsed.entries.size, "skipped" to parsed.errors.size, "errors" to parsed.errors.take(24)))
            },
            "setText" to LuaFn.o {
                requireHosts()
                val raw = it.checkjstring()
                if (raw.length > HostsFile.MAX_TEXT_CHARS) throw LuaError("Hosts file is too large.")
                val parsed = HostsFile.parse(raw)
                bridge.setHosts(parsed.entries)
                LuaFn.fromJava(mapOf("applied" to parsed.entries.size, "skipped" to parsed.errors.size))
            },
            "set" to LuaFn.o {
                requireHosts()
                val entries = LunaLuaApi.luaToHosts(it.checktable())
                bridge.setHosts(entries)
                LuaValue.valueOf(entries.size)
            },
            "list" to LuaFn.z {
                requireHosts()
                val out = LuaTable()
                bridge.listHosts().forEachIndexed { index, entry -> out.set(index + 1, structure(entry)) }
                out
            },
            "fetch" to LuaFn.z {
                requireHosts()
                val out = LuaTable()
                bridge.listHosts().forEachIndexed { index, entry -> out.set(index + 1, structure(entry)) }
                out
            },
            "cache" to LuaFn.module(
                "size" to LuaFn.z {
                    requireHosts()
                    LuaValue.valueOf(bridge.listHosts().size)
                },
                "get" to LuaFn.o {
                    requireHosts()
                    val host = it.checkjstring()
                    bridge.listHosts().find { entry -> entry.host.equals(host, ignoreCase = true) }?.let { structure(it) }
                        ?: LuaValue.NIL
                },
            ),
            "get" to LuaFn.o {
                requireHosts()
                val host = it.checkjstring()
                bridge.listHosts().find { entry -> entry.host.equals(host, ignoreCase = true) }?.let { structure(it) }
                    ?: LuaValue.NIL
            },
            "resolve" to LuaFn.o {
                requireHosts()
                val host = it.checkjstring()
                bridge.listHosts().find { entry -> entry.host.equals(host, ignoreCase = true) }?.ipv4?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            },
            "has" to LuaFn.o {
                requireHosts()
                val host = it.checkjstring()
                LuaValue.valueOf(bridge.listHosts().any { entry -> entry.host.equals(host, ignoreCase = true) })
            },
            "count" to LuaFn.z {
                requireHosts()
                LuaValue.valueOf(bridge.listHosts().size)
            },
            "add" to LuaFn.t { host, ip ->
                requireHosts()
                val row = LuaTable()
                row.set("host", host)
                row.set("ip", ip)
                val list = LuaTable()
                bridge.listHosts().forEachIndexed { index, entry ->
                    val item = LuaTable()
                    item.set("host", entry.host)
                    item.set("ip", entry.ipv4)
                    list.set(index + 1, item)
                }
                list.set(bridge.listHosts().size + 1, row)
                val entries = LunaLuaApi.luaToHosts(list)
                bridge.setHosts(entries)
                LuaValue.valueOf(entries.size)
            },
            "remove" to LuaFn.o {
                requireHosts()
                val host = it.checkjstring()
                bridge.setHosts(bridge.listHosts().filter { entry -> !entry.host.equals(host, ignoreCase = true) })
                LuaValue.TRUE
            },
            "clear" to LuaFn.z {
                requireHosts()
                bridge.clearHosts()
                LuaValue.TRUE
            },
            "parse" to LuaFn.o {
                requireHosts()
                val raw = it.checkjstring()
                if (raw.length > HostsFile.MAX_TEXT_CHARS) throw LuaError("Hosts file is too large.")
                val parsed = HostsFile.parse(raw)
                val entries = LuaTable()
                parsed.entries.forEachIndexed { index, entry -> entries.set(index + 1, structure(entry)) }
                LuaFn.fromJava(mapOf("entries" to entries, "errors" to parsed.errors.take(24)))
            },
            "to_text" to LuaFn.z {
                requireHosts()
                LuaValue.valueOf(bridge.listHosts().joinToString("\n") { "${it.ipv4} ${it.host}" })
            },
            "toText" to LuaFn.z {
                requireHosts()
                LuaValue.valueOf(bridge.listHosts().joinToString("\n") { "${it.ipv4} ${it.host}" })
            },
            "load_file" to LuaFn.o {
                requireHosts()
                val raw = bridge.readPackageFile(it.checkjstring()) ?: throw LuaError("Package file not found.")
                if (raw.length > HostsFile.MAX_TEXT_CHARS) throw LuaError("Hosts file is too large.")
                val parsed = HostsFile.parse(raw)
                bridge.setHosts(parsed.entries)
                LuaFn.fromJava(mapOf("applied" to parsed.entries.size, "skipped" to parsed.errors.size, "errors" to parsed.errors.take(24)))
            },
            "merge" to LuaFn.o {
                requireHosts()
                val parsed = if (it.isstring()) {
                    val raw = it.checkjstring()
                    if (raw.length > HostsFile.MAX_TEXT_CHARS) throw LuaError("Hosts file is too large.")
                    HostsFile.parse(raw).entries
                } else {
                    LunaLuaApi.luaToHosts(it.checktable())
                }
                val merged = LinkedHashMap<String, HostEntry>()
                bridge.listHosts().forEach { entry -> merged[entry.host] = entry }
                parsed.forEach { entry -> merged[entry.host] = entry }
                val next = merged.values.take(HostsFile.MAX_PER_PLUGIN)
                bridge.setHosts(next)
                LuaValue.valueOf(next.size)
            },
        )
        table.set("loadFile", table.get("load_file"))
        table.set("mergeText", table.get("merge"))
        return table
    }

    private fun jsonObjectToLua(json: JSONObject): LuaTable {
        val table = LuaTable()
        json.keys().asSequence().take(64).forEach { key ->
            table.set(key, LuaFn.fromJava(json.opt(key)))
        }
        return table
    }

    private fun fsTable(bridge: PluginNativeBridge): LuaTable {
        fun text(path: LuaValue): String {
            return bridge.readPackageFile(path.checkjstring()) ?: throw LuaError("Package file not found.")
        }
        val table = LuaFn.module(
            "read" to LuaFn.o {
                bridge.readPackageFile(it.checkjstring())?.let { value -> LuaValue.valueOf(value) } ?: LuaValue.NIL
            },
            "exists" to LuaFn.o { LuaValue.valueOf(bridge.packageFileExists(it.checkjstring())) },
            "list" to LuaFn.o {
                val dir = if (it.isnil()) "" else it.tojstring()
                LuaFn.fromJava(bridge.listPackageFiles(dir))
            },
            "lines" to LuaFn.o {
                val out = LuaTable()
                text(it).lineSequence().take(2_048).forEachIndexed { index, line ->
                    out.set(index + 1, line)
                }
                out
            },
            "json" to LuaFn.o { decodePackageJson(text(it)) },
        )
        table.set("load", table.get("read"))
        table.set("readText", table.get("read"))
        table.set("readJSON", table.get("json"))
        return table
    }

    private fun decodePackageJson(raw: String): LuaValue {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("{") -> jsonObjectToLua(JSONObject(trimmed))
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                val table = LuaTable()
                for (i in 0 until minOf(arr.length(), 256)) {
                    table.set(i + 1, LuaFn.fromJava(arr.opt(i)))
                }
                table
            }
            else -> throw LuaError("Package file is not JSON.")
        }
    }

    private fun interpolate(text: String, vars: LuaValue): String {
        if (!vars.istable()) return text.take(PluginLimits.MAX_I18N_CHARS)
        var out = text
        vars.checktable().keys().take(16).forEach { key ->
            val name = key.tojstring().trim().take(32)
            if (name.isEmpty()) return@forEach
            val value = vars.checktable().get(key).tojstring().take(80)
            out = out.replace("{$name}", value).replace("%{$name}", value)
        }
        return out.take(PluginLimits.MAX_I18N_CHARS)
    }
}

internal object LunaStdLibEncode {
    fun encode(value: LuaValue): String {
        return when {
            value.isnil() -> "null"
            value.isboolean() -> value.toboolean().toString()
            value.isnumber() -> value.todouble().toString()
            value.isstring() -> JSONObject.quote(value.tojstring())
            value.istable() -> {
                val obj = JSONObject()
                value.checktable().keys().forEach { key ->
                    obj.put(key.tojstring(), value.checktable().get(key).tojstring())
                }
                obj.toString()
            }
            else -> JSONObject.quote(value.tojstring())
        }
    }
}
