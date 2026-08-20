package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.plugin.PluginLimits
import com.lunasdev.lunasdpi.plugin.PluginSecurity
import com.lunasdev.lunasdpi.plugin.PluginUiItem
import com.lunasdev.lunasdpi.plugin.PluginUiPage
import com.lunasdev.lunasdpi.plugin.PluginUiSection
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

internal object PluginUiParser {
    fun parse(value: LuaValue): PluginUiPage {
        val table = value.checktable()
        val title = table.get("title").optjstring("Settings").trim().take(40).ifBlank { "Settings" }
        val description = table.get("description").optjstring("").trim().take(240)
        val sectionsVal = table.get("sections")
        val sections = ArrayList<PluginUiSection>()
        if (sectionsVal.istable()) {
            var i = 1
            while (i <= PluginLimits.MAX_UI_SECTIONS) {
                val item = sectionsVal.get(i)
                if (item.isnil()) break
                sections.add(parseSection(item))
                i += 1
            }
        }
        if (sections.isEmpty()) {
            throw LuaError("settings_page() must return at least one section.")
        }
        return PluginUiPage(title = title, description = description, sections = sections)
    }

    private fun parseSection(value: LuaValue): PluginUiSection {
        val table = value.checktable()
        val title = table.get("title").optjstring("").trim().take(40)
        val description = table.get("description").optjstring("").trim().take(160)
        val itemsVal = table.get("items")
        val items = ArrayList<PluginUiItem>()
        if (itemsVal.istable()) {
            var i = 1
            while (i <= PluginLimits.MAX_UI_ITEMS) {
                val item = itemsVal.get(i)
                if (item.isnil()) break
                items.add(parseItem(item))
                i += 1
            }
        }
        return PluginUiSection(title = title.ifBlank { " " }, description = description, items = items)
    }

