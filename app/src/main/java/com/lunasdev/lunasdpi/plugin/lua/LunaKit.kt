package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.plugin.PluginLimits
import com.lunasdev.lunasdpi.plugin.PluginPermission
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.max

internal object LunaKit {
    private const val MAX = 256
    private const val MAX_LISTENERS = 8

    val typeNames = listOf(
        "Machine", "Pipeline", "History", "Cache", "Trie", "Router", "Actions", "Config",
        "Expr", "Paginator", "SearchIndex", "Channel", "Bus", "RateLimit", "Once", "Retry",
        "Checklist", "Tags", "Counter", "PriorityQueue", "Tree", "JsonPath", "TableQuery",
        "Markdown", "Id", "Theme", "Dashboard", "SchemaForm", "Policy", "Deep", "Debounce",
        "Throttle", "Catalog", "Graph", "Quota", "Fingerprint", "Patch", "Session", "Binder",
        "Matchbook", "Window",
    )

    fun install(luna: LuaTable, bridge: PluginNativeBridge) {
        luna.set("Machine", machineType())
        luna.set("Pipeline", pipelineType())
        luna.set("History", historyType())
        luna.set("Cache", cacheType())
        luna.set("Trie", trieType())
        luna.set("Router", routerType())
        luna.set("Actions", actionsType())
        luna.set("Config", configType(bridge))
        luna.set("Expr", exprType())
        luna.set("Paginator", paginatorType())
        luna.set("SearchIndex", searchType())
        luna.set("Channel", channelType())
        luna.set("Bus", busType())
        luna.set("RateLimit", rateType())
        luna.set("Once", onceType())
        luna.set("Retry", retryType())
        luna.set("Checklist", checklistType(luna))
        luna.set("Tags", tagsType())
        luna.set("Counter", counterType())
        luna.set("PriorityQueue", pqType())
        luna.set("Tree", treeType())
        luna.set("JsonPath", jsonPathType())
        luna.set("TableQuery", queryType())
        luna.set("Markdown", markdownType())
        luna.set("Id", idType())
        luna.set("Theme", themeType())
        luna.set("Dashboard", dashboardType(luna))
        luna.set("SchemaForm", schemaFormType(luna))
        luna.set("Policy", policyType())
        luna.set("Deep", deepType())
        luna.set("Debounce", debounceType(bridge))
        luna.set("Throttle", throttleType())
        luna.set("Catalog", catalogType())
        luna.set("Graph", graphType())
        luna.set("Quota", quotaType())
        luna.set("Fingerprint", fingerprintType())
        luna.set("Patch", patchType())
        luna.set("Session", sessionType())
        luna.set("Binder", binderType())
        luna.set("Matchbook", matchbookType())
        luna.set("Window", windowType())
        val kit = LuaTable()
        typeNames.forEach { name -> kit.set(name, luna.get(name)) }
        luna.set("kit", kit)
        val systems = luna.get("systems")
        if (systems.istable()) {
            val table = systems.checktable()
            table.set("kit", LuaValue.TRUE)
            table.set("fsm", LuaValue.TRUE)
            table.set("query", LuaValue.TRUE)
            table.set("dashboard", LuaValue.TRUE)
            table.set("expr", LuaValue.TRUE)
            table.set("router", LuaValue.TRUE)
        }
    }

    fun nestOnto(client: LuaTable, luna: LuaTable) {
        typeNames.forEach { name -> client.set(name, luna.get(name)) }
        client.set("kit", luna.get("kit"))
    }

