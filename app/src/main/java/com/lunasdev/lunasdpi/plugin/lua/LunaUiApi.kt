package com.lunasdev.lunasdpi.plugin.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

internal object LunaUiApi {
    fun table(): LuaTable {
        val ui = LuaTable()
        listOf(
            "page",
            "note",
            "heading",
            "divider",
            "spacer",
            "badge",
            "code",
            "alert",
            "kv",
            "progress",
            "link",
            "switch",
            "checkbox",
            "text",
            "textarea",
            "number",
            "select",
            "radio",
            "slider",
            "button",
            "danger_button",
        ).forEach { type ->
            ui.set(type, typed(type))
        }
        ui.set("section", LuaFn.t { title, items ->
            val table = LuaTable()
            table.set("type", "section")
            table.set("title", title)
            table.set("items", items)
            table
        })
        ui.set("section_ex", LuaFn.r { title, description, items ->
            val table = LuaTable()
            table.set("type", "section")
            table.set("title", title)
            table.set("description", description)
            table.set("items", items)
            table
        })
        ui.set("markdown", typed("note"))
        ui.set("paragraph", typed("note"))
        ui.set("label", typed("note"))
        ui.set("hint", typed("note"))
        ui.set("title", typed("heading"))
        ui.set("subtitle", typed("heading"))
        ui.set("separator", typed("divider"))
        ui.set("hr", typed("divider"))
        ui.set("chip", typed("badge"))
        ui.set("tag", typed("badge"))
        ui.set("pre", typed("code"))
        ui.set("monospace", typed("code"))
        ui.set("callout", typed("alert"))
        ui.set("banner", typed("alert"))
        ui.set("row", typed("kv"))
        ui.set("field", typed("kv"))
        ui.set("meter", typed("progress"))
        ui.set("bar", typed("progress"))
        ui.set("anchor", typed("link"))
        ui.set("toggle", typed("switch"))
        ui.set("check", typed("checkbox"))
        ui.set("input", typed("text"))
        ui.set("textbox", typed("text"))
        ui.set("hosts", typed("textarea"))
        ui.set("integer", typed("number"))
        ui.set("choice", typed("select"))
        ui.set("chips", typed("select"))
        ui.set("range", typed("slider"))
        ui.set("action", typed("button"))
        ui.set("primary", typed("button"))
        ui.set("submit", typed("button"))
        ui.set("destroy", typed("danger_button"))
        ui.set("embed", LuaFn.o { arg ->
            val src = arg.checktable()
            val page = LuaTable()
            page.set("type", "page")
            page.set("title", src.get("title"))
            page.set("description", src.get("description"))
            val sections = LuaTable()
            val section = LuaTable()
            section.set("type", "section")
            section.set("title", src.get("section").optjstring(""))
            section.set("items", src.get("fields"))
            sections.set(1, section)
            page.set("sections", sections)
            page
        })
        ui.set("stat", typed("kv"))
        ui.set("example", typed("code"))
        ui.set("group", ui.get("section"))
        ui.set("stack", ui.get("section"))
        return ui
    }

    private fun typed(type: String) = LuaFn.o { arg ->
        val table = if (arg.istable()) arg.checktable() else LuaTable().also { it.set("text", arg) }
        table.set("type", if (type == "textarea") "text" else if (type == "radio") "select" else if (type == "danger_button") "button" else type)
        if (type == "textarea") table.set("multiline", LuaValue.TRUE)
        if (type == "danger_button") table.set("destructive", LuaValue.TRUE)
        table
    }
}
