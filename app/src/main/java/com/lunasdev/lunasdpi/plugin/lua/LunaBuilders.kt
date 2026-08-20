package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.plugin.PluginPermission
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

internal object LunaBuilders {
    fun install(luna: LuaTable, bridge: PluginNativeBridge) {
        luna.set("Events", events())
        luna.set("PermissionFlagsBits", permissionFlags())
        luna.set("IntentsBitField", intentsBitField(bridge))
        luna.set("PageBuilder", pageBuilderType(luna))
        luna.set("EmbedBuilder", embedBuilderType(luna))
        luna.set("RuleBuilder", ruleBuilderType())
        luna.set("HostsBuilder", hostsBuilderType())
    }

    private fun events(): LuaTable = LuaFn.module(
        "Ready" to LuaValue.valueOf("ready"),
        "ClientReady" to LuaValue.valueOf("ready"),
        "VpnPhase" to LuaValue.valueOf("vpnPhase"),
        "READY" to LuaValue.valueOf("ready"),
        "VPN_PHASE" to LuaValue.valueOf("vpnPhase"),
        "SettingChanged" to LuaValue.valueOf("settingChanged"),
        "SETTING_CHANGED" to LuaValue.valueOf("settingChanged"),
        "VpnConnected" to LuaValue.valueOf("vpnConnected"),
        "VPN_CONNECTED" to LuaValue.valueOf("vpnConnected"),
        "VpnDisconnected" to LuaValue.valueOf("vpnDisconnected"),
        "VPN_DISCONNECTED" to LuaValue.valueOf("vpnDisconnected"),
        "Error" to LuaValue.valueOf("error"),
        "ERROR" to LuaValue.valueOf("error"),
    )

    private fun permissionFlags(): LuaTable {
        val table = LuaTable()
        PluginPermission.entries.forEach { perm ->
            table.set(perm.name, LuaValue.valueOf(perm.manifestKey()))
            table.set(perm.manifestKey(), LuaValue.valueOf(perm.manifestKey()))
        }
        table.set("Storage", LuaValue.valueOf("storage"))
        table.set("UiSettings", LuaValue.valueOf("ui.settings"))
        table.set("RulesRead", LuaValue.valueOf("rules.read"))
        table.set("RulesWrite", LuaValue.valueOf("rules.write"))
        table.set("VpnRead", LuaValue.valueOf("vpn.read"))
        table.set("VpnControl", LuaValue.valueOf("vpn.control"))
        table.set("Notify", LuaValue.valueOf("notify"))
        table.set("HostsWrite", LuaValue.valueOf("hosts.write"))
        table.set("AppRead", LuaValue.valueOf("app.read"))
        return table
    }

    private fun intentsBitField(bridge: PluginNativeBridge): LuaTable {
        val type = LuaTable()
        val flags = permissionFlags()
        type.set("Flags", flags)
        type.set(
            "from",
            LuaFn.o { bits -> bitfieldInstance(bits.toint()) },
        )
        type.set(
            "resolve",
            LuaFn.z {
                var bits = 0
                PluginPermission.entries.forEachIndexed { index, perm ->
                    if (bridge.granted(perm)) bits = bits or (1 shl index)
                }
                bitfieldInstance(bits)
            },
        )
        return type
    }

    private fun bitfieldInstance(bits: Int): LuaTable {
        val field = LuaTable()
        field.set("bitfield", LuaFn.z { LuaValue.valueOf(bits) })
        field.set(
            "has",
            LuaFn.o { raw ->
                val perm = PluginPermission.fromManifest(raw.tojstring()) ?: return@o LuaValue.FALSE
                LuaValue.valueOf(bits and (1 shl perm.ordinal) != 0)
            },
        )
        field.set(
            "missing",
            LuaFn.o { raw ->
                val perm = PluginPermission.fromManifest(raw.tojstring()) ?: return@o LuaValue.TRUE
                LuaValue.valueOf(bits and (1 shl perm.ordinal) == 0)
            },
        )
        field.set(
            "toArray",
            LuaFn.z {
                LuaFn.fromJava(
                    PluginPermission.entries.filter { perm -> bits and (1 shl perm.ordinal) != 0 }.map { it.manifestKey() },
                )
            },
        )
        field.set("equals", LuaFn.o { LuaValue.valueOf(it.toint() == bits) })
        return field
    }