    private fun machineType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { initial ->
            var state = initial.optjstring("idle").take(40)
            val edges = ArrayList<Triple<String, String, String>>()
            val listeners = mutableListOf<LuaValue>()
            val hist = ArrayDeque<String>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Machine"))
            t.set("on", LuaFn.m3(t) { from, event, to ->
                if (edges.size < MAX) {
                    edges.add(Triple(from.tojstring().take(40), event.tojstring().take(40), to.tojstring().take(40)))
                }
                t
            })
            t.set("send", LuaFn.m1(t) { event ->
                val name = event.tojstring()
                val hit = edges.firstOrNull { (it.first == state || it.first == "*") && it.second == name }
                if (hit != null) {
                    val prev = state
                    state = hit.third
                    if (hist.size >= 16) hist.removeFirst()
                    hist.addLast(prev)
                    listeners.forEach { runCatching { it.call(LuaValue.valueOf(state), LuaValue.valueOf(prev)) } }
                    LuaValue.TRUE
                } else {
                    LuaValue.FALSE
                }
            })
            t.set("state", LuaFn.z { LuaValue.valueOf(state) })
            t.set("can", LuaFn.m1(t) { event ->
                LuaValue.valueOf(edges.any { (it.first == state || it.first == "*") && it.second == event.tojstring() })
            })
            t.set("events", LuaFn.z {
                LuaFn.fromJava(edges.filter { it.first == state || it.first == "*" }.map { it.second }.distinct())
            })
            t.set("history", LuaFn.z { LuaFn.fromJava(hist.toList()) })
            t.set("subscribe", LuaFn.m1(t) { fn ->
                if (fn.isfunction() && listeners.size < MAX_LISTENERS) listeners.add(fn)
                t
            })
            t.set("reset", LuaFn.m1(t) { next ->
                state = next.optjstring("idle").take(40)
                hist.clear()
                t
            })
            t
        })
        return type
    }

    private fun pipelineType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val steps = mutableListOf<LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Pipeline"))
            t.set("use", LuaFn.m1(t) { fn ->
                if (fn.isfunction() && steps.size < 32) steps.add(fn)
                t
            })
            t.set("tap", LuaFn.m1(t) { fn ->
                if (fn.isfunction() && steps.size < 32) {
                    steps.add(
                        LuaFn.o { acc ->
                            runCatching { fn.call(acc) }
                            acc
                        },
                    )
                }
                t
            })
            t.set("run", LuaFn.m1(t) { input ->
                var acc = input
                steps.forEach { acc = runCatching { it.call(acc) }.getOrDefault(acc) }
                acc
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(steps.size) })
            t.set("clear", LuaFn.z {
                steps.clear()
                t
            })
            t
        })
        return type
    }

    private fun historyType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { cap ->
            val capacity = cap.optint(32).coerceIn(1, MAX)
            val past = ArrayDeque<LuaValue>()
            val future = ArrayDeque<LuaValue>()
            var current: LuaValue = LuaValue.NIL
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("History"))
            t.set("push", LuaFn.m1(t) { value ->
                if (!current.isnil()) {
                    if (past.size >= capacity) past.removeFirst()
                    past.addLast(current)
                }
                current = value
                future.clear()
                t
            })
            t.set("undo", LuaFn.z {
                if (past.isEmpty()) current else {
                    future.addFirst(current)
                    current = past.removeLast()
                    current
                }
            })
            t.set("redo", LuaFn.z {
                if (future.isEmpty()) current else {
                    past.addLast(current)
                    current = future.removeFirst()
                    current
                }
            })
            t.set("current", LuaFn.z { current })
            t.set("can_undo", LuaFn.z { LuaValue.valueOf(past.isNotEmpty()) })
            t.set("can_redo", LuaFn.z { LuaValue.valueOf(future.isNotEmpty()) })
            t.set("peek", LuaFn.z { current })
            t.set("size", LuaFn.z { LuaValue.valueOf(past.size) })
            t.set("clear", LuaFn.z {
                past.clear()
                future.clear()
                current = LuaValue.NIL
                t
            })
            t
        })
        return type
    }

    private fun cacheType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val data = LinkedHashMap<String, Pair<LuaValue, Long>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Cache"))
            fun alive(key: String): LuaValue? {
                val row = data[key] ?: return null
                if (row.second > 0 && row.second < System.currentTimeMillis()) {
                    data.remove(key)
                    return null
                }
                return row.first
            }
            t.set("get", LuaFn.m1(t) { alive(it.tojstring()) ?: LuaValue.NIL })
            t.set("set", LuaFn.m3(t) { key, value, ttl ->
                val ms = ttl.optdouble(0.0).toLong().coerceIn(0, PluginLimits.MAX_TIMER_MS * 10)
                val exp = if (ms <= 0) 0L else System.currentTimeMillis() + ms
                if (data.size < 64 || data.containsKey(key.tojstring())) {
                    data[key.tojstring().take(80)] = value to exp
                }
                t
            })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(alive(it.tojstring()) != null) })
            t.set("delete", LuaFn.m1(t) { LuaValue.valueOf(data.remove(it.tojstring()) != null) })
            t.set("get_or", LuaFn.m2(t) { key, fn ->
                val hit = alive(key.tojstring())
                if (hit != null) return@m2 hit
                val value = if (fn.isfunction()) runCatching { fn.call() }.getOrDefault(LuaValue.NIL) else fn
                val k = key.tojstring().take(80)
                if (data.size < 64 || data.containsKey(k)) data[k] = value to 0L
                value
            })
            t.set("keys", LuaFn.z { LuaFn.fromJava(data.keys.toList()) })
            t.set("clear", LuaFn.z {
                data.clear()
                t
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(data.size) })
            t
        })
        return type
    }

    private fun trieType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val words = LinkedHashSet<String>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Trie"))
            t.set("insert", LuaFn.m1(t) {
                if (words.size < MAX) words.add(it.tojstring().lowercase(Locale.US).take(80))
                t
            })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(words.contains(it.tojstring().lowercase(Locale.US))) })
            t.set("prefix", LuaFn.m1(t) { needle ->
                val q = needle.tojstring().lowercase(Locale.US)
                LuaFn.fromJava(words.filter { it.startsWith(q) }.take(32))
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(words.size) })
            t
        })
        return type
    }

    private fun routerType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val routes = mutableListOf<Pair<String, LuaValue>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Router"))
            t.set("add", LuaFn.m2(t) { path, fn ->
                if (routes.size < 64) routes.add(path.tojstring().take(80) to fn)
                t
            })
            t.set("match", LuaFn.m1(t) { raw ->
                val path = raw.tojstring().trim()
                routes.forEach { (pattern, fn) ->
                    val params = matchPath(pattern, path) ?: return@forEach
                    val payload = LuaFn.fromJava(params)
                    return@m1 if (fn.isfunction()) runCatching { fn.call(payload) }.getOrDefault(payload) else payload
                }
                LuaValue.NIL
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(routes.size) })
            t
        })
        return type
    }

    private fun actionsType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val cmds = LinkedHashMap<String, LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Actions"))
            t.set("add", LuaFn.m2(t) { name, fn ->
                if (cmds.size < 64) cmds[name.tojstring().take(40)] = fn
                t
            })
            t.set("run", LuaFn.m2(t) { name, arg ->
                val fn = cmds[name.tojstring()] ?: return@m2 LuaValue.NIL
                runCatching { if (arg.isnil()) fn.call() else fn.call(arg) }.getOrDefault(LuaValue.NIL)
            })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(cmds.containsKey(it.tojstring())) })
            t.set("keys", LuaFn.z { LuaFn.fromJava(cmds.keys.toList()) })
            t.set("remove", LuaFn.m1(t) { LuaValue.valueOf(cmds.remove(it.tojstring()) != null) })
            t
        })
        return type
    }

    private fun configType(bridge: PluginNativeBridge): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { defaults ->
            val data = LinkedHashMap<String, LuaValue>()
            tableToValueMap(defaults).forEach { (k, v) -> data[k] = v }
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Config"))
            t.set("get", LuaFn.m1(t) { data[it.tojstring()] ?: LuaValue.NIL })
            t.set("get_or", LuaFn.m2(t) { key, fallback -> data[key.tojstring()] ?: fallback })
            t.set("set", LuaFn.m2(t) { key, value ->
                if (data.size < 64 || data.containsKey(key.tojstring())) data[key.tojstring().take(80)] = value
                t
            })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(data.containsKey(it.tojstring())) })
            t.set("keys", LuaFn.z { LuaFn.fromJava(data.keys.toList()) })
            t.set("toJSON", LuaFn.z { LuaFn.fromJava(data.mapValues { luaToJava(it.value) }) })
            t.set("merge", LuaFn.m1(t) { table ->
                tableToValueMap(table).forEach { (k, v) -> if (data.size < 64 || data.containsKey(k)) data[k] = v }
                t
            })
            t.set("persist", LuaFn.m1(t) { key ->
                if (bridge.granted(PluginPermission.STORAGE)) {
                    val encoded = data.entries.joinToString("\n") { "${it.key}=${it.value.tojstring().take(200)}" }.take(8000)
                    runCatching { bridge.storage().set(key.optjstring("config"), encoded) }
                }
                t
            })
            t.set("load", LuaFn.m1(t) { key ->
                if (bridge.granted(PluginPermission.STORAGE)) {
                    runCatching {
                        val raw = bridge.storage().get(key.optjstring("config")) ?: return@m1 t
                        raw.lines().take(64).forEach { line ->
                            val kv = line.split('=', limit = 2)
                            if (kv.size == 2) data[kv[0].take(80)] = LuaValue.valueOf(kv[1])
                        }
                    }
                }
                t
            })
            t
        })
        return type
    }

    private fun exprType(): LuaTable {
        val type = LuaTable()
        type.set("eval", LuaFn.t { src, env ->
            runCatching { LuaValue.valueOf(Expr.eval(src.tojstring(), tableToNumberMap(env))) }
                .getOrDefault(LuaValue.NIL)
        })
        type.set("bool", LuaFn.t { src, env ->
            runCatching { LuaValue.valueOf(Expr.eval(src.tojstring(), tableToNumberMap(env)) != 0.0) }
                .getOrDefault(LuaValue.FALSE)
        })
        type.set("valid", LuaFn.o {
            runCatching { Expr.eval(it.tojstring(), emptyMap()); LuaValue.TRUE }.getOrDefault(LuaValue.FALSE)
        })
        return type
    }

    private fun paginatorType(): LuaTable {
        val type = LuaTable()
        type.set("page", LuaFn.r { list, page, size ->
            val items = arrayFrom(list)
            val per = size.optint(10).coerceIn(1, 64)
            val p = page.optint(1).coerceAtLeast(1)
            val pages = max(1, (items.size + per - 1) / per)
            val start = ((p - 1) * per).coerceAtMost(items.size)
            val slice = items.drop(start).take(per)
            LuaFn.fromJava(
                mapOf(
                    "items" to slice.map { luaToJava(it) },
                    "page" to p.coerceAtMost(pages),
                    "pages" to pages,
                    "total" to items.size,
                    "has_next" to (p < pages),
                    "has_prev" to (p > 1),
                ),
            )
        })
        return type
    }

    private fun searchType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val docs = LinkedHashMap<String, String>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("SearchIndex"))
            t.set("add", LuaFn.m2(t) { id, body ->
                if (docs.size < MAX) docs[id.tojstring().take(80)] = body.tojstring().lowercase(Locale.US).take(400)
                t
            })
            t.set("remove", LuaFn.m1(t) { LuaValue.valueOf(docs.remove(it.tojstring()) != null) })
            t.set("search", LuaFn.m1(t) { needle ->
                val q = needle.tojstring().lowercase(Locale.US).trim()
                if (q.isEmpty()) return@m1 LuaFn.fromJava(emptyList<String>())
                LuaFn.fromJava(docs.filter { it.value.contains(q) || it.key.contains(q) }.keys.take(32).toList())
            })
            t.set("suggest", LuaFn.m1(t) { needle ->
                val q = needle.tojstring().lowercase(Locale.US).trim()
                LuaFn.fromJava(docs.keys.filter { it.lowercase(Locale.US).startsWith(q) }.take(16).toList())
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(docs.size) })
            t
        })
        return type
    }

    private fun channelType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val q = ArrayDeque<LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Channel"))
            t.set("send", LuaFn.m1(t) {
                if (q.size < MAX) q.addLast(it)
                t
            })
            t.set("recv", LuaFn.z { q.pollFirst() ?: LuaValue.NIL })
            t.set("size", LuaFn.z { LuaValue.valueOf(q.size) })
            t.set("empty", LuaFn.z { LuaValue.valueOf(q.isEmpty()) })
            t
        })
        return type
    }

    private fun busType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val topics = LinkedHashMap<String, MutableList<LuaValue>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Bus"))
            t.set("on", LuaFn.m2(t) { topic, fn ->
                val list = topics.getOrPut(topic.tojstring().take(40)) { mutableListOf() }
                if (fn.isfunction() && list.size < MAX_LISTENERS) list.add(fn)
                t
            })
            t.set("emit", LuaFn.m2(t) { topic, payload ->
                topics[topic.tojstring()].orEmpty().forEach { runCatching { it.call(payload) } }
                t
            })
            t.set("off", LuaFn.m1(t) {
                topics.remove(it.tojstring())
                t
            })
            t.set("topics", LuaFn.z { LuaFn.fromJava(topics.keys.toList()) })
            t
        })
        return type
    }

    private fun rateType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.t { n, windowMs ->
            val cap = n.optint(5).coerceIn(1, 64)
            val window = windowMs.optdouble(60_000.0).toLong().coerceIn(1_000, 600_000)
            val hits = LinkedHashMap<String, MutableList<Long>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("RateLimit"))
            t.set("allow", LuaFn.m1(t) { key ->
                val now = System.currentTimeMillis()
                val k = key.tojstring().take(40)
                val list = hits.getOrPut(k) { mutableListOf() }
                list.removeAll { now - it > window }
                if (list.size >= cap) LuaValue.FALSE else {
                    list.add(now)
                    LuaValue.TRUE
                }
            })
            t.set("remaining", LuaFn.m1(t) { key ->
                val now = System.currentTimeMillis()
                val list = hits[key.tojstring()].orEmpty().filter { now - it <= window }
                LuaValue.valueOf((cap - list.size).coerceAtLeast(0))
            })
            t
        })
        return type
    }

    private fun onceType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { fn ->
            var done = false
            var value: LuaValue = LuaValue.NIL
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Once"))
            t.set("run", LuaFn.z {
                if (!done) {
                    done = true
                    value = if (fn.isfunction()) runCatching { fn.call() }.getOrDefault(LuaValue.NIL) else fn
                }
                value
            })
            t.set("done", LuaFn.z { LuaValue.valueOf(done) })
            t
        })
        return type
    }

    private fun retryType(): LuaTable {
        val type = LuaTable()
        type.set("run", LuaFn.t { fn, n ->
            val tries = n.optint(3).coerceIn(1, 8)
            var last: LuaValue = LuaValue.NIL
            repeat(tries) {
                last = runCatching { fn.call() }.getOrDefault(LuaValue.NIL)
                if (!last.isnil() && !(last.isboolean() && !last.toboolean())) return@t last
            }
            last
        })
        return type
    }

    private fun checklistType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val items = mutableListOf<Pair<String, Boolean>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Checklist"))
            t.set("add", LuaFn.m1(t) {
                if (items.size < MAX) items.add(it.tojstring().take(80) to false)
                t
            })
            t.set("check", LuaFn.m1(t) { idx ->
                val i = idx.toint() - 1
                if (i in items.indices) items[i] = items[i].first to true
                t
            })
            t.set("uncheck", LuaFn.m1(t) { idx ->
                val i = idx.toint() - 1
                if (i in items.indices) items[i] = items[i].first to false
                t
            })
            t.set("toggle", LuaFn.m1(t) { idx ->
                val i = idx.toint() - 1
                if (i in items.indices) items[i] = items[i].first to !items[i].second
                t
            })
            t.set("progress", LuaFn.z {
                if (items.isEmpty()) LuaValue.valueOf(0.0)
                else LuaValue.valueOf(items.count { it.second }.toDouble() / items.size)
            })
            t.set("done", LuaFn.z { LuaValue.valueOf(items.isNotEmpty() && items.all { it.second }) })
            t.set("to_table", LuaFn.z {
                LuaFn.fromJava(items.map { mapOf("text" to it.first, "done" to it.second) })
            })
            t.set("to_ui", LuaFn.z {
                val ui = luna.get("ui")
                val out = LuaTable()
                items.take(PluginLimits.MAX_UI_ITEMS).forEachIndexed { i, (text, done) ->
                    val spec = LuaTable()
                    spec.set("title", LuaValue.valueOf(text))
                    spec.set("body", LuaValue.valueOf(if (done) "done" else "open"))
                    spec.set("trailing", LuaValue.valueOf(if (done) "ok" else ""))
                    spec.set("tone", LuaValue.valueOf(if (done) "success" else "accent"))
                    out.set(i + 1, runCatching { ui.get("list_item").call(spec) }.getOrDefault(spec))
                }
                out
            })
            t
        })
        return type
    }

    private fun tagsType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { src ->
            val tags = linkedSetFrom(src)
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Tags"))
            t.set("add", LuaFn.m1(t) {
                if (tags.size < 64) tags.add(it.tojstring().lowercase(Locale.US).take(32))
                t
            })
            t.set("remove", LuaFn.m1(t) { LuaValue.valueOf(tags.remove(it.tojstring().lowercase(Locale.US))) })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(tags.contains(it.tojstring().lowercase(Locale.US))) })
            t.set("toggle", LuaFn.m1(t) { key ->
                val k = key.tojstring().lowercase(Locale.US).take(32)
                if (!tags.add(k)) tags.remove(k)
                LuaValue.valueOf(tags.contains(k))
            })
            t.set("to_table", LuaFn.z { LuaFn.fromJava(tags.toList()) })
            t.set("toJSON", t.get("to_table"))
            t.set("size", LuaFn.z { LuaValue.valueOf(tags.size) })
            t.set("any", LuaFn.m1(t) { other ->
                LuaValue.valueOf(LuaFn.stringList(other, 64).any { it.lowercase(Locale.US) in tags })
            })
            t.set("all", LuaFn.m1(t) { other ->
                val needed = LuaFn.stringList(other, 64).map { it.lowercase(Locale.US) }
                LuaValue.valueOf(needed.isNotEmpty() && needed.all { it in tags })
            })
            t
        })
        return type
    }

    private fun counterType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val counts = LinkedHashMap<String, Int>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Counter"))
            t.set("inc", LuaFn.m1(t) { key ->
                val k = key.tojstring().take(40)
                counts[k] = (counts[k] ?: 0) + 1
                LuaValue.valueOf(counts[k] ?: 0)
            })
            t.set("add", LuaFn.m2(t) { key, n ->
                val k = key.tojstring().take(40)
                counts[k] = (counts[k] ?: 0) + n.optint(1)
                LuaValue.valueOf((counts[k] ?: 0).toDouble())
            })
            t.set("set", LuaFn.m2(t) { key, n ->
                counts[key.tojstring().take(40)] = n.optint(0)
                t
            })
            t.set("get", LuaFn.m1(t) { LuaValue.valueOf((counts[it.tojstring()] ?: 0).toDouble()) })
            t.set("top", LuaFn.m1(t) { n ->
                LuaFn.fromJava(
                    counts.entries.sortedByDescending { it.value }.take(n.optint(5).coerceIn(1, 32))
                        .map { mapOf("key" to it.key, "count" to it.value) },
                )
            })
            t.set("toJSON", LuaFn.z { LuaFn.fromJava(counts) })
            t.set("clear", LuaFn.z {
                counts.clear()
                t
            })
            t
        })
        return type
    }

    private fun pqType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            data class Node(val value: LuaValue, val priority: Int)
            val heap = PriorityQueue<Node>(compareBy { it.priority })
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("PriorityQueue"))
            t.set("push", LuaFn.m2(t) { value, pri ->
                if (heap.size < MAX) heap.add(Node(value, pri.optint(0)))
                t
            })
            t.set("pop", LuaFn.z { heap.poll()?.value ?: LuaValue.NIL })
            t.set("peek", LuaFn.z { heap.peek()?.value ?: LuaValue.NIL })
            t.set("size", LuaFn.z { LuaValue.valueOf(heap.size) })
            t
        })
        return type
    }

    private fun treeType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { label ->
            wrapTree(label.optjstring("root"), mutableListOf())
        })
        return type
    }

    private fun wrapTree(label: String, kids: MutableList<LuaTable>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Tree"))
        t.set("label", LuaValue.valueOf(label.take(80)))
        t.set("add", LuaFn.m1(t) { child ->
            val node = if (child.istable() && child.get("__kind").optjstring("") == "Tree") {
                child.checktable()
            } else {
                wrapTree(child.tojstring(), mutableListOf())
            }
            if (kids.size < 32) kids.add(node)
            t
        })
        t.set("children", LuaFn.z { LuaFn.fromJava(kids) })
        t.set("size", LuaFn.z { LuaValue.valueOf(kids.size) })
        t.set("walk", LuaFn.m1(t) { fn ->
            runCatching { fn.call(LuaValue.valueOf(label)) }
            kids.forEach { kid -> runCatching { kid.get("walk").call(kid, fn) } }
            t
        })
        t.set("toJSON", LuaFn.z {
            LuaFn.fromJava(mapOf("label" to label, "children" to kids.map { it.get("label").tojstring() }))
        })
        return t
    }

    private fun jsonPathType(): LuaTable {
        val type = LuaTable()
        type.set("get", LuaFn.t { root, path -> walkGet(root, path.tojstring()) })
        type.set("set", LuaFn.r { root, path, value ->
            walkSet(root, path.tojstring(), value)
            root
        })
        type.set("has", LuaFn.t { root, path -> LuaValue.valueOf(!walkGet(root, path.tojstring()).isnil()) })
        return type
    }

    private fun queryType(): LuaTable {
        val type = LuaTable()
        type.set("from", LuaFn.o { src ->
            var rows = arrayFrom(src)
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("TableQuery"))
            t.set("where", LuaFn.m1(t) { fn ->
                rows = rows.filter { runCatching { fn.call(it).toboolean() }.getOrDefault(false) }.toMutableList()
                t
            })
            t.set("map", LuaFn.m1(t) { fn ->
                rows = rows.map { runCatching { fn.call(it) }.getOrDefault(it) }.toMutableList()
                t
            })
            t.set("sort", LuaFn.m1(t) { key ->
                val k = key.tojstring()
                rows = rows.sortedBy { if (it.istable()) it.get(k).tojstring() else it.tojstring() }.toMutableList()
                t
            })
            t.set("limit", LuaFn.m1(t) { n ->
                rows = rows.take(n.toint().coerceIn(0, MAX)).toMutableList()
                t
            })
            t.set("skip", LuaFn.m1(t) { n ->
                rows = rows.drop(n.toint().coerceIn(0, MAX)).toMutableList()
                t
            })
            t.set("count", LuaFn.z { LuaValue.valueOf(rows.size) })
            t.set("to_table", LuaFn.z { LuaFn.fromJava(rows.map { luaToJava(it) }) })
            t.set("first", LuaFn.z { rows.firstOrNull() ?: LuaValue.NIL })
            t.set("group", LuaFn.m1(t) { key ->
                val k = key.tojstring()
                val grouped = LinkedHashMap<String, MutableList<LuaValue>>()
                rows.forEach { row ->
                    val name = if (row.istable()) row.get(k).tojstring() else row.tojstring()
                    grouped.getOrPut(name) { mutableListOf() }.add(row)
                }
                val out = LuaTable()
                grouped.forEach { (name, items) -> out.set(name, LuaFn.fromJava(items.map { luaToJava(it) })) }
                out
            })
            t
        })
        return type
    }

    private fun markdownType(): LuaTable {
        val type = LuaTable()
        type.set("strip", LuaFn.o {
            LuaValue.valueOf(
                it.tojstring()
                    .replace(Regex("```[\\s\\S]*?```"), " ")
                    .replace(Regex("[*_`#>+\\-]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(2000),
            )
        })
        type.set("headings", LuaFn.o {
            LuaFn.fromJava(
                it.tojstring().lines().filter { line -> line.trimStart().startsWith("#") }.map { line ->
                    line.trimStart().trimStart('#').trim()
                }.filter { it.isNotEmpty() }.take(32),
            )
        })
        type.set("bullets", LuaFn.o {
            LuaFn.fromJava(
                it.tojstring().lines().map { line -> line.trim() }
                    .filter { it.startsWith("- ") || it.startsWith("* ") }
                    .map { it.drop(2).trim() }
                    .take(64),
            )
        })
        return type
    }

    private fun idType(): LuaTable {
        val type = LuaTable()
        type.set("short", LuaFn.z {
            LuaValue.valueOf(List(8) { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString(""))
        })
        type.set("ulid", LuaFn.z {
            val time = System.currentTimeMillis().toString(36)
            val rand = List(8) { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
            LuaValue.valueOf((time + rand).take(20))
        })
        type.set("slug", LuaFn.o {
            LuaValue.valueOf(
                it.tojstring().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(80),
            )
        })
        return type
    }

    private fun themeType(): LuaTable {
        val type = LuaTable()
        type.set("tokens", LuaFn.z {
            LuaFn.fromJava(
                mapOf(
                    "accent" to "accent",
                    "success" to "success",
                    "warn" to "warn",
                    "danger" to "danger",
                    "info" to "info",
                    "tones" to listOf("accent", "success", "warn", "danger", "info"),
                ),
            )
        })
        type.set("tone", LuaFn.o {
            val raw = it.tojstring().lowercase(Locale.US)
            LuaValue.valueOf(
                when (raw) {
                    "ok", "green" -> "success"
                    "warning", "yellow" -> "warn"
                    "error", "red" -> "danger"
                    "blue" -> "info"
                    else -> raw
                },
            )
        })
        return type
    }

    private fun dashboardType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { title ->
            val heading = title.optjstring("Dashboard")
            val items = mutableListOf<LuaValue>()
            val ui = luna.get("ui")
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Dashboard"))
            fun add(node: LuaValue): LuaTable {
                if (items.size < PluginLimits.MAX_UI_ITEMS) items.add(node)
                return t
            }
            t.set("stat", LuaFn.m1(t) { add(runCatching { ui.get("stat").call(it) }.getOrDefault(it)) })
            t.set("item", LuaFn.m1(t) { add(runCatching { ui.get("list_item").call(it) }.getOrDefault(it)) })
            t.set("empty", LuaFn.m1(t) { add(runCatching { ui.get("empty").call(it) }.getOrDefault(it)) })
            t.set("chips", LuaFn.m1(t) { add(runCatching { ui.get("chips").call(it) }.getOrDefault(it)) })
            t.set("alert", LuaFn.m1(t) { add(runCatching { ui.get("alert").call(it) }.getOrDefault(it)) })
            t.set("progress", LuaFn.m1(t) { add(runCatching { ui.get("progress").call(it) }.getOrDefault(it)) })
            t.set("quote", LuaFn.m1(t) { add(runCatching { ui.get("quote").call(it) }.getOrDefault(it)) })
            t.set("fold", LuaFn.m1(t) { add(runCatching { ui.get("fold").call(it) }.getOrDefault(it)) })
            t.set("steps", LuaFn.m1(t) { add(runCatching { ui.get("steps").call(it) }.getOrDefault(it)) })
            t.set("timeline", LuaFn.m1(t) { add(runCatching { ui.get("timeline").call(it) }.getOrDefault(it)) })
            t.set("score", LuaFn.m1(t) { add(runCatching { ui.get("score").call(it) }.getOrDefault(it)) })
            t.set("compare", LuaFn.m1(t) { add(runCatching { ui.get("compare").call(it) }.getOrDefault(it)) })
            t.set("faq", LuaFn.m1(t) { add(runCatching { ui.get("faq").call(it) }.getOrDefault(it)) })
            t.set("status", LuaFn.m1(t) { add(runCatching { ui.get("status").call(it) }.getOrDefault(it)) })
            t.set("add", LuaFn.m1(t) { add(it) })
            t.set("build", LuaFn.z { pageOf(heading, items) })
            t
        })
        return type
    }

    private fun schemaFormType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.t { title, schema ->
            val ui = luna.get("ui")
            val items = mutableListOf<LuaValue>()
            if (schema.istable()) {
                val table = schema.checktable()
                var k = table.next(LuaValue.NIL)
                var n = 0
                while (!k.arg1().isnil() && n < PluginLimits.MAX_UI_ITEMS) {
                    val id = k.arg1().tojstring().take(32)
                    val spec = if (k.arg(2).istable()) k.arg(2).checktable() else LuaTable()
                    spec.set("id", LuaValue.valueOf(id))
                    if (spec.get("title").isnil()) spec.set("title", LuaValue.valueOf(id))
                    val kind = spec.get("type").optjstring("text")
                    val node = runCatching { ui.get(kind).call(spec) }.getOrDefault(spec)
                    items.add(node)
                    k = table.next(k.arg1())
                    n++
                }
            }
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("SchemaForm"))
            t.set("build", LuaFn.z { pageOf(title.optjstring("Settings"), items) })
            t
        })
        return type
    }

    private fun policyType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val allow = LinkedHashSet<String>()
            val deny = LinkedHashSet<String>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Policy"))
            t.set("allow", LuaFn.m1(t) {
                if (allow.size < MAX) allow.add(DomainValidator.normalize(it.tojstring()))
                t
            })
            t.set("deny", LuaFn.m1(t) {
                if (deny.size < MAX) deny.add(DomainValidator.normalize(it.tojstring()))
                t
            })
            t.set("test", LuaFn.m1(t) { host ->
                val n = DomainValidator.normalize(host.tojstring())
                val blocked = deny.any { matches(n, it) }
                val allowed = allow.isEmpty() || allow.any { matches(n, it) }
                LuaValue.valueOf(!blocked && allowed)
            })
            t
        })
        return type
    }

    private fun deepType(): LuaTable {
        val type = LuaTable()
        type.set("get", LuaFn.t { root, path -> walkGet(root, path.tojstring()) })
        type.set("merge", LuaFn.t { a, b ->
            val out = if (a.istable()) a.checktable() else LuaTable()
            if (b.istable()) {
                val extra = b.checktable()
                extra.keys().forEach { key -> out.set(key, extra.get(key)) }
            }
            out
        })
        type.set("equals", LuaFn.t { a, b -> LuaValue.valueOf(a.eq_b(b) || a.tojstring() == b.tojstring()) })
        type.set("clone", LuaFn.o { value ->
            if (!value.istable()) return@o value
            val src = value.checktable()
            val out = LuaTable()
            src.keys().forEach { key -> out.set(key, src.get(key)) }
            out
        })
        return type
    }

    private fun debounceType(bridge: PluginNativeBridge): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.t { ms, fn ->
            val wait = ms.optdouble(PluginLimits.MIN_TIMER_MS.toDouble()).toLong()
                .coerceIn(PluginLimits.MIN_TIMER_MS, PluginLimits.MAX_TIMER_MS)
            var lastId = 0
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Debounce"))
            t.set("call", LuaFn.m1(t) { arg ->
                if (lastId != 0) runCatching { bridge.cancelTimer(lastId) }
                lastId = runCatching {
                    bridge.schedule(wait, LuaFn.z {
                        runCatching { if (arg.isnil()) fn.call() else fn.call(arg) }
                        LuaValue.NIL
                    }, false)
                }.getOrDefault(0)
                t
            })
            t
        })
        return type
    }

    private fun throttleType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { ms ->
            val wait = ms.optdouble(1_000.0).toLong().coerceIn(50, 120_000)
            var last = 0L
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Throttle"))
            t.set("allow", LuaFn.z {
                val now = System.currentTimeMillis()
                if (now - last >= wait) {
                    last = now
                    LuaValue.TRUE
                } else {
                    LuaValue.FALSE
                }
            })
            t
        })
        return type
    }

    private fun catalogType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val rows = mutableListOf<Map<String, String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Catalog"))
            t.set("add", LuaFn.m1(t) { row ->
                val map = tableToStringMap(row)
                if (rows.size < MAX) rows.add(map)
                t
            })
            t.set("find", LuaFn.m1(t) { needle ->
                val q = needle.tojstring().lowercase(Locale.US)
                LuaFn.fromJava(
                    rows.filter { row -> row.values.any { it.lowercase(Locale.US).contains(q) } }.take(32),
                )
            })
            t.set("get", LuaFn.m1(t) { id ->
                val key = id.tojstring()
                val hit = rows.firstOrNull { it["id"] == key }
                if (hit == null) LuaValue.NIL else LuaFn.fromJava(hit)
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(rows.size) })
            t.set("to_table", LuaFn.z { LuaFn.fromJava(rows) })
            t
        })
        return type
    }

    private fun graphType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val adj = LinkedHashMap<String, LinkedHashSet<String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Graph"))
            t.set("link", LuaFn.m2(t) { a, b ->
                val left = a.tojstring().take(80)
                val right = b.tojstring().take(80)
                adj.getOrPut(left) { LinkedHashSet() }.add(right)
                adj.getOrPut(right) { LinkedHashSet() }.add(left)
                t
            })
            t.set("neighbors", LuaFn.m1(t) { LuaFn.fromJava(adj[it.tojstring()].orEmpty().toList()) })
            t.set("has", LuaFn.m2(t) { a, b -> LuaValue.valueOf(adj[a.tojstring()]?.contains(b.tojstring()) == true) })
            t.set("nodes", LuaFn.z { LuaFn.fromJava(adj.keys.toList()) })
            t.set("reach", LuaFn.m1(t) { start ->
                val seen = LinkedHashSet<String>()
                val q = ArrayDeque<String>()
                q.add(start.tojstring())
                while (q.isNotEmpty() && seen.size < MAX) {
                    val node = q.removeFirst()
                    if (!seen.add(node)) continue
                    adj[node].orEmpty().forEach { q.addLast(it) }
                }
                LuaFn.fromJava(seen.toList())
            })
            t
        })
        return type
    }

    private fun quotaType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { maxN ->
            val cap = maxN.optint(10).coerceIn(1, MAX)
            var used = 0
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Quota"))
            t.set("take", LuaFn.z {
                if (used >= cap) LuaValue.FALSE else {
                    used++
                    LuaValue.TRUE
                }
            })
            t.set("remaining", LuaFn.z { LuaValue.valueOf(cap - used) })
            t.set("used", LuaFn.z { LuaValue.valueOf(used) })
            t.set("ratio", LuaFn.z { LuaValue.valueOf(used.toDouble() / cap) })
            t.set("reset", LuaFn.z {
                used = 0
                t
            })
            t
        })
        return type
    }

    private fun fingerprintType(): LuaTable {
        val type = LuaTable()
        type.set("of", LuaFn.o {
            LuaValue.valueOf(it.tojstring().hashCode().toUInt().toString(16))
        })
        return type
    }

    private fun patchType(): LuaTable {
        val type = LuaTable()
        type.set("apply", LuaFn.t { target, patch ->
            val out = if (target.istable()) target.checktable() else LuaTable()
            if (patch.istable()) {
                val extra = patch.checktable()
                extra.keys().forEach { key ->
                    val v = extra.get(key)
                    if (v.isnil()) out.set(key, LuaValue.NIL) else out.set(key, v)
                }
            }
            out
        })
        type.set("diff", LuaFn.t { a, b ->
            val left = tableToStringMap(a)
            val right = tableToStringMap(b)
            LuaFn.fromJava(
                mapOf(
                    "changed" to right.filter { left[it.key] != it.value },
                    "removed" to left.keys.filter { it !in right },
                ),
            )
        })
        return type
    }

    private fun sessionType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val started = System.currentTimeMillis()
            val data = LinkedHashMap<String, LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Session"))
            t.set("age", LuaFn.z { LuaValue.valueOf((System.currentTimeMillis() - started) / 1000.0) })
            t.set("get", LuaFn.m1(t) { data[it.tojstring()] ?: LuaValue.NIL })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(data.containsKey(it.tojstring())) })
            t.set("set", LuaFn.m2(t) { key, value ->
                if (data.size < 32 || data.containsKey(key.tojstring())) data[key.tojstring().take(80)] = value
                t
            })
            t.set("toJSON", LuaFn.z {
                LuaFn.fromJava(mapOf("age" to (System.currentTimeMillis() - started) / 1000.0, "keys" to data.keys.toList()))
            })
            t
        })
        return type
    }

    private fun binderType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val map = LinkedHashMap<String, LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Binder"))
            t.set("bind", LuaFn.m2(t) { id, fn ->
                map[id.tojstring().take(32)] = fn
                t
            })
            t.set("dispatch", LuaFn.m2(t) { id, value ->
                val fn = map[id.tojstring()] ?: return@m2 LuaValue.FALSE
                runCatching { fn.call(value) }
                LuaValue.TRUE
            })
            t.set("ids", LuaFn.z { LuaFn.fromJava(map.keys.toList()) })
            t
        })
        return type
    }

    private fun matchbookType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val rules = mutableListOf<Pair<LuaValue, LuaValue>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Matchbook"))
            t.set("when", LuaFn.m2(t) { pred, action ->
                if (rules.size < 64) rules.add(pred to action)
                t
            })
            t.set("match", LuaFn.m1(t) { value ->
                rules.forEach { (pred, action) ->
                    val ok = when {
                        pred.isfunction() -> runCatching { pred.call(value).toboolean() }.getOrDefault(false)
                        pred.isstring() -> value.tojstring() == pred.tojstring() || glob(pred.tojstring(), value.tojstring())
                        else -> value.eq_b(pred)
                    }
                    if (ok) {
                        return@m1 if (action.isfunction()) runCatching { action.call(value) }.getOrDefault(action) else action
                    }
                }
                LuaValue.NIL
            })
            t
        })
        return type
    }

    private fun windowType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { size ->
            val cap = size.optint(16).coerceIn(1, MAX)
            val items = ArrayDeque<Double>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Window"))
            t.set("push", LuaFn.m1(t) {
                if (items.size >= cap) items.removeFirst()
                items.addLast(it.todouble())
                t
            })
            t.set("mean", LuaFn.z {
                if (items.isEmpty()) LuaValue.valueOf(0.0) else LuaValue.valueOf(items.average())
            })
            t.set("sum", LuaFn.z { LuaValue.valueOf(items.sum()) })
            t.set("min", LuaFn.z { LuaValue.valueOf(items.minOrNull() ?: 0.0) })
            t.set("max", LuaFn.z { LuaValue.valueOf(items.maxOrNull() ?: 0.0) })
            t.set("last", LuaFn.z { LuaValue.valueOf(items.lastOrNull() ?: 0.0) })
            t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
            t.set("to_table", LuaFn.z { LuaFn.fromJava(items.toList()) })
            t
        })
        return type
    }

    private fun pageOf(title: String, items: List<LuaValue>): LuaTable {
        val section = LuaTable()
        section.set("type", LuaValue.valueOf("section"))
        section.set("title", LuaValue.valueOf(title.take(80)))
        val arr = LuaTable()
        items.forEachIndexed { i, item -> arr.set(i + 1, item) }
        section.set("items", arr)
        val page = LuaTable()
        page.set("type", LuaValue.valueOf("page"))
        page.set("title", LuaValue.valueOf(title.take(80)))
        val sections = LuaTable()
        sections.set(1, section)
        page.set("sections", sections)
        return page
    }

    private fun matchPath(pattern: String, path: String): Map<String, String>? {
        val p = pattern.trim('/').split('/')
        val s = path.trim('/').split('/')
        if (p.size != s.size) return null
        val out = LinkedHashMap<String, String>()
        p.forEachIndexed { i, part ->
            when {
                part.startsWith(":") -> out[part.drop(1)] = s[i]
                part == "*" -> out["wildcard"] = s[i]
                !part.equals(s[i], ignoreCase = true) -> return null
            }
        }
        return out
    }

    private fun matches(host: String, pattern: String): Boolean {
        return host == pattern ||
            (pattern.startsWith("*.") && (host.endsWith("." + pattern.drop(2)) || host == pattern.drop(2)))
    }

    private fun glob(pattern: String, value: String): Boolean {
        val regex = buildString {
            append('^')
            pattern.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
            append('$')
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(value)
    }

    private fun arrayFrom(value: LuaValue): MutableList<LuaValue> {
        if (!value.istable()) return mutableListOf()
        val table = value.checktable()
        val out = mutableListOf<LuaValue>()
        for (i in 1..table.length().coerceAtMost(MAX)) out.add(table.get(i))
        return out
    }

    private fun linkedSetFrom(value: LuaValue): LinkedHashSet<String> {
        return arrayFrom(value).map { it.tojstring().lowercase(Locale.US).take(32) }.filter { it.isNotBlank() }
            .take(64).toCollection(LinkedHashSet())
    }

    private fun tableToStringMap(value: LuaValue): Map<String, String> {
        if (!value.istable()) return emptyMap()
        val table = value.checktable()
        val out = LinkedHashMap<String, String>()
        var k = table.next(LuaValue.NIL)
        while (!k.arg1().isnil() && out.size < 64) {
            out[k.arg1().tojstring().take(80)] = k.arg(2).tojstring().take(400)
            k = table.next(k.arg1())
        }
        return out
    }

    private fun tableToValueMap(value: LuaValue): Map<String, LuaValue> {
        if (!value.istable()) return emptyMap()
        val table = value.checktable()
        val out = LinkedHashMap<String, LuaValue>()
        var k = table.next(LuaValue.NIL)
        while (!k.arg1().isnil() && out.size < 64) {
            out[k.arg1().tojstring().take(80)] = k.arg(2)
            k = table.next(k.arg1())
        }
        return out
    }

    private fun tableToNumberMap(value: LuaValue): Map<String, Double> {
        if (!value.istable()) return emptyMap()
        val table = value.checktable()
        val out = LinkedHashMap<String, Double>()
        var k = table.next(LuaValue.NIL)
        while (!k.arg1().isnil() && out.size < 32) {
            val v = k.arg(2)
            if (v.isnumber() || v.isboolean()) out[k.arg1().tojstring()] = if (v.isboolean()) if (v.toboolean()) 1.0 else 0.0 else v.todouble()
            k = table.next(k.arg1())
        }
        return out
    }

    private fun luaToJava(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        value.istable() -> {
            val table = value.checktable()
            if (table.get(1) != LuaValue.NIL) arrayFrom(table).map { luaToJava(it) } else tableToStringMap(table)
        }
        else -> value.tojstring()
    }

    private fun walkGet(root: LuaValue, path: String): LuaValue {
        var cur = root
        path.split('.').filter { it.isNotBlank() }.take(12).forEach { part ->
            if (!cur.istable()) return LuaValue.NIL
            cur = if (part.toIntOrNull() != null) cur.get(part.toInt()) else cur.get(part)
        }
        return cur
    }

    private fun walkSet(root: LuaValue, path: String, value: LuaValue) {
        if (!root.istable()) return
        val parts = path.split('.').filter { it.isNotBlank() }.take(12)
        if (parts.isEmpty()) return
        var cur = root.checktable()
        parts.dropLast(1).forEach { part ->
            val next = if (part.toIntOrNull() != null) cur.get(part.toInt()) else cur.get(part)
            if (!next.istable()) {
                val created = LuaTable()
                if (part.toIntOrNull() != null) cur.set(part.toInt(), created) else cur.set(part, created)
                cur = created
            } else {
                cur = next.checktable()
            }
        }
        val last = parts.last()
        if (last.toIntOrNull() != null) cur.set(last.toInt(), value) else cur.set(last, value)
    }
}