    private fun parseItem(value: LuaValue): PluginUiItem {
        val table = value.checktable()
        val type = table.get("type").optjstring("note").trim().lowercase()
        return when (type) {
            "note", "markdown", "paragraph", "label", "hint" ->
                PluginUiItem.Note(text = table.get("text").optjstring("").trim().take(600))
            "heading", "title", "subtitle" -> PluginUiItem.Heading(
                text = table.get("text").optjstring(table.get("title").optjstring("")).trim().take(80),
                level = table.get("level").optint(if (type == "subtitle") 2 else 1).coerceIn(1, 3),
            )
            "divider", "separator", "hr" -> PluginUiItem.Divider()
            "spacer" -> PluginUiItem.Spacer()
            "badge", "chip", "tag" -> PluginUiItem.Badge(
                text = table.get("text").optjstring("").trim().take(24),
                tone = table.get("tone").optjstring("accent").trim().take(16),
            )
            "code", "pre", "monospace" -> PluginUiItem.Code(text = table.get("text").optjstring("").take(2_000))
            "alert", "callout", "banner" -> PluginUiItem.Alert(
                text = table.get("text").optjstring("").trim().take(400),
                tone = table.get("tone").optjstring(table.get("variant").optjstring("info")).trim().take(16),
            )
            "kv", "row", "field" -> PluginUiItem.KeyValue(
                label = table.get("label").optjstring(table.get("title").optjstring("")).trim().take(40),
                value = table.get("value").optjstring("").trim().take(80),
            )
            "progress", "meter", "bar" -> {
                var amount = table.get("value").optdouble(0.0).toFloat()
                if (amount > 1f) amount /= 100f
                PluginUiItem.Progress(
                    title = table.get("title").optjstring("").trim().take(40),
                    value = amount.coerceIn(0f, 1f),
                )
            }
            "link", "anchor" -> {
                val url = table.get("url").optjstring("").trim()
                if (PluginSecurity.validateHomepage(url) != null) {
                    throw LuaError("Links must be https://github.com/…")
                }
                PluginUiItem.Link(
                    text = table.get("text").optjstring(url).trim().take(80),
                    url = url,
                )
            }
            "switch", "toggle" -> PluginUiItem.Switch(
                id = fieldId(table),
                title = table.get("title").optjstring("Switch").trim().take(40),
                body = table.get("body").optjstring("").trim().take(160),
                value = table.get("value").optboolean(false),
                enabled = enabled(table),
            )
            "checkbox", "check" -> PluginUiItem.Checkbox(
                id = fieldId(table),
                title = table.get("title").optjstring("Checkbox").trim().take(40),
                body = table.get("body").optjstring("").trim().take(160),
                value = table.get("value").optboolean(false),
                enabled = enabled(table),
            )
            "text", "textfield", "input", "textbox", "textarea", "hosts" -> PluginUiItem.TextField(
                id = fieldId(table),
                title = table.get("title").optjstring("Text").trim().take(40),
                value = table.get("value").optjstring("").take(32_768),
                placeholder = table.get("placeholder").optjstring("").take(120),
                multiline = type == "textarea" || type == "hosts" || table.get("multiline").optboolean(false),
                enabled = enabled(table),
            )
            "number", "integer" -> PluginUiItem.NumberField(
                id = fieldId(table),
                title = table.get("title").optjstring("Number").trim().take(40),
                value = table.get("value").optdouble(0.0).toFloat(),
                min = table.get("min").optdouble(Float.NEGATIVE_INFINITY.toDouble()).toFloat(),
                max = table.get("max").optdouble(Float.POSITIVE_INFINITY.toDouble()).toFloat(),
                enabled = enabled(table),
            )
            "select", "radio", "choice" -> {
                val options = stringList(table.get("options")).take(12)
                val selected = table.get("value").optjstring(options.firstOrNull().orEmpty())
                PluginUiItem.Select(
                    id = fieldId(table),
                    title = table.get("title").optjstring("Select").trim().take(40),
                    options = options,
                    value = if (selected in options) selected else options.firstOrNull().orEmpty(),
                    enabled = enabled(table),
                )
            }
            "slider", "range" -> {
                val min = table.get("min").optdouble(0.0).toFloat()
                val max = table.get("max").optdouble(100.0).toFloat().coerceAtLeast(min + 0.01f)
                val raw = table.get("value").optdouble(min.toDouble()).toFloat()
                PluginUiItem.Slider(
                    id = fieldId(table),
                    title = table.get("title").optjstring("Slider").trim().take(40),
                    value = raw.coerceIn(min, max),
                    min = min,
                    max = max,
                    enabled = enabled(table),
                )
            }
            "button", "action", "primary", "submit", "danger_button", "destroy" -> PluginUiItem.Button(
                id = fieldId(table),
                title = table.get("title").optjstring("OK").trim().take(40),
                destructive = type == "danger_button" || type == "destroy" || table.get("destructive").optboolean(false),
                enabled = enabled(table),
            )
            "stat", "metric", "tile" -> PluginUiItem.Stat(
                label = table.get("label").optjstring(table.get("title").optjstring("")).trim().take(40),
                value = table.get("value").optjstring("").trim().take(24),
                hint = table.get("hint").optjstring(table.get("body").optjstring("")).trim().take(80),
                tone = table.get("tone").optjstring("accent").trim().take(16),
            )
            "list_item", "listitem", "row_item", "cell" -> PluginUiItem.ListItem(
                title = table.get("title").optjstring(table.get("text").optjstring("")).trim().take(80),
                body = table.get("body").optjstring(table.get("subtitle").optjstring("")).trim().take(160),
                trailing = table.get("trailing").optjstring(table.get("value").optjstring("")).trim().take(24),
                tone = table.get("tone").optjstring("accent").trim().take(16),
            )
            "empty", "placeholder", "blank" -> PluginUiItem.Empty(
                text = table.get("text").optjstring(table.get("title").optjstring("Nothing here")).trim().take(120),
                hint = table.get("hint").optjstring(table.get("body").optjstring("")).trim().take(160),
            )
            "chips", "tags", "pills" -> PluginUiItem.Chips(
                labels = stringList(table.get("labels").istable().let { ok ->
                    if (ok) table.get("labels") else table.get("items")
                }).take(12),
            )
            "quote", "blockquote", "cite" -> PluginUiItem.Quote(
                text = table.get("text").optjstring("").trim().take(400),
                cite = table.get("cite").optjstring(table.get("caption").optjstring("")).trim().take(80),
            )
            "fold", "details", "accordion", "collapse" -> PluginUiItem.Fold(
                title = table.get("title").optjstring(
                    if (table.get("body").isnil()) "Details" else table.get("text").optjstring("Details"),
                ).trim().take(80),
                body = table.get("body").optjstring(
                    if (table.get("title").isnil()) table.get("text").optjstring("") else "",
                ).trim().take(600),
                open = table.get("open").optboolean(true),
            )
            "steps", "stepper", "wizard_bar" -> PluginUiItem.Steps(
                labels = stringList(
                    if (table.get("labels").istable()) table.get("labels") else table.get("items"),
                ).take(12),
                current = table.get("current").optint(1).coerceIn(1, 12),
            )
            "timeline", "events", "log_list" -> PluginUiItem.Timeline(
                events = stringList(
                    if (table.get("events").istable()) table.get("events") else table.get("items"),
                ).take(16),
            )
            "score", "rating", "gauge" -> {
                val max = table.get("max").optdouble(1.0).toFloat().let { if (it <= 0f) 1f else it }
                var amount = table.get("value").optdouble(0.0).toFloat()
                if (max == 1f && amount > 1f) amount /= 100f
                PluginUiItem.Score(
                    label = table.get("label").optjstring(table.get("title").optjstring("")).trim().take(40),
                    value = amount.coerceIn(0f, max),
                    max = max,
                )
            }
            "compare", "vs", "diff_row" -> PluginUiItem.Compare(
                leftLabel = table.get("left_label").optjstring(table.get("a_label").optjstring("A")).trim().take(24),
                left = table.get("left").optjstring(table.get("a").optjstring("")).trim().take(40),
                rightLabel = table.get("right_label").optjstring(table.get("b_label").optjstring("B")).trim().take(24),
                right = table.get("right").optjstring(table.get("b").optjstring("")).trim().take(40),
            )
            "faq", "qa", "help" -> PluginUiItem.Faq(
                question = table.get("q").optjstring(table.get("question").optjstring(table.get("title").optjstring(""))).trim().take(120),
                answer = table.get("a").optjstring(table.get("answer").optjstring(table.get("body").optjstring(""))).trim().take(400),
            )
            "status", "pill", "state_row" -> PluginUiItem.Status(
                text = table.get("text").optjstring(table.get("title").optjstring("")).trim().take(40),
                tone = table.get("tone").optjstring("accent").trim().take(16),
                detail = table.get("detail").optjstring(table.get("body").optjstring("")).trim().take(80),
            )
            else -> throw LuaError("Unknown settings control: $type")
        }
    }

    private fun fieldId(table: org.luaj.vm2.LuaTable): String {
        val id = table.get("id").optjstring("").trim()
        if (id.isEmpty() || id.length > 32) {
            throw LuaError("Settings control id must be 1–32 characters.")
        }
        if (!id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            throw LuaError("Settings control id is invalid.")
        }
        return id
    }

    private fun enabled(table: org.luaj.vm2.LuaTable): Boolean {
        if (table.get("disabled").optboolean(false)) return false
        return table.get("enabled").optboolean(true)
    }

    private fun stringList(value: LuaValue): List<String> {
        if (!value.istable()) return emptyList()
        val out = ArrayList<String>()
        var i = 1
        while (i <= 12) {
            val item = value.get(i)
            if (item.isnil()) break
            val text = item.tojstring().trim().take(40)
            if (text.isNotEmpty()) out.add(text)
            i += 1
        }
        return out
    }
}