    private fun pageBuilderType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { pageBuilder(luna) })
        return type
    }

    private fun pageBuilder(luna: LuaTable): LuaTable {
        val self = LuaTable()
        self.set("_title", "Settings")
        self.set("_description", "")
        self.set("_sections", LuaTable())
        self.set(
            "setTitle",
            LuaFn.t { thisSelf, title ->
                thisSelf.checktable().set("_title", title)
                thisSelf
            },
        )
        self.set(
            "setDescription",
            LuaFn.t { thisSelf, description ->
                thisSelf.checktable().set("_description", description)
                thisSelf
            },
        )
        self.set(
            "addSection",
            object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    val builder = args.arg(1).checktable()
                    val sections = builder.get("_sections").checktable()
                    val next = seqSize(sections) + 1
                    if (args.narg() >= 3 && args.arg(3).istable()) {
                        val section = LuaTable()
                        section.set("type", "section")
                        section.set("title", args.arg(2))
                        if (args.arg(3).istable() && !args.arg(3).get("type").isnil() && args.arg(3).get("type").tojstring() == "section") {
                            sections.set(next, args.arg(3))
                        } else {
                            section.set("items", args.arg(3))
                            sections.set(next, section)
                        }
                    } else {
                        sections.set(next, args.arg(2))
                    }
                    return builder
                }
            },
        )
        self.set(
            "addNote",
            LuaFn.t { thisSelf, text ->
                appendItem(thisSelf.checktable(), luna, "note", text)
                thisSelf
            },
        )
        self.set(
            "build",
            LuaFn.o { thisSelf ->
                val builder = thisSelf.checktable()
                val page = LuaTable()
                page.set("type", "page")
                page.set("title", builder.get("_title"))
                page.set("description", builder.get("_description"))
                page.set("sections", builder.get("_sections"))
                page
            },
        )
        self.set("toJSON", self.get("build"))
        return self
    }

    private fun embedBuilderType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { embedBuilder(luna) })
        return type
    }

    private fun embedBuilder(luna: LuaTable): LuaTable {
        val self = LuaTable()
        val items = LuaTable()
        self.set("_title", "Settings")
        self.set("_description", "")
        self.set("_section", "")
        self.set("_items", items)
        fun push(item: LuaValue): LuaTable {
            items.set(seqSize(items) + 1, item)
            return self
        }
        self.set("setTitle", LuaFn.t { thisSelf, title -> thisSelf.checktable().set("_title", title); thisSelf })
        self.set("setDescription", LuaFn.t { thisSelf, text -> thisSelf.checktable().set("_description", text); thisSelf })
        self.set("setSection", LuaFn.t { thisSelf, title -> thisSelf.checktable().set("_section", title); thisSelf })
        self.set(
            "addHeading",
            LuaFn.m1(self) { text ->
                push(LuaFn.invoke(luna.get("ui"), "heading", tableOf("text" to text.tojstring())))
            },
        )
        self.set(
            "addAlert",
            LuaFn.m2(self) { text, tone ->
                push(
                    LuaFn.invoke(
                        luna.get("ui"),
                        "alert",
                        tableOf("text" to text.tojstring(), "tone" to tone.optjstring("info")),
                    ),
                )
            },
        )
        self.set(
            "addNote",
            LuaFn.m1(self) { text ->
                push(LuaFn.invoke(luna.get("ui"), "note", tableOf("text" to text.tojstring())))
            },
        )
        self.set(
            "addCode",
            LuaFn.m1(self) { text ->
                push(LuaFn.invoke(luna.get("ui"), "code", tableOf("text" to text.tojstring())))
            },
        )
        self.set(
            "addField",
            LuaFn.m2(self) { label, value ->
                push(
                    LuaFn.invoke(
                        luna.get("ui"),
                        "kv",
                        tableOf("label" to label.tojstring(), "value" to value.tojstring()),
                    ),
                )
            },
        )
        self.set(
            "addDivider",
            LuaFn.z { push(LuaFn.invoke(luna.get("ui"), "divider", LuaTable())) },
        )
        self.set(
            "addBadge",
            LuaFn.m1(self) { text ->
                push(LuaFn.invoke(luna.get("ui"), "badge", tableOf("text" to text.tojstring())))
            },
        )
        self.set(
            "addProgress",
            LuaFn.m2(self) { title, value ->
                val row = LuaTable()
                row.set("title", title)
                row.set("value", value)
                push(LuaFn.invoke(luna.get("ui"), "progress", row))
            },
        )
        self.set(
            "addToggle",
            LuaFn.m3(self) { id, title, value ->
                val row = LuaTable()
                row.set("id", id)
                row.set("title", title)
                row.set("value", value)
                push(LuaFn.invoke(luna.get("ui"), "switch", row))
            },
        )
        self.set(
            "addInput",
            LuaFn.m3(self) { id, title, value ->
                val row = LuaTable()
                row.set("id", id)
                row.set("title", title)
                row.set("value", value)
                push(LuaFn.invoke(luna.get("ui"), "text", row))
            },
        )
        self.set(
            "addTextarea",
            LuaFn.m3(self) { id, title, value ->
                val row = LuaTable()
                row.set("id", id)
                row.set("title", title)
                row.set("value", value)
                push(LuaFn.invoke(luna.get("ui"), "textarea", row))
            },
        )
        self.set(
            "addButton",
            LuaFn.m2(self) { id, title ->
                val row = LuaTable()
                row.set("id", id)
                row.set("title", title)
                push(LuaFn.invoke(luna.get("ui"), "button", row))
            },
        )
        self.set(
            "addDangerButton",
            LuaFn.m2(self) { id, title ->
                val row = LuaTable()
                row.set("id", id)
                row.set("title", title)
                row.set("destructive", LuaValue.TRUE)
                push(LuaFn.invoke(luna.get("ui"), "button", row))
            },
        )
        self.set(
            "build",
            LuaFn.o { thisSelf ->
                val builder = thisSelf.checktable()
                val page = LuaTable()
                page.set("type", "page")
                page.set("title", builder.get("_title"))
                page.set("description", builder.get("_description"))
                val sections = LuaTable()
                val section = LuaTable()
                section.set("type", "section")
                section.set("title", builder.get("_section"))
                section.set("items", builder.get("_items"))
                sections.set(1, section)
                page.set("sections", sections)
                page
            },
        )
        self.set("toJSON", self.get("build"))
        return self
    }

    private fun ruleBuilderType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { ruleBuilder() })
        return type
    }

    private fun ruleBuilder(): LuaTable {
        val self = LuaTable()
        self.set("id", "")
        self.set("name", "Plugin rule")
        self.set("enabled", LuaValue.TRUE)
        self.set("strategy", "automatic")
        self.set("domains", LuaTable())
        self.set("setId", LuaFn.t { thisSelf, id -> thisSelf.checktable().set("id", id); thisSelf })
        self.set("setName", LuaFn.t { thisSelf, name -> thisSelf.checktable().set("name", name); thisSelf })
        self.set("setEnabled", LuaFn.t { thisSelf, enabled -> thisSelf.checktable().set("enabled", enabled); thisSelf })
        self.set("setStrategy", LuaFn.t { thisSelf, strategy -> thisSelf.checktable().set("strategy", strategy); thisSelf })
        self.set(
            "addDomain",
            LuaFn.t { thisSelf, domain ->
                val domains = thisSelf.checktable().get("domains").checktable()
                domains.set(seqSize(domains) + 1, domain)
                thisSelf
            },
        )
        self.set(
            "setDomains",
            LuaFn.t { thisSelf, domains ->
                thisSelf.checktable().set("domains", domains)
                thisSelf
            },
        )
        self.set(
            "build",
            LuaFn.o { thisSelf ->
                val src = thisSelf.checktable()
                val out = LuaTable()
                out.set("id", src.get("id"))
                out.set("name", src.get("name"))
                out.set("enabled", src.get("enabled"))
                out.set("strategy", src.get("strategy"))
                out.set("domains", src.get("domains"))
                out
            },
        )
        self.set("toJSON", self.get("build"))
        return self
    }

    private fun hostsBuilderType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { hostsBuilder() })
        return type
    }

    private fun hostsBuilder(): LuaTable {
        val self = LuaTable()
        val rows = LuaTable()
        self.set("_rows", rows)
        self.set(
            "add",
            LuaFn.r { thisSelf, host, ip ->
                val list = thisSelf.checktable().get("_rows").checktable()
                val row = LuaTable()
                row.set("host", host)
                row.set("ip", ip)
                list.set(seqSize(list) + 1, row)
                thisSelf
            },
        )
        self.set(
            "addLine",
            LuaFn.t { thisSelf, line ->
                val parts = line.tojstring().trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val list = thisSelf.checktable().get("_rows").checktable()
                    val row = LuaTable()
                    row.set("ip", parts[0])
                    row.set("host", parts[1])
                    list.set(seqSize(list) + 1, row)
                }
                thisSelf
            },
        )
        self.set(
            "build",
            LuaFn.o { thisSelf -> thisSelf.checktable().get("_rows") },
        )
        self.set(
            "toText",
            LuaFn.o { thisSelf ->
                val list = thisSelf.checktable().get("_rows").checktable()
                val lines = ArrayList<String>()
                var i = 1
                while (i <= 256) {
                    val row = list.get(i)
                    if (row.isnil()) break
                    lines.add("${row.get("ip").tojstring()} ${row.get("host").tojstring()}")
                    i++
                }
                LuaValue.valueOf(lines.joinToString("\n"))
            },
        )
        self.set("toJSON", self.get("build"))
        return self
    }

    private fun appendItem(builder: LuaTable, luna: LuaTable, type: String, text: LuaValue) {
        val sections = builder.get("_sections").checktable()
        if (seqSize(sections) == 0) {
            val section = LuaTable()
            section.set("type", "section")
            section.set("title", "")
            section.set("items", LuaTable())
            sections.set(1, section)
        }
        val section = sections.get(seqSize(sections)).checktable()
        val items = section.get("items").checktable()
        items.set(seqSize(items) + 1, LuaFn.invoke(luna.get("ui"), type, tableOf("text" to text.tojstring())))
    }

    private fun seqSize(table: LuaTable): Int {
        var i = 1
        while (i <= 256) {
            if (table.get(i).isnil()) return i - 1
            i++
        }
        return 256
    }

    private fun tableOf(vararg pairs: Pair<String, String>): LuaTable {
        val table = LuaTable()
        pairs.forEach { (key, value) -> table.set(key, value) }
        return table
    }
}
