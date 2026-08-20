package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.plugin.PLUGIN_API_LEVEL
import com.lunasdev.lunasdpi.plugin.PluginLimits
import com.lunasdev.lunasdpi.plugin.PluginPermission
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object LunaSdk {
    private const val MAX = 256
    private const val MAX_LISTENERS = 8
    private const val GITHUB_HOST = "github.com"

    private val typeNames = listOf(
        "List", "Set", "Queue", "Stack", "LRU", "DomainSet", "CidrSet", "URL", "Query",
        "Template", "Buffer", "Diff", "Fuzzy", "Random", "Store", "Dict", "Signal", "State",
        "Flags", "Logger", "Metrics", "Histogram", "Matcher", "Result", "Optional", "Range",
        "BitSet", "Interval", "Csv", "Stopwatch", "Memo", "Registry", "RingBuffer", "Glob",
        "FormBuilder", "WizardBuilder", "TableBuilder", "Enums", "Constants",
    )

    fun install(luna: LuaTable, bridge: PluginNativeBridge) {
        luna.set("List", listType())
        luna.set("Set", setType())
        luna.set("Queue", queueType())
        luna.set("Stack", stackType())
        luna.set("LRU", lruType())
        luna.set("DomainSet", domainSetType())
        luna.set("CidrSet", cidrSetType())
        luna.set("URL", urlType())
        luna.set("Query", queryType())
        luna.set("Template", templateType())
        luna.set("Buffer", bufferType())
        luna.set("Diff", diffType())
        luna.set("Fuzzy", fuzzyType())
        luna.set("Random", randomType())
        luna.set("Store", storeType())
        luna.set("Dict", storeType())
        luna.set("Signal", signalType())
        luna.set("State", stateType())
        luna.set("Flags", flagsType(bridge))
        luna.set("Logger", loggerType(bridge))
        luna.set("Metrics", metricsType())
        luna.set("Histogram", histogramType())
        luna.set("Matcher", domainSetType())
        luna.set("Result", resultType())
        luna.set("Optional", optionalType())
        luna.set("Range", rangeType())
        luna.set("BitSet", bitSetType())
        luna.set("Interval", intervalType())
        luna.set("Csv", csvType())
        luna.set("Stopwatch", stopwatchType())
        luna.set("Memo", memoType())
        luna.set("Registry", registryType())
        luna.set("RingBuffer", ringType())
        luna.set("Glob", globType())
        luna.set("FormBuilder", formBuilderType(luna))
        luna.set("WizardBuilder", wizardBuilderType())
        luna.set("TableBuilder", tableBuilderType(luna))
        luna.set("Enums", enums())
        luna.set("Constants", constants())
        luna.set(
            "systems",
            LuaFn.fromJava(
                mapOf(
                    "collections" to true,
                    "net_parse" to true,
                    "text" to true,
                    "reactive" to true,
                    "metrics" to true,
                    "forms" to true,
                    "debug" to true,
                    "schema" to true,
                    "fs" to true,
                    "network" to false,
                    "tun" to false,
                    "tls" to false,
                    "shell" to false,
                    "java" to false,
                ),
            ),
        )
        val sdk = LuaTable()
        typeNames.forEach { name -> sdk.set(name, luna.get(name)) }
        sdk.set("systems", luna.get("systems"))
        luna.set("sdk", sdk)
    }

    fun nestOnto(client: LuaTable, luna: LuaTable) {
        typeNames.forEach { name -> client.set(name, luna.get(name)) }
        client.set("systems", luna.get("systems"))
        client.set("sdk", luna.get("sdk"))
    }

    private fun listType(): LuaTable {
        val type = LuaTable()
        val ctor = LuaFn.o { wrapList(arrayFrom(it)) }
        type.set("new", ctor)
        type.set("from", ctor)
        type.set("of", LuaFn.v { args ->
            wrapList((1..args.narg()).map { args.arg(it) }.take(MAX).toMutableList())
        })
        type.set("empty", LuaFn.z { wrapList(mutableListOf()) })
        return type
    }

    private fun wrapList(items: MutableList<LuaValue>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("List"))
        t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
        t.set("length", t.get("size"))
        t.set("empty", LuaFn.z { LuaValue.valueOf(items.isEmpty()) })
        t.set("get", LuaFn.m1(t) { items.getOrNull(it.toint() - 1) ?: LuaValue.NIL })
        t.set("at", t.get("get"))
        t.set("first", LuaFn.z { items.firstOrNull() ?: LuaValue.NIL })
        t.set("last", LuaFn.z { items.lastOrNull() ?: LuaValue.NIL })
        t.set("push", LuaFn.m1(t) {
            if (items.size < MAX) items.add(it)
            t
        })
        t.set("append", t.get("push"))
        t.set("add", t.get("push"))
        t.set("unshift", LuaFn.m1(t) {
            if (items.size < MAX) items.add(0, it)
            t
        })
        t.set("prepend", t.get("unshift"))
        t.set("pop", LuaFn.z { if (items.isEmpty()) LuaValue.NIL else items.removeAt(items.lastIndex) })
        t.set("shift", LuaFn.z { if (items.isEmpty()) LuaValue.NIL else items.removeAt(0) })
        t.set("insert", LuaFn.m2(t) { idx, value ->
            val i = (idx.toint() - 1).coerceIn(0, items.size)
            if (items.size < MAX) items.add(i, value)
            t
        })
        t.set("remove_at", LuaFn.m1(t) {
            val i = it.toint() - 1
            if (i in items.indices) items.removeAt(i) else LuaValue.NIL
        })
        t.set("clear", LuaFn.z {
            items.clear()
            t
        })
        t.set("contains", LuaFn.m1(t) { LuaValue.valueOf(items.any { item -> item.eq_b(it) }) })
        t.set("index_of", LuaFn.m1(t) {
            val i = items.indexOfFirst { item -> item.eq_b(it) }
            LuaValue.valueOf(if (i < 0) 0 else i + 1)
        })
        t.set("reverse", LuaFn.z {
            items.reverse()
            t
        })
        t.set("unique", LuaFn.z {
            val seen = LinkedHashSet<String>()
            val next = mutableListOf<LuaValue>()
            items.forEach { item -> if (seen.add(item.tojstring())) next.add(item) }
            items.clear()
            items.addAll(next)
            t
        })
        t.set("sort", LuaFn.z {
            items.sortWith { a, b -> a.tojstring().compareTo(b.tojstring()) }
            t
        })
        t.set("map", LuaFn.m1(t) { fn -> wrapList(items.take(MAX).map { call1(fn, it) }.toMutableList()) })
        t.set("filter", LuaFn.m1(t) { fn ->
            wrapList(items.filter { call1(fn, it).toboolean() }.take(MAX).toMutableList())
        })
        t.set("each", LuaFn.m1(t) { fn ->
            items.forEach { call1(fn, it) }
            t
        })
        t.set("join", LuaFn.m1(t) {
            LuaValue.valueOf(items.joinToString(it.optjstring(",")) { item -> item.tojstring() })
        })
        t.set("to_table", LuaFn.z { LuaFn.fromJava(items.map { luaToJava(it) }) })
        t.set("toJSON", t.get("to_table"))
        t.set("clone", LuaFn.z { wrapList(items.toMutableList()) })
        t.set("chunk", LuaFn.m1(t) { n ->
            val size = n.toint().coerceIn(1, MAX)
            wrapList(items.chunked(size).map { wrapList(it.toMutableList()) }.toMutableList())
        })
        t.set("slice", LuaFn.m2(t) { a, b ->
            val from = (a.toint() - 1).coerceIn(0, items.size)
            val to = if (b.isnil()) items.size else b.toint().coerceIn(from, items.size)
            wrapList(items.subList(from, to).toMutableList())
        })
        t.set("find", LuaFn.m1(t) { fn -> items.firstOrNull { call1(fn, it).toboolean() } ?: LuaValue.NIL })
        t.set("every", LuaFn.m1(t) { fn -> LuaValue.valueOf(items.all { call1(fn, it).toboolean() }) })
        t.set("some", LuaFn.m1(t) { fn -> LuaValue.valueOf(items.any { call1(fn, it).toboolean() }) })
        t.set("reduce", LuaFn.m2(t) { acc, fn ->
            var cur = acc
            items.forEach { cur = runCatching { fn.call(cur, it) }.getOrDefault(cur) }
            cur
        })
        t.set("concat", LuaFn.m1(t) { other ->
            wrapList((items + arrayFrom(if (other.istable() && other.get("to_table").isfunction()) call0(other.get("to_table")) else other)).take(MAX).toMutableList())
        })
        t.set("take", LuaFn.m1(t) { wrapList(items.take(it.toint().coerceIn(0, MAX)).toMutableList()) })
        t.set("drop", LuaFn.m1(t) { wrapList(items.drop(it.toint().coerceIn(0, MAX)).toMutableList()) })
        t.set("sum", LuaFn.z {
            LuaValue.valueOf(items.filter { it.isnumber() }.sumOf { it.todouble() })
        })
        t.set("min", LuaFn.z {
            items.filter { it.isnumber() }.minByOrNull { it.todouble() } ?: LuaValue.NIL
        })
        t.set("max", LuaFn.z {
            items.filter { it.isnumber() }.maxByOrNull { it.todouble() } ?: LuaValue.NIL
        })
        t.set("count_if", LuaFn.m1(t) { fn -> LuaValue.valueOf(items.count { call1(fn, it).toboolean() }) })
        t.set("equals", LuaFn.m1(t) { other ->
            val rhs = arrayFrom(if (other.istable() && other.get("to_table").isfunction()) call0(other.get("to_table")) else other)
            LuaValue.valueOf(items.size == rhs.size && items.indices.all { items[it].eq_b(rhs[it]) })
        })
        t.set("to_set", LuaFn.z { wrapSet(items.map { it.tojstring() }.toCollection(LinkedHashSet())) })
        t.set("includes", t.get("contains"))
        t.set("swap", LuaFn.m2(t) { a, b ->
            val i = a.toint() - 1
            val j = b.toint() - 1
            if (i in items.indices && j in items.indices) {
                val tmp = items[i]
                items[i] = items[j]
                items[j] = tmp
            }
            t
        })
        t.set("fill", LuaFn.m1(t) { value ->
            for (i in items.indices) items[i] = value
            t
        })
        t.set("shuffle", LuaFn.z {
            val next = items.shuffled()
            items.clear()
            items.addAll(next)
            t
        })
        t.set("sample", LuaFn.m1(t) {
            wrapList(items.shuffled().take(it.toint().coerceIn(0, MAX)).toMutableList())
        })
        t.set("flatten", LuaFn.z {
            val next = mutableListOf<LuaValue>()
            items.forEach { item ->
                when {
                    item.istable() && item.get("__kind").optjstring("") == "List" ->
                        next.addAll(arrayFrom(call0(item.get("to_table"))))
                    item.istable() && item.get(1) != LuaValue.NIL -> next.addAll(arrayFrom(item))
                    else -> next.add(item)
                }
            }
            wrapList(next.take(MAX).toMutableList())
        })
        t.set("rotate", LuaFn.m1(t) { n ->
            if (items.isNotEmpty()) {
                val k = Math.floorMod(n.toint(), items.size)
                val next = items.drop(k) + items.take(k)
                items.clear()
                items.addAll(next)
            }
            t
        })
        t.set("partition", LuaFn.m1(t) { fn ->
            val pass = items.filter { call1(fn, it).toboolean() }
            val fail = items.filter { !call1(fn, it).toboolean() }
            val out = LuaTable()
            out.set(1, wrapList(pass.toMutableList()))
            out.set(2, wrapList(fail.toMutableList()))
            out
        })
        t.set("group_by", LuaFn.m1(t) { fn ->
            val groups = LinkedHashMap<String, MutableList<LuaValue>>()
            items.forEach { item ->
                val key = call1(fn, item).tojstring().take(80)
                groups.getOrPut(key) { mutableListOf() }.add(item)
            }
            val out = LuaTable()
            groups.forEach { (k, v) -> out.set(k, wrapList(v)) }
            out
        })
        t.set("sort_by", LuaFn.m1(t) { fn ->
            items.sortWith { a, b -> call1(fn, a).tojstring().compareTo(call1(fn, b).tojstring()) }
            t
        })
        t.set("unique_by", LuaFn.m1(t) { fn ->
            val seen = LinkedHashSet<String>()
            wrapList(items.filter { seen.add(call1(fn, it).tojstring()) }.toMutableList())
        })
        t.set("window", LuaFn.m1(t) { n ->
            val size = n.toint().coerceIn(1, MAX)
            wrapList(items.windowed(size, 1, false).map { wrapList(it.toMutableList()) }.toMutableList())
        })
        return t
    }

    private fun setType(): LuaTable {
        val type = LuaTable()
        val ctor = LuaFn.o { wrapSet(linkedSetFrom(it)) }
        type.set("new", ctor)
        type.set("from", ctor)
        type.set("of", LuaFn.v { args ->
            wrapSet((1..args.narg()).map { args.arg(it).tojstring() }.take(MAX).toCollection(LinkedHashSet()))
        })
        return type
    }

    private fun wrapSet(items: LinkedHashSet<String>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Set"))
        t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
        t.set("empty", LuaFn.z { LuaValue.valueOf(items.isEmpty()) })
        t.set("has", LuaFn.m1(t) { LuaValue.valueOf(items.contains(it.tojstring())) })
        t.set("contains", t.get("has"))
        t.set("add", LuaFn.m1(t) {
            if (items.size < MAX) items.add(it.tojstring())
            t
        })
        t.set("remove", LuaFn.m1(t) { LuaValue.valueOf(items.remove(it.tojstring())) })
        t.set("clear", LuaFn.z {
            items.clear()
            t
        })
        t.set("union", LuaFn.m1(t) { other ->
            wrapSet(LinkedHashSet(items).also { set -> set.addAll(stringSet(other)) })
        })
        t.set("intersect", LuaFn.m1(t) { other ->
            val rhs = stringSet(other)
            wrapSet(items.filterTo(LinkedHashSet()) { it in rhs })
        })
        t.set("difference", LuaFn.m1(t) { other ->
            val rhs = stringSet(other)
            wrapSet(items.filterTo(LinkedHashSet()) { it !in rhs })
        })
        t.set("to_table", LuaFn.z { LuaFn.fromJava(items.toList()) })
        t.set("toJSON", t.get("to_table"))
        t.set("values", t.get("to_table"))
        t.set("clone", LuaFn.z { wrapSet(LinkedHashSet(items)) })
        t.set("subset", LuaFn.m1(t) { other ->
            val rhs = stringSet(other)
            LuaValue.valueOf(items.all { item -> item in rhs })
        })
        t.set("superset", LuaFn.m1(t) { LuaValue.valueOf(stringSet(it).all { it in items }) })
        t.set("xor", LuaFn.m1(t) { other ->
            val rhs = stringSet(other)
            wrapSet(((items - rhs) + (rhs - items)).toCollection(LinkedHashSet()))
        })
        t.set("equals", LuaFn.m1(t) { LuaValue.valueOf(items == stringSet(it)) })
        t.set("add_many", LuaFn.m1(t) { other ->
            stringSet(other).forEach { if (items.size < MAX) items.add(it) }
            t
        })
        t.set("each", LuaFn.m1(t) { fn ->
            items.forEach { call1(fn, LuaValue.valueOf(it)) }
            t
        })
        t.set("filter", LuaFn.m1(t) { fn ->
            wrapSet(items.filter { call1(fn, LuaValue.valueOf(it)).toboolean() }.toCollection(LinkedHashSet()))
        })
        return t
    }

    private fun queueType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { wrapQueue(ArrayDeque()) })
        return type
    }

    private fun wrapQueue(items: ArrayDeque<LuaValue>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Queue"))
        t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
        t.set("empty", LuaFn.z { LuaValue.valueOf(items.isEmpty()) })
        t.set("enqueue", LuaFn.m1(t) {
            if (items.size < MAX) items.addLast(it)
            t
        })
        t.set("push", t.get("enqueue"))
        t.set("dequeue", LuaFn.z { items.pollFirst() ?: LuaValue.NIL })
        t.set("pop", t.get("dequeue"))
        t.set("peek", LuaFn.z { items.peekFirst() ?: LuaValue.NIL })
        t.set("clear", LuaFn.z {
            items.clear()
            t
        })
        t.set("to_table", LuaFn.z { LuaFn.fromJava(items.map { luaToJava(it) }) })
        t.set("each", LuaFn.m1(t) { fn ->
            items.forEach { call1(fn, it) }
            t
        })
        return t
    }

    private fun stackType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { wrapStack(ArrayDeque()) })
        return type
    }

    private fun wrapStack(items: ArrayDeque<LuaValue>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Stack"))
        t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
        t.set("empty", LuaFn.z { LuaValue.valueOf(items.isEmpty()) })
        t.set("push", LuaFn.m1(t) {
            if (items.size < MAX) items.addLast(it)
            t
        })
        t.set("pop", LuaFn.z { items.pollLast() ?: LuaValue.NIL })
        t.set("peek", LuaFn.z { items.peekLast() ?: LuaValue.NIL })
        t.set("clear", LuaFn.z {
            items.clear()
            t
        })
        t.set("to_table", LuaFn.z { LuaFn.fromJava(items.map { luaToJava(it) }) })
        t.set("each", LuaFn.m1(t) { fn ->
            items.forEach { call1(fn, it) }
            t
        })
        return t
    }

    private fun lruType(): LuaTable {
        val type = LuaTable()
        type.set(
            "new",
            LuaFn.o { cap ->
                val capacity = cap.optint(32).coerceIn(1, MAX)
                wrapLru(
                    object : LinkedHashMap<String, LuaValue>(capacity, 0.75f, true) {
                        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LuaValue>?): Boolean {
                            return size > capacity
                        }
                    },
                    capacity,
                )
            },
        )
        return type
    }

    private fun wrapLru(map: LinkedHashMap<String, LuaValue>, capacity: Int): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("LRU"))
        t.set("capacity", LuaFn.z { LuaValue.valueOf(capacity) })
        t.set("size", LuaFn.z { LuaValue.valueOf(map.size) })
        t.set("get", LuaFn.m1(t) { map[it.tojstring()] ?: LuaValue.NIL })
        t.set("put", LuaFn.m2(t) { key, value ->
            map[key.tojstring().take(80)] = value
            t
        })
        t.set("set", t.get("put"))
        t.set("remove", LuaFn.m1(t) { LuaValue.valueOf(map.remove(it.tojstring()) != null) })
        t.set("has", LuaFn.m1(t) { LuaValue.valueOf(map.containsKey(it.tojstring())) })
        t.set("clear", LuaFn.z {
            map.clear()
            t
        })
        t.set("keys", LuaFn.z { LuaFn.fromJava(map.keys.toList()) })
        t.set("toJSON", LuaFn.z { LuaFn.fromJava(map.mapValues { luaToJava(it.value) }) })
        return t
    }

    private fun domainSetType(): LuaTable {
        val type = LuaTable()
        val ctor = LuaFn.o {
            wrapDomainSet(linkedSetFrom(it).map { DomainValidator.normalize(it) }.toCollection(LinkedHashSet()))
        }
        type.set("new", ctor)
        type.set("from", ctor)
        type.set("from_text", LuaFn.o { raw ->
            wrapDomainSet(
                raw.tojstring().lines().map { DomainValidator.normalize(it.substringBefore('#').trim()) }
                    .filter { it.isNotEmpty() }
                    .take(MAX)
                    .toCollection(LinkedHashSet()),
            )
        })
        type.set("from_lines", type.get("from_text"))
        return type
    }

    private fun wrapDomainSet(items: LinkedHashSet<String>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("DomainSet"))
        t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
        t.set("add", LuaFn.m1(t) {
            val n = DomainValidator.normalize(it.tojstring())
            if (n.isNotEmpty() && items.size < MAX) items.add(n)
            t
        })
        t.set("remove", LuaFn.m1(t) { LuaValue.valueOf(items.remove(DomainValidator.normalize(it.tojstring()))) })
        t.set("has", LuaFn.m1(t) { LuaValue.valueOf(items.contains(DomainValidator.normalize(it.tojstring()))) })
        t.set("test", LuaFn.m1(t) { host ->
            val n = DomainValidator.normalize(host.tojstring())
            LuaValue.valueOf(items.any { pattern -> domainMatches(n, pattern) })
        })
        t.set("matches", t.get("test"))
        t.set("contains", t.get("test"))
        t.set("clear", LuaFn.z {
            items.clear()
            t
        })
        t.set("to_table", LuaFn.z { LuaFn.fromJava(items.toList()) })
        t.set("toJSON", t.get("to_table"))
        t.set("clone", LuaFn.z { wrapDomainSet(LinkedHashSet(items)) })
        t.set("add_text", LuaFn.m1(t) { raw ->
            raw.tojstring().lines().map { DomainValidator.normalize(it.substringBefore('#').trim()) }
                .filter { it.isNotEmpty() }
                .forEach { if (items.size < MAX) items.add(it) }
            t
        })
        t.set("filter_hosts", LuaFn.m1(t) { list ->
            wrapList(
                arrayFrom(list).filter { host ->
                    val n = DomainValidator.normalize(host.tojstring())
                    items.any { pattern -> domainMatches(n, pattern) }
                }.toMutableList(),
            )
        })
        t.set("count_matching", LuaFn.m1(t) { list ->
            LuaValue.valueOf(
                arrayFrom(list).count { host ->
                    val n = DomainValidator.normalize(host.tojstring())
                    items.any { pattern -> domainMatches(n, pattern) }
                },
            )
        })
        return t
    }

    private fun cidrSetType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { wrapCidr(mutableListOf()) })
        return type
    }

    private fun wrapCidr(ranges: MutableList<Pair<Int, Int>>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("CidrSet"))
        t.set("size", LuaFn.z { LuaValue.valueOf(ranges.size) })
        t.set("add", LuaFn.m1(t) {
            parseCidr(it.tojstring())?.let { cidr -> if (ranges.size < MAX) ranges.add(cidr) }
            t
        })
        t.set("contains", LuaFn.m1(t) { ip ->
            val packed = HostsFile.parseIpv4(ip.tojstring()) ?: return@m1 LuaValue.FALSE
            LuaValue.valueOf(ranges.any { (net, mask) -> (packed and mask) == net })
        })
        t.set("test", t.get("contains"))
        t.set("clear", LuaFn.z {
            ranges.clear()
            t
        })
        t.set("to_table", LuaFn.z {
            LuaFn.fromJava(ranges.map { (net, mask) -> "${HostsFile.formatIpv4(net)}/${maskBits(mask)}" })
        })
        return t
    }

    private fun urlType(): LuaTable {
        val type = LuaTable()
        type.set("parse", LuaFn.o { wrapUrl(parseUrl(it.tojstring())) })
        type.set("github", LuaFn.o { LuaValue.valueOf(isGithub(it.tojstring())) })
        type.set("host", LuaFn.o { LuaValue.valueOf(parseUrl(it.tojstring())["host"] as? String ?: "") })
        type.set("scheme", LuaFn.o { LuaValue.valueOf(parseUrl(it.tojstring())["scheme"] as? String ?: "") })
        type.set("path", LuaFn.o { LuaValue.valueOf(parseUrl(it.tojstring())["path"] as? String ?: "") })
        type.set("query", LuaFn.o { LuaFn.fromJava(parseUrl(it.tojstring())["query"] as Map<*, *>) })
        type.set("join", LuaFn.t { base, extra ->
            val parsed = parseUrl(base.tojstring())
            val scheme = parsed["scheme"] as? String ?: ""
            val host = parsed["host"] as? String ?: ""
            val origin = if (scheme.isBlank()) host else "$scheme://$host"
            val add = extra.tojstring().trim()
            val path = if (add.startsWith("/")) add else "/$add"
            LuaValue.valueOf("$origin$path".take(400))
        })
        return type
    }

    private fun wrapUrl(parsed: Map<String, Any?>): LuaTable {
        val t = LuaFn.fromJava(parsed).checktable()
        t.set("__kind", LuaValue.valueOf("URL"))
        t.set("is_github", LuaFn.z { LuaValue.valueOf(parsed["host"] == GITHUB_HOST) })
        t.set("https", LuaFn.z { LuaValue.valueOf(parsed["scheme"] == "https") })
        t.set("ok", LuaFn.z { LuaValue.valueOf(parsed["valid"] == true) })
        t.set("origin", LuaFn.z {
            val scheme = parsed["scheme"] as? String ?: ""
            val host = parsed["host"] as? String ?: ""
            LuaValue.valueOf(if (scheme.isBlank()) host else "$scheme://$host")
        })
        t.set("repo", LuaFn.z {
            val path = (parsed["path"] as? String).orEmpty().trim('/')
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parsed["host"] == GITHUB_HOST && parts.size >= 2) {
                LuaValue.valueOf("${parts[0]}/${parts[1]}")
            } else LuaValue.NIL
        })
        t.set("toJSON", LuaFn.z { LuaFn.fromJava(parsed) })
        return t
    }

    private fun queryType(): LuaTable {
        val type = LuaTable()
        type.set("parse", LuaFn.o { LuaFn.fromJava(parseQuery(it.tojstring())) })
        type.set("build", LuaFn.o { table ->
            val map = tableToStringMap(table)
            LuaValue.valueOf(map.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" })
        })
        return type
    }

    private fun templateType(): LuaTable {
        val type = LuaTable()
        type.set("render", LuaFn.t { src, vars ->
            LuaValue.valueOf(renderTemplate(src.tojstring(), tableToStringMap(vars)))
        })
        type.set(
            "compile",
            LuaFn.o { src ->
                val raw = src.tojstring()
                val compiled = LuaTable()
                compiled.set("source", LuaValue.valueOf(raw))
                compiled.set("render", LuaFn.m1(compiled) { vars ->
                    LuaValue.valueOf(renderTemplate(raw, tableToStringMap(vars)))
                })
                compiled
            },
        )
        return type
    }

    private fun bufferType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { wrapBuffer(StringBuilder(it.optjstring("").take(4096))) })
        return type
    }

    private fun wrapBuffer(buf: StringBuilder): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Buffer"))
        t.set("write", LuaFn.m1(t) {
            if (buf.length < 8192) buf.append(it.tojstring().take(1024))
            t
        })
        t.set("writeln", LuaFn.m1(t) {
            if (buf.length < 8192) buf.append(it.tojstring().take(1024)).append('\n')
            t
        })
        t.set("clear", LuaFn.z {
            buf.setLength(0)
            t
        })
        t.set("size", LuaFn.z { LuaValue.valueOf(buf.length) })
        t.set("tostring", LuaFn.z { LuaValue.valueOf(buf.toString()) })
        t.set("toJSON", t.get("tostring"))
        return t
    }

    private fun diffType(): LuaTable {
        val type = LuaTable()
        type.set("changed", LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring() != b.tojstring()) })
        type.set("equal", LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring() == b.tojstring()) })
        type.set("lines", LuaFn.t { a, b ->
            val left = a.tojstring().lines().take(400).toSet()
            val right = b.tojstring().lines().take(400).toSet()
            LuaFn.fromJava(
                mapOf(
                    "added" to (right - left).take(80).toList(),
                    "removed" to (left - right).take(80).toList(),
                    "same" to left.intersect(right).size,
                ),
            )
        })
        return type
    }

    private fun fuzzyType(): LuaTable {
        val type = LuaTable()
        type.set("levenshtein", LuaFn.t { a, b -> LuaValue.valueOf(levenshtein(a.tojstring(), b.tojstring()).toDouble()) })
        type.set("ratio", LuaFn.t { a, b -> LuaValue.valueOf(similarity(a.tojstring(), b.tojstring())) })
        type.set("similar", LuaFn.r { a, b, c ->
            LuaValue.valueOf(similarity(a.tojstring(), b.tojstring()) >= c.optdouble(0.8))
        })
        type.set("suggest", LuaFn.t { needle, list ->
            val q = needle.tojstring()
            val scored = arrayFrom(list)
                .map { it.tojstring() }
                .map { it to similarity(q, it) }
                .sortedByDescending { it.second }
                .take(8)
                .filter { it.second > 0.2 }
            LuaFn.fromJava(scored.map { mapOf("value" to it.first, "score" to it.second) })
        })
        return type
    }

    private fun randomType(): LuaTable {
        val type = LuaTable()
        type.set("int", LuaFn.t { a, b ->
            val lo = a.optint(0)
            val hi = b.optint(lo)
            val minV = min(lo, hi)
            val maxV = max(lo, hi)
            LuaValue.valueOf((minV..maxV).random())
        })
        type.set("bool", LuaFn.z { LuaValue.valueOf(listOf(true, false).random()) })
        type.set("pick", LuaFn.o { arrayFrom(it).randomOrNull() ?: LuaValue.NIL })
        type.set("id", LuaFn.z {
            LuaValue.valueOf(List(12) { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString(""))
        })
        type.set("shuffle", LuaFn.o { wrapList(arrayFrom(it).shuffled().toMutableList()) })
        type.set("sample", LuaFn.t { list, n ->
            wrapList(arrayFrom(list).shuffled().take(n.optint(1).coerceIn(1, MAX)).toMutableList())
        })
        return type
    }

    private fun storeType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { wrapStore(LinkedHashMap(), mutableListOf()) })
        return type
    }

    private fun wrapStore(data: LinkedHashMap<String, LuaValue>, listeners: MutableList<LuaValue>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Store"))
        t.set("get", LuaFn.m1(t) { data[it.tojstring()] ?: LuaValue.NIL })
        t.set("has", LuaFn.m1(t) { LuaValue.valueOf(data.containsKey(it.tojstring())) })
        t.set("set", LuaFn.m2(t) { key, value ->
            val k = key.tojstring().take(80)
            if (data.size < 64 || data.containsKey(k)) {
                data[k] = value
                emit(listeners, LuaFn.fromJava(mapOf("key" to k, "value" to luaToJava(value))))
            }
            t
        })
        t.set("delete", LuaFn.m1(t) {
            val removed = data.remove(it.tojstring()) != null
            if (removed) emit(listeners, LuaFn.fromJava(mapOf("key" to it.tojstring(), "deleted" to true)))
            LuaValue.valueOf(removed)
        })
        t.set("clear", LuaFn.z {
            data.clear()
            t
        })
        t.set("keys", LuaFn.z { LuaFn.fromJava(data.keys.toList()) })
        t.set("size", LuaFn.z { LuaValue.valueOf(data.size) })
        t.set("subscribe", LuaFn.m1(t) { fn ->
            if (fn.isfunction() && listeners.size < MAX_LISTENERS) listeners.add(fn)
            t
        })
        t.set("toJSON", LuaFn.z { LuaFn.fromJava(data.mapValues { luaToJava(it.value) }) })
        t.set("merge", LuaFn.m1(t) { table ->
            tableToValueMap(table).forEach { (k, v) ->
                if (data.size < 64 || data.containsKey(k)) data[k] = v
            }
            t
        })
        t.set("get_or", LuaFn.m2(t) { key, fallback -> data[key.tojstring()] ?: fallback })
        t.set("update", LuaFn.m2(t) { key, fn ->
            val k = key.tojstring()
            val next = runCatching { fn.call(data[k] ?: LuaValue.NIL) }.getOrDefault(data[k] ?: LuaValue.NIL)
            if (data.size < 64 || data.containsKey(k)) {
                data[k] = next
                emit(listeners, LuaFn.fromJava(mapOf("key" to k, "value" to luaToJava(next))))
            }
            t
        })
        t.set("watch", t.get("subscribe"))
        t.set("namespace", LuaFn.m1(t) { prefix ->
            val p = prefix.tojstring().take(40)
            wrapStore(
                LinkedHashMap<String, LuaValue>().also { child ->
                    data.forEach { (k, v) -> if (k.startsWith(p)) child[k.removePrefix(p)] = v }
                },
                mutableListOf(),
            )
        })
        t.set("to_table", t.get("toJSON"))
        return t
    }

    private fun signalType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z { wrapSignal(mutableListOf()) })
        return type
    }

    private fun wrapSignal(listeners: MutableList<LuaValue>): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Signal"))
        t.set("on", LuaFn.m1(t) { fn ->
            if (fn.isfunction() && listeners.size < MAX_LISTENERS) listeners.add(fn)
            t
        })
        t.set("off", LuaFn.z {
            listeners.clear()
            t
        })
        t.set("emit", LuaFn.m1(t) { payload ->
            emit(listeners, payload)
            t
        })
        t.set("size", LuaFn.z { LuaValue.valueOf(listeners.size) })
        return t
    }

    private fun stateType(): LuaTable {
        val type = LuaTable()
        type.set(
            "new",
            LuaFn.o { initial ->
                var value = initial
                val listeners = mutableListOf<LuaValue>()
                val t = LuaTable()
                t.set("__kind", LuaValue.valueOf("State"))
                t.set("get", LuaFn.z { value })
                t.set("set", LuaFn.m1(t) { next ->
                    value = next
                    emit(listeners, next)
                    t
                })
                t.set("subscribe", LuaFn.m1(t) { fn ->
                    if (fn.isfunction() && listeners.size < MAX_LISTENERS) listeners.add(fn)
                    t
                })
                t
            },
        )
        return type
    }

    private fun flagsType(bridge: PluginNativeBridge): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val flags = LinkedHashMap<String, Boolean>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Flags"))
            t.set("get", LuaFn.m1(t) { LuaValue.valueOf(flags[it.tojstring()] == true) })
            t.set("set", LuaFn.m2(t) { key, on ->
                flags[key.tojstring().take(80)] = on.toboolean()
                t
            })
            t.set("toggle", LuaFn.m1(t) { key ->
                val k = key.tojstring().take(80)
                flags[k] = flags[k] != true
                LuaValue.valueOf(flags[k] == true)
            })
            t.set("enable", LuaFn.m1(t) {
                flags[it.tojstring().take(80)] = true
                t
            })
            t.set("disable", LuaFn.m1(t) {
                flags[it.tojstring().take(80)] = false
                t
            })
            t.set("all", LuaFn.z { LuaFn.fromJava(flags) })
            t.set("toJSON", t.get("all"))
            t.set("clear", LuaFn.z {
                flags.clear()
                t
            })
            t.set("load", LuaFn.m1(t) { table ->
                tableToStringMap(table).forEach { (k, v) -> flags[k.take(80)] = v == "true" || v == "1" }
                t
            })
            t.set("from_storage", LuaFn.m1(t) { key ->
                if (bridge.granted(PluginPermission.STORAGE)) {
                    runCatching {
                        val raw = bridge.storage().get(key.optjstring("flags")) ?: return@m1 t
                        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(64).forEach { flags[it] = true }
                    }
                }
                t
            })
            t.set("persist", LuaFn.m1(t) { key ->
                if (bridge.granted(PluginPermission.STORAGE)) {
                    val enabled = flags.filter { it.value }.keys.take(64).joinToString(",")
                    runCatching { bridge.storage().set(key.optjstring("flags"), enabled) }
                }
                t
            })
            t
        })
        return type
    }

    private fun loggerType(bridge: PluginNativeBridge): LuaTable {
        val type = LuaTable()
        type.set(
            "new",
            LuaFn.o { prefix ->
                val tag = prefix.optjstring("plugin").take(40)
                val t = LuaTable()
                t.set("__kind", LuaValue.valueOf("Logger"))
                fun line(level: String, msg: LuaValue): LuaValue {
                    bridge.log(level, "[$tag] ${msg.tojstring()}")
                    return LuaValue.NIL
                }
                t.set("info", LuaFn.m1(t) { line("info", it) })
                t.set("warn", LuaFn.m1(t) { line("warn", it) })
                t.set("error", LuaFn.m1(t) { line("error", it) })
                t.set("debug", LuaFn.m1(t) { line("debug", it) })
                t.set("prefix", LuaFn.z { LuaValue.valueOf(tag) })
                t
            },
        )
        return type
    }

    private fun metricsType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val counters = LinkedHashMap<String, Double>()
            val gauges = LinkedHashMap<String, Double>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Metrics"))
            t.set("inc", LuaFn.m2(t) { name, by ->
                val k = name.tojstring().take(40)
                counters[k] = (counters[k] ?: 0.0) + by.optdouble(1.0)
                t
            })
            t.set("dec", LuaFn.m2(t) { name, by ->
                val k = name.tojstring().take(40)
                counters[k] = (counters[k] ?: 0.0) - by.optdouble(1.0)
                t
            })
            t.set("gauge", LuaFn.m2(t) { name, value ->
                gauges[name.tojstring().take(40)] = value.optdouble(0.0)
                t
            })
            t.set("get", LuaFn.m1(t) {
                counters[it.tojstring()]?.let { n -> LuaValue.valueOf(n) }
                    ?: gauges[it.tojstring()]?.let { n -> LuaValue.valueOf(n) }
                    ?: LuaValue.NIL
            })
            t.set("snapshot", LuaFn.z {
                LuaFn.fromJava(mapOf("counters" to counters.toMap(), "gauges" to gauges.toMap()))
            })
            t.set("toJSON", t.get("snapshot"))
            t.set("reset", LuaFn.z {
                counters.clear()
                gauges.clear()
                t
            })
            t
        })
        return type
    }

    private fun histogramType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val samples = mutableListOf<Double>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Histogram"))
            t.set("observe", LuaFn.m1(t) {
                if (samples.size < MAX) samples.add(it.todouble())
                t
            })
            t.set("count", LuaFn.z { LuaValue.valueOf(samples.size) })
            t.set("mean", LuaFn.z {
                if (samples.isEmpty()) LuaValue.valueOf(0.0) else LuaValue.valueOf(samples.average())
            })
            t.set("min", LuaFn.z {
                if (samples.isEmpty()) LuaValue.NIL else LuaValue.valueOf(samples.minOrNull() ?: 0.0)
            })
            t.set("max", LuaFn.z {
                if (samples.isEmpty()) LuaValue.NIL else LuaValue.valueOf(samples.maxOrNull() ?: 0.0)
            })
            t.set("reset", LuaFn.z {
                samples.clear()
                t
            })
            t.set("toJSON", LuaFn.z {
                LuaFn.fromJava(
                    mapOf(
                        "count" to samples.size,
                        "mean" to (if (samples.isEmpty()) 0.0 else samples.average()),
                    ),
                )
            })
            t
        })
        return type
    }

    private fun resultType(): LuaTable {
        val type = LuaTable()
        type.set("ok", LuaFn.o { wrapResult(true, it, LuaValue.NIL) })
        type.set("err", LuaFn.o { wrapResult(false, LuaValue.NIL, it) })
        type.set("from", LuaFn.o { value ->
            if (value.isnil()) wrapResult(false, LuaValue.NIL, LuaValue.valueOf("nil"))
            else wrapResult(true, value, LuaValue.NIL)
        })
        return type
    }

    private fun wrapResult(ok: Boolean, value: LuaValue, error: LuaValue): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Result"))
        t.set("ok", LuaValue.valueOf(ok))
        t.set("value", value)
        t.set("error", error)
        t.set("is_ok", LuaFn.z { LuaValue.valueOf(ok) })
        t.set("is_err", LuaFn.z { LuaValue.valueOf(!ok) })
        t.set("unwrap", LuaFn.z { if (ok) value else LuaValue.NIL })
        t.set("unwrap_or", LuaFn.m1(t) { if (ok) value else it })
        t.set("map", LuaFn.m1(t) { fn -> if (ok) wrapResult(true, call1(fn, value), LuaValue.NIL) else t })
        t.set("toJSON", LuaFn.z {
            LuaFn.fromJava(mapOf("ok" to ok, "value" to luaToJava(value), "error" to luaToJava(error)))
        })
        return t
    }

    private fun optionalType(): LuaTable {
        val type = LuaTable()
        type.set("of", LuaFn.o { wrapOptional(it) })
        type.set("none", LuaFn.z { wrapOptional(LuaValue.NIL) })
        type.set("from", LuaFn.o { wrapOptional(it) })
        return type
    }

    private fun wrapOptional(value: LuaValue): LuaTable {
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("Optional"))
        t.set("present", LuaFn.z { LuaValue.valueOf(!value.isnil()) })
        t.set("empty", LuaFn.z { LuaValue.valueOf(value.isnil()) })
        t.set("get", LuaFn.z { value })
        t.set("or_else", LuaFn.m1(t) { if (value.isnil()) it else value })
        t.set("map", LuaFn.m1(t) { fn -> if (value.isnil()) t else wrapOptional(call1(fn, value)) })
        t.set("toJSON", LuaFn.z { if (value.isnil()) LuaValue.NIL else value })
        return t
    }

    private fun rangeType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.t { a, b ->
            val start = a.toint()
            val end = b.toint()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Range"))
            t.set("start", LuaValue.valueOf(start))
            t.set("finish", LuaValue.valueOf(end))
            t.set("contains", LuaFn.m1(t) {
                val n = it.toint()
                LuaValue.valueOf(n in min(start, end)..max(start, end))
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(abs(end - start) + 1) })
            t.set("to_table", LuaFn.z {
                val lo = min(start, end)
                val hi = max(start, end)
                LuaFn.fromJava((lo..hi).take(MAX).toList())
            })
            t.set("clamp", LuaFn.m1(t) {
                LuaValue.valueOf(it.toint().coerceIn(min(start, end), max(start, end)))
            })
            t
        })
        return type
    }

    private fun bitSetType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val bits = BooleanArray(64)
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("BitSet"))
            t.set("set", LuaFn.m1(t) {
                val i = it.toint().coerceIn(0, 63)
                bits[i] = true
                t
            })
            t.set("unset", LuaFn.m1(t) {
                val i = it.toint().coerceIn(0, 63)
                bits[i] = false
                t
            })
            t.set("toggle", LuaFn.m1(t) {
                val i = it.toint().coerceIn(0, 63)
                bits[i] = !bits[i]
                LuaValue.valueOf(bits[i])
            })
            t.set("test", LuaFn.m1(t) {
                val i = it.toint()
                LuaValue.valueOf(i in 0..63 && bits[i])
            })
            t.set("clear", LuaFn.z {
                bits.fill(false)
                t
            })
            t.set("count", LuaFn.z { LuaValue.valueOf(bits.count { flag -> flag }) })
            t.set("to_table", LuaFn.z {
                val on = ArrayList<Int>()
                bits.forEachIndexed { i, flag -> if (flag) on.add(i) }
                LuaFn.fromJava(on)
            })
            t
        })
        return type
    }

    private fun intervalType(): LuaTable {
        val type = LuaTable()
        type.set("parse", LuaFn.o { LuaValue.valueOf(parseInterval(it.tojstring()).toDouble()) })
        type.set("ms", LuaFn.o { LuaValue.valueOf(parseInterval(it.tojstring()) * 1000.0) })
        type.set("valid", LuaFn.o { LuaValue.valueOf(parseIntervalOrNull(it.tojstring()) != null) })
        return type
    }

    private fun csvType(): LuaTable {
        val type = LuaTable()
        type.set("parse", LuaFn.o { LuaFn.fromJava(parseCsv(it.tojstring())) })
        type.set("stringify", LuaFn.o { table ->
            val rows = arrayFrom(table).take(200)
            LuaValue.valueOf(
                rows.joinToString("\n") { row ->
                    arrayFrom(row).take(16).joinToString(",") { cell -> cell.tojstring().replace(",", " ") }
                },
            )
        })
        type.set("row", LuaFn.o {
            val rows = parseCsv(it.tojstring())
            LuaFn.fromJava(rows.firstOrNull().orEmpty())
        })
        return type
    }

    private fun stopwatchType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            var start = System.nanoTime()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Stopwatch"))
            t.set("reset", LuaFn.z {
                start = System.nanoTime()
                t
            })
            t.set("elapsed_ms", LuaFn.z { LuaValue.valueOf((System.nanoTime() - start) / 1_000_000.0) })
            t.set("elapsed", LuaFn.z { LuaValue.valueOf((System.nanoTime() - start) / 1_000_000_000.0) })
            t
        })
        return type
    }

    private fun memoType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val cache = LinkedHashMap<String, LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Memo"))
            t.set("get", LuaFn.m1(t) { cache[it.tojstring()] ?: LuaValue.NIL })
            t.set("set", LuaFn.m2(t) { key, value ->
                val k = key.tojstring().take(80)
                if (cache.size < 64 || cache.containsKey(k)) cache[k] = value
                t
            })
            t.set("compute", LuaFn.m2(t) { key, fn ->
                val k = key.tojstring().take(80)
                cache[k] ?: run {
                    val computed = call1(fn, key)
                    if (cache.size < 64) cache[k] = computed
                    computed
                }
            })
            t.set("clear", LuaFn.z {
                cache.clear()
                t
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(cache.size) })
            t
        })
        return type
    }

    private fun registryType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val items = LinkedHashMap<String, LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Registry"))
            t.set("register", LuaFn.m2(t) { name, value ->
                if (items.size < MAX) items[name.tojstring().take(80)] = value
                t
            })
            t.set("get", LuaFn.m1(t) { items[it.tojstring()] ?: LuaValue.NIL })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(items.containsKey(it.tojstring())) })
            t.set("keys", LuaFn.z { LuaFn.fromJava(items.keys.toList()) })
            t.set("clear", LuaFn.z {
                items.clear()
                t
            })
            t
        })
        return type
    }

    private fun ringType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { cap ->
            val capacity = cap.optint(16).coerceIn(1, MAX)
            val items = ArrayDeque<LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("RingBuffer"))
            t.set("capacity", LuaFn.z { LuaValue.valueOf(capacity) })
            t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
            t.set("push", LuaFn.m1(t) {
                if (items.size >= capacity) items.removeFirst()
                items.addLast(it)
                t
            })
            t.set("to_table", LuaFn.z { LuaFn.fromJava(items.map { luaToJava(it) }) })
            t.set("clear", LuaFn.z {
                items.clear()
                t
            })
            t
        })
        return type
    }

    private fun globType(): LuaTable {
        val type = LuaTable()
        type.set("match", LuaFn.t { pattern, value -> LuaValue.valueOf(glob(pattern.tojstring(), value.tojstring())) })
        type.set("filter", LuaFn.t { list, pattern ->
            val p = pattern.tojstring()
            LuaFn.fromJava(arrayFrom(list).map { it.tojstring() }.filter { glob(p, it) }.take(MAX))
        })
        return type
    }

    private fun formBuilderType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { title -> wrapForm(luna, title.optjstring("Settings"), mutableListOf()) })
        return type
    }

    private fun wrapForm(luna: LuaTable, title: String, items: MutableList<LuaValue>): LuaTable {
        val ui = luna.get("ui")
        val t = LuaTable()
        t.set("__kind", LuaValue.valueOf("FormBuilder"))
        fun add(node: LuaValue): LuaTable {
            if (items.size < PluginLimits.MAX_UI_ITEMS) items.add(node)
            return t
        }
        fun control(name: String) = LuaFn.m1(t) { add(call1(ui.get(name), it)) }
        t.set("note", control("note"))
        t.set("heading", control("heading"))
        t.set("divider", LuaFn.z { add(call0(ui.get("divider"))) })
        t.set("spacer", LuaFn.z { add(call0(ui.get("spacer"))) })
        t.set("switch", control("switch"))
        t.set("checkbox", control("checkbox"))
        t.set("text", control("text"))
        t.set("textarea", control("textarea"))
        t.set("number", control("number"))
        t.set("select", control("select"))
        t.set("slider", control("slider"))
        t.set("button", control("button"))
        t.set("link", control("link"))
        t.set("alert", control("alert"))
        t.set("badge", control("badge"))
        t.set("kv", control("kv"))
        t.set("progress", control("progress"))
        t.set("code", control("code"))
        t.set("stat", control("stat"))
        t.set("list_item", control("list_item"))
        t.set("item", control("list_item"))
        t.set("empty", control("empty"))
        t.set("chips", control("chips"))
        t.set("quote", control("quote"))
        t.set("fold", control("fold"))
        t.set("steps", control("steps"))
        t.set("timeline", control("timeline"))
        t.set("score", control("score"))
        t.set("compare", control("compare"))
        t.set("faq", control("faq"))
        t.set("status", control("status"))
        t.set("add", LuaFn.m1(t) { add(it) })
        t.set("build", LuaFn.z {
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
            page
        })
        return t
    }

    private fun wizardBuilderType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { title ->
            val heading = title.optjstring("Setup")
            val steps = mutableListOf<LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("WizardBuilder"))
            t.set("step", LuaFn.m2(t) { name, form ->
                val section = LuaTable()
                section.set("type", LuaValue.valueOf("section"))
                section.set("title", LuaValue.valueOf(name.tojstring().take(80)))
                val built = if (form.istable() && form.get("build").isfunction()) call0(form.get("build")) else form
                val items = when {
                    built.istable() && built.get("sections").istable() ->
                        built.get("sections").checktable().get(1).get("items")
                    built.istable() -> built.get("items")
                    else -> LuaTable()
                }
                section.set("items", if (items.istable()) items else LuaTable())
                if (steps.size < PluginLimits.MAX_UI_SECTIONS) steps.add(section)
                t
            })
            t.set("build", LuaFn.z {
                val page = LuaTable()
                page.set("type", LuaValue.valueOf("page"))
                page.set("title", LuaValue.valueOf(heading.take(80)))
                val sections = LuaTable()
                steps.forEachIndexed { i, step -> sections.set(i + 1, step) }
                page.set("sections", sections)
                page
            })
            t
        })
        return type
    }

    private fun tableBuilderType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { title ->
            val heading = title.optjstring("Table")
            val rows = mutableListOf<Pair<String, String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("TableBuilder"))
            t.set("row", LuaFn.m2(t) { k, v ->
                if (rows.size < PluginLimits.MAX_UI_ITEMS) {
                    rows.add(k.tojstring().take(80) to v.tojstring().take(200))
                }
                t
            })
            t.set("add", t.get("row"))
            t.set("build", LuaFn.z {
                val ui = luna.get("ui")
                val items = LuaTable()
                rows.forEachIndexed { i, (k, v) ->
                    val spec = LuaTable()
                    spec.set("key", LuaValue.valueOf(k))
                    spec.set("value", LuaValue.valueOf(v))
                    items.set(i + 1, call1(ui.get("kv"), spec))
                }
                val section = LuaTable()
                section.set("type", LuaValue.valueOf("section"))
                section.set("title", LuaValue.valueOf(heading.take(80)))
                section.set("items", items)
                val page = LuaTable()
                page.set("type", LuaValue.valueOf("page"))
                page.set("title", LuaValue.valueOf(heading.take(80)))
                val sections = LuaTable()
                sections.set(1, section)
                page.set("sections", sections)
                page
            })
            t
        })
        return type
    }

    private fun enums(): LuaTable = LuaFn.fromJava(
        mapOf(
            "VpnPhase" to mapOf("connected" to "connected", "disconnected" to "disconnected"),
            "LogLevel" to mapOf("info" to "info", "warn" to "warn", "error" to "error", "debug" to "debug"),
            "Tone" to mapOf("info" to "info", "warn" to "warn", "danger" to "danger", "success" to "success"),
            "RuleKind" to mapOf("sni" to "sni", "ip" to "ip"),
            "SettingKind" to mapOf(
                "switch" to "switch",
                "text" to "text",
                "number" to "number",
                "select" to "select",
                "slider" to "slider",
                "button" to "button",
                "stat" to "stat",
                "list_item" to "list_item",
                "fold" to "fold",
                "steps" to "steps",
                "timeline" to "timeline",
                "score" to "score",
                "compare" to "compare",
                "faq" to "faq",
                "status" to "status",
            ),
        ),
    ).checktable()

    private fun constants(): LuaTable = LuaFn.fromJava(
        mapOf(
            "API_LEVEL" to PLUGIN_API_LEVEL,
            "MAX_RULES" to PluginLimits.MAX_RULES,
            "MAX_TIMERS" to PluginLimits.MAX_TIMERS,
            "MAX_STORAGE_KEYS" to PluginLimits.MAX_STORAGE_KEYS,
            "MAX_UI_SECTIONS" to PluginLimits.MAX_UI_SECTIONS,
            "MAX_UI_ITEMS" to PluginLimits.MAX_UI_ITEMS,
            "MAX_COLLECTION" to MAX,
            "MAX_LISTENERS" to MAX_LISTENERS,
            "GITHUB_HOST" to GITHUB_HOST,
        ),
    ).checktable()

    private fun arrayFrom(value: LuaValue): MutableList<LuaValue> {
        if (!value.istable()) return mutableListOf()
        val table = value.checktable()
        val out = mutableListOf<LuaValue>()
        for (i in 1..table.length().coerceAtMost(MAX)) out.add(table.get(i))
        return out
    }

    private fun linkedSetFrom(value: LuaValue): LinkedHashSet<String> {
        return arrayFrom(value).map { it.tojstring() }.filter { it.isNotBlank() }.take(MAX).toCollection(LinkedHashSet())
    }

    private fun stringSet(value: LuaValue): Set<String> {
        if (value.istable() && value.get("to_table").isfunction()) {
            return arrayFrom(call0(value.get("to_table"))).map { it.tojstring() }.toSet()
        }
        return linkedSetFrom(value)
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

    private fun call0(fn: LuaValue): LuaValue = runCatching { fn.call() }.getOrDefault(LuaValue.NIL)

    private fun call1(fn: LuaValue, arg: LuaValue): LuaValue = runCatching { fn.call(arg) }.getOrDefault(LuaValue.NIL)

    private fun emit(listeners: List<LuaValue>, payload: LuaValue) {
        listeners.take(MAX_LISTENERS).forEach { runCatching { it.call(payload) } }
    }

    private fun luaToJava(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        value.istable() -> {
            val table = value.checktable()
            if (table.get(1) != LuaValue.NIL) arrayFrom(table).map { luaToJava(it) }
            else tableToStringMap(table)
        }
        else -> value.tojstring()
    }

    private fun domainMatches(host: String, pattern: String): Boolean {
        return host == pattern ||
            (pattern.startsWith("*.") && (host.endsWith("." + pattern.drop(2)) || host == pattern.drop(2)))
    }

    private fun parseCidr(raw: String): Pair<Int, Int>? {
        val parts = raw.trim().split('/', limit = 2)
        val ip = HostsFile.parseIpv4(parts[0]) ?: return null
        val bits = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 32) ?: 32
        val mask = if (bits == 0) 0 else (-1 shl (32 - bits))
        return (ip and mask) to mask
    }

    private fun maskBits(mask: Int): Int = if (mask == 0) 0 else Integer.bitCount(mask)

    private fun parseUrl(raw: String): Map<String, Any?> {
        val text = raw.trim()
        val schemeSplit = text.split("://", limit = 2)
        val scheme = if (schemeSplit.size == 2) schemeSplit[0].lowercase(Locale.US) else ""
        val rest = if (schemeSplit.size == 2) schemeSplit[1] else text
        val hashSplit = rest.split('#', limit = 2)
        val fragment = hashSplit.getOrNull(1).orEmpty()
        val querySplit = hashSplit[0].split('?', limit = 2)
        val query = parseQuery(querySplit.getOrNull(1).orEmpty())
        val pathSplit = querySplit[0].split('/', limit = 2)
        val host = pathSplit[0].substringBefore(':').lowercase(Locale.US)
        val path = if (pathSplit.size == 2) "/" + pathSplit[1] else "/"
        val valid = host.isNotBlank() && host.contains('.') && scheme in setOf("", "https", "http")
        return mapOf(
            "raw" to text.take(400),
            "scheme" to scheme,
            "host" to host,
            "path" to path.take(200),
            "query" to query,
            "fragment" to fragment.take(80),
            "valid" to valid,
            "github" to (host == GITHUB_HOST),
        )
    }

    private fun isGithub(raw: String): Boolean = parseUrl(raw)["host"] == GITHUB_HOST

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').take(32).mapNotNull { part ->
            val kv = part.split('=', limit = 2)
            val key = decode(kv[0]).take(80)
            if (key.isBlank()) null else key to decode(kv.getOrElse(1) { "" }).take(200)
        }.toMap()
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")

    private fun decode(value: String): String = runCatching {
        java.net.URLDecoder.decode(value.replace("+", "%20"), Charsets.UTF_8.name())
    }.getOrDefault(value)

    private fun renderTemplate(src: String, vars: Map<String, String>): String {
        var out = src.take(4000)
        vars.forEach { (k, v) ->
            out = out.replace("{$k}", v).replace("%{$k}", v).replace("{{$k}}", v)
        }
        return out.take(4000)
    }

    private fun levenshtein(left: String, right: String): Int {
        val a = left.take(64)
        val b = right.take(64)
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..b.length) {
                val cur = dp[j]
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
        }
        return dp[b.length]
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val maxLen = max(left.length, right.length).coerceAtLeast(1)
        return 1.0 - (levenshtein(left, right).toDouble() / maxLen)
    }

    private fun parseInterval(raw: String): Int = parseIntervalOrNull(raw) ?: 0

    private fun parseIntervalOrNull(raw: String): Int? {
        val text = raw.trim().lowercase(Locale.US)
        val match = Regex("^(\\d+)(ms|s|m|h)?$").matchEntire(text) ?: return null
        val n = match.groupValues[1].toIntOrNull() ?: return null
        if (n <= 0) return null
        val unit = match.groupValues[2]
        val seconds = when (unit) {
            "ms" -> (n / 1000).coerceAtLeast(1)
            "m" -> n * 60
            "h" -> n * 3600
            else -> n
        }
        return seconds.coerceIn(1, 120)
    }

    private fun parseCsv(raw: String): List<List<String>> {
        return raw.lines().take(200).map { line ->
            line.split(',').map { it.trim().trim('"') }.take(16)
        }.filter { row -> row.any { it.isNotEmpty() } }
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
}