private object Expr {
    fun eval(src: String, env: Map<String, Double>): Double {
        return Parser(src, env).parse()
    }

    private class Parser(src: String, private val env: Map<String, Double>) {
        private val text = src
        private var i = 0
        fun parse(): Double = or()
        private fun skip() {
            while (i < text.length && text[i].isWhitespace()) i++
        }
        private fun or(): Double {
            var v = and()
            skip()
            while (match("||") || keyword("or")) {
                skip()
                v = if (v != 0.0 || and() != 0.0) 1.0 else 0.0
                skip()
            }
            return v
        }
        private fun and(): Double {
            var v = cmp()
            skip()
            while (match("&&") || keyword("and")) {
                skip()
                v = if (v != 0.0 && cmp() != 0.0) 1.0 else 0.0
                skip()
            }
            return v
        }
        private fun cmp(): Double {
            skip()
            var v = add()
            skip()
            v = when {
                match("==") -> if (v == add()) 1.0 else 0.0
                match("!=") -> if (v != add()) 1.0 else 0.0
                match("<=") -> if (v <= add()) 1.0 else 0.0
                match(">=") -> if (v >= add()) 1.0 else 0.0
                match("<") -> if (v < add()) 1.0 else 0.0
                match(">") -> if (v > add()) 1.0 else 0.0
                else -> v
            }
            return v
        }
        private fun add(): Double {
            skip()
            var v = mul()
            skip()
            while (true) {
                skip()
                v = when {
                    match("+") -> v + mul()
                    match("-") -> v - mul()
                    else -> return v
                }
                skip()
            }
        }
        private fun mul(): Double {
            skip()
            var v = unary()
            skip()
            while (true) {
                skip()
                v = when {
                    match("*") -> v * unary()
                    match("/") -> {
                        val d = unary(); if (d == 0.0) 0.0 else v / d
                    }
                    match("%") -> {
                        val d = unary(); if (d == 0.0) 0.0 else v % d
                    }
                    else -> return v
                }
                skip()
            }
        }
        private fun unary(): Double {
            skip()
            if (match("-") || keyword("not") || match("!")) return if (unary() == 0.0) 1.0 else 0.0
            return primary()
        }
        private fun primary(): Double {
            skip()
            if (match("(")) {
                val v = parse()
                skip()
                match(")")
                return v
            }
            if (keyword("true")) return 1.0
            if (keyword("false")) return 0.0
            if (i < text.length && (text[i].isDigit() || text[i] == '.')) {
                val start = i
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                return text.substring(start, i).toDoubleOrNull() ?: 0.0
            }
            if (i < text.length && (text[i].isLetter() || text[i] == '_')) {
                val start = i
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                return env[text.substring(start, i)] ?: 0.0
            }
            throw IllegalArgumentException("bad expr")
        }
        private fun match(tok: String): Boolean {
            skip()
            if (text.startsWith(tok, i)) {
                i += tok.length
                return true
            }
            return false
        }
        private fun keyword(word: String): Boolean {
            skip()
            if (!text.startsWith(word, i)) return false
            val end = i + word.length
            if (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) return false
            i = end
            return true
        }
    }
}
