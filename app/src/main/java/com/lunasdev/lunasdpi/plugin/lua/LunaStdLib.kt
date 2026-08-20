package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.HostsFile
import java.security.MessageDigest
import java.util.Base64
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object LunaStdLib {
    fun install(luna: LuaTable) {
        val strings = stringMod()
        val tables = tableMod()
        val domains = domainMod()
        val ips = ipv4Mod()
        luna.set("string", strings)
        luna.set("str", strings)
        luna.set("table", tables)
        luna.set("list", tables)
        luna.set("json", jsonMod())
        luna.set("time", timeMod())
        luna.set("color", colorMod())
        luna.set("domain", domains)
        luna.set("ipv4", ips)
        luna.set("net", ips)
        val hashes = hashMod()
        luna.set("hash", hashes)
        luna.set("crypto", hashes)
        luna.set("util", utilMod())
        luna.set("semver", semverMod())
        luna.set("path", pathMod())
        luna.set("text", strings)
        luna.set("fmt", fmtMod())
    }

    private fun stringMod(): LuaTable = LuaFn.module(
        "trim" to LuaFn.o { LuaValue.valueOf(it.tojstring().trim()) },
        "trim_start" to LuaFn.o { LuaValue.valueOf(it.tojstring().trimStart()) },
        "trim_end" to LuaFn.o { LuaValue.valueOf(it.tojstring().trimEnd()) },
        "lower" to LuaFn.o { LuaValue.valueOf(it.tojstring().lowercase(Locale.US)) },
        "upper" to LuaFn.o { LuaValue.valueOf(it.tojstring().uppercase(Locale.US)) },
        "length" to LuaFn.o { LuaValue.valueOf(it.tojstring().length) },
        "is_empty" to LuaFn.o { LuaValue.valueOf(it.tojstring().isEmpty()) },
        "is_blank" to LuaFn.o { LuaValue.valueOf(it.tojstring().isBlank()) },
        "reverse" to LuaFn.o { LuaValue.valueOf(it.tojstring().reversed()) },
        "capitalize" to LuaFn.o { LuaValue.valueOf(it.tojstring().replaceFirstChar { ch -> ch.uppercaseChar() }) },
        "slug" to LuaFn.o { LuaValue.valueOf(slug(it.tojstring())) },
        "lines" to LuaFn.o { LuaFn.fromJava(it.tojstring().lines().take(400)) },
        "contains" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().contains(b.tojstring())) },
        "starts_with" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().startsWith(b.tojstring())) },
        "ends_with" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().endsWith(b.tojstring())) },
        "equals_ignore_case" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().equals(b.tojstring(), ignoreCase = true)) },
        "index_of" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().indexOf(b.tojstring()) + 1) },
        "last_index_of" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().lastIndexOf(b.tojstring()) + 1) },
        "count" to LuaFn.t { a, b ->
            val needle = b.tojstring()
            if (needle.isEmpty()) LuaValue.valueOf(0) else LuaValue.valueOf(a.tojstring().split(needle).size - 1)
        },
        "replace" to LuaFn.r { a, b, c -> LuaValue.valueOf(a.tojstring().replaceFirst(b.tojstring(), c.tojstring())) },
        "replace_all" to LuaFn.r { a, b, c -> LuaValue.valueOf(a.tojstring().replace(b.tojstring(), c.tojstring())) },
        "pad_start" to LuaFn.r { a, b, c ->
            val width = b.optint(0).coerceIn(0, 256)
            val pad = c.optjstring(" ").ifEmpty { " " }.first()
            LuaValue.valueOf(a.tojstring().padStart(width, pad))
        },
        "pad_end" to LuaFn.r { a, b, c ->
            val width = b.optint(0).coerceIn(0, 256)
            val pad = c.optjstring(" ").ifEmpty { " " }.first()
            LuaValue.valueOf(a.tojstring().padEnd(width, pad))
        },
        "truncate" to LuaFn.t { a, b ->
            val n = b.optint(0).coerceIn(0, 8_192)
            val raw = a.tojstring()
            LuaValue.valueOf(if (raw.length <= n) raw else raw.take(n))
        },
        "slice" to LuaFn.r { a, b, c ->
            val raw = a.tojstring()
            val start = (b.optint(1) - 1).coerceIn(0, raw.length)
            val end = if (c.isnil()) raw.length else c.optint(raw.length).coerceIn(start, raw.length)
            LuaValue.valueOf(raw.substring(start, end))
        },
        "char_at" to LuaFn.t { a, b ->
            val raw = a.tojstring()
            val i = b.optint(1) - 1
            if (i !in raw.indices) LuaValue.NIL else LuaValue.valueOf(raw[i].toString())
        },
        "split" to LuaFn.t { a, b ->
            val sep = b.optjstring("\n")
            LuaFn.fromJava(a.tojstring().split(sep).take(400))
        },
        "join" to LuaFn.t { a, b ->
            LuaValue.valueOf(LuaFn.stringList(a, 400).joinToString(b.optjstring(",")))
        },
        "strip_prefix" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().removePrefix(b.tojstring())) },
        "strip_suffix" to LuaFn.t { a, b -> LuaValue.valueOf(a.tojstring().removeSuffix(b.tojstring())) },
        "repeat" to LuaFn.t { a, b ->
            val n = b.optint(1).coerceIn(0, 64)
            LuaValue.valueOf(a.tojstring().take(128).repeat(n).take(4_096))
        },
        "title_case" to LuaFn.o { raw ->
            LuaValue.valueOf(raw.tojstring().split(Regex("\\s+")).joinToString(" ") { part ->
                part.lowercase(Locale.US).replaceFirstChar { ch -> ch.uppercaseChar() }
            })
        },
        "matches" to LuaFn.t { a, b -> LuaValue.valueOf(glob(b.tojstring(), a.tojstring())) },
        "camel" to LuaFn.o { LuaValue.valueOf(camel(it.tojstring())) },
        "snake" to LuaFn.o { LuaValue.valueOf(snake(it.tojstring())) },
        "kebab" to LuaFn.o { LuaValue.valueOf(kebab(it.tojstring())) },
        "pascal" to LuaFn.o { LuaValue.valueOf(pascal(it.tojstring())) },
        "words" to LuaFn.o { LuaFn.fromJava(words(it.tojstring())) },
        "indent" to LuaFn.t { a, b ->
            val pad = " ".repeat(b.optint(2).coerceIn(0, 16))
            LuaValue.valueOf(a.tojstring().lines().take(200).joinToString("\n") { line -> pad + line }.take(8_192))
        },
        "ellipsis" to LuaFn.t { a, b ->
            val n = b.optint(24).coerceIn(1, 8_192)
            val raw = a.tojstring()
            LuaValue.valueOf(if (raw.length <= n) raw else raw.take((n - 1).coerceAtLeast(0)) + "…")
        },
        "is_ascii" to LuaFn.o { LuaValue.valueOf(it.tojstring().all { ch -> ch.code < 128 }) },
        "collapse_ws" to LuaFn.o { LuaValue.valueOf(it.tojstring().replace(Regex("\\s+"), " ").trim()) },
        "quote" to LuaFn.o { LuaValue.valueOf("\"" + it.tojstring().replace("\"", "'").take(400) + "\"") },
        "interpolate" to LuaFn.t { src, vars ->
            var out = src.tojstring().take(4000)
            if (vars.istable()) {
                val table = vars.checktable()
                var k = table.next(LuaValue.NIL)
                var n = 0
                while (!k.arg1().isnil() && n < 64) {
                    val name = k.arg1().tojstring()
                    val value = k.arg(2).tojstring()
                    out = out.replace("{$name}", value).replace("%{$name}", value).replace("{{$name}}", value)
                    k = table.next(k.arg1())
                    n++
                }
            }
            LuaValue.valueOf(out.take(4000))
        },
        "levenshtein" to LuaFn.t { a, b -> LuaValue.valueOf(levenshtein(a.tojstring(), b.tojstring())) },
        "similarity" to LuaFn.t { a, b -> LuaValue.valueOf(similarity(a.tojstring(), b.tojstring())) },
    )

    private fun tableMod(): LuaTable = LuaFn.module(
        "size" to LuaFn.o { LuaValue.valueOf(seqSize(it.checktable())) },
        "is_empty" to LuaFn.o { LuaValue.valueOf(seqSize(it.checktable()) == 0) },
        "keys" to LuaFn.o { LuaFn.fromJava(mapKeys(it.checktable())) },
        "values" to LuaFn.o { values(it.checktable()) },
        "copy" to LuaFn.o { copyTable(it.checktable()) },
        "first" to LuaFn.o { it.checktable().get(1) },
        "last" to LuaFn.o {
            val table = it.checktable()
            table.get(seqSize(table))
        },
        "contains" to LuaFn.t { a, b ->
            val table = a.checktable()
            var i = 1
            while (i <= 256) {
                val item = table.get(i)
                if (item.isnil()) break
                if (item.eq_b(b)) return@t LuaValue.TRUE
                i++
            }
            LuaValue.FALSE
        },
        "index_of" to LuaFn.t { a, b ->
            val table = a.checktable()
            var i = 1
            while (i <= 256) {
                val item = table.get(i)
                if (item.isnil()) break
                if (item.eq_b(b)) return@t LuaValue.valueOf(i)
                i++
            }
            LuaValue.valueOf(0)
        },
        "slice" to LuaFn.r { a, b, c ->
            val src = a.checktable()
            val start = b.optint(1).coerceAtLeast(1)
            val end = c.optint(seqSize(src)).coerceAtLeast(start)
            val out = LuaTable()
            var n = 1
            for (i in start..min(end, 256)) {
                val item = src.get(i)
                if (item.isnil()) break
                out.set(n++, item)
            }
            out
        },
        "concat" to LuaFn.t { a, b ->
            val out = LuaTable()
            var n = 1
            listOf(a.checktable(), b.checktable()).forEach { src ->
                var i = 1
                while (n <= 256) {
                    val item = src.get(i)
                    if (item.isnil()) break
                    out.set(n++, item)
                    i++
                }
            }
            out
        },
        "unique" to LuaFn.o { raw ->
            val src = raw.checktable()
            val out = LuaTable()
            val seen = HashSet<String>()
            var n = 1
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                val key = item.tojstring()
                if (seen.add(key)) out.set(n++, item)
                i++
            }
            out
        },
        "reverse" to LuaFn.o { raw ->
            val src = raw.checktable()
            val n = seqSize(src)
            val out = LuaTable()
            for (i in 1..n) out.set(i, src.get(n - i + 1))
            out
        },
        "merge" to LuaFn.t { a, b ->
            val out = copyTable(a.checktable())
            val extra = b.checktable()
            extra.keys().forEach { key -> out.set(key, extra.get(key)) }
            out
        },
        "get" to LuaFn.t { a, b -> a.checktable().get(b) },
        "set" to LuaFn.r { a, b, c ->
            a.checktable().set(b, c)
            a
        },
        "remove" to LuaFn.t { a, b ->
            val table = a.checktable()
            val removed = table.get(b)
            table.set(b, LuaValue.NIL)
            removed
        },
        "insert" to LuaFn.t { a, b ->
            val table = a.checktable()
            table.set(seqSize(table) + 1, b)
            table
        },
        "pack" to LuaFn.v { args ->
            val out = LuaTable()
            for (i in 1..min(args.narg(), 64)) out.set(i, args.arg(i))
            out
        },
        "map" to LuaFn.t { a, b ->
            val src = a.checktable()
            val fn = b.checkfunction()
            val out = LuaTable()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                out.set(i, fn.call(item, LuaValue.valueOf(i)))
                i++
            }
            out
        },
        "filter" to LuaFn.t { a, b ->
            val src = a.checktable()
            val fn = b.checkfunction()
            val out = LuaTable()
            var i = 1
            var n = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (fn.call(item, LuaValue.valueOf(i)).toboolean()) out.set(n++, item)
                i++
            }
            out
        },
        "find" to LuaFn.t { a, b ->
            val src = a.checktable()
            val fn = b.checkfunction()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (fn.call(item, LuaValue.valueOf(i)).toboolean()) return@t item
                i++
            }
            LuaValue.NIL
        },
        "sort" to LuaFn.o { raw ->
            val src = raw.checktable()
            val items = ArrayList<String>()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                items.add(item.tojstring())
                i++
            }
            items.sort()
            LuaFn.fromJava(items)
        },
        "every" to LuaFn.t { a, b ->
            val src = a.checktable()
            val fn = b.checkfunction()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (!fn.call(item, LuaValue.valueOf(i)).toboolean()) return@t LuaValue.FALSE
                i++
            }
            LuaValue.TRUE
        },
        "some" to LuaFn.t { a, b ->
            val src = a.checktable()
            val fn = b.checkfunction()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (fn.call(item, LuaValue.valueOf(i)).toboolean()) return@t LuaValue.TRUE
                i++
            }
            LuaValue.FALSE
        },
        "reduce" to LuaFn.r { a, b, c ->
            val src = a.checktable()
            val fn = c.checkfunction()
            var acc = b
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                acc = fn.call(acc, item, LuaValue.valueOf(i))
                i++
            }
            acc
        },
        "zip" to LuaFn.t { a, b ->
            val left = a.checktable()
            val right = b.checktable()
            val out = LuaTable()
            var i = 1
            while (i <= 256) {
                val l = left.get(i)
                val r = right.get(i)
                if (l.isnil() && r.isnil()) break
                val pair = LuaTable()
                pair.set(1, l)
                pair.set(2, r)
                out.set(i, pair)
                i++
            }
            out
        },
        "flatten" to LuaFn.o { raw ->
            val src = raw.checktable()
            val out = LuaTable()
            var n = 1
            var i = 1
            while (i <= 256 && n <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (item.istable()) {
                    val inner = item.checktable()
                    var j = 1
                    while (j <= 256 && n <= 256) {
                        val nested = inner.get(j)
                        if (nested.isnil()) break
                        out.set(n++, nested)
                        j++
                    }
                } else {
                    out.set(n++, item)
                }
                i++
            }
            out
        },
        "chunk" to LuaFn.t { a, b ->
            val src = a.checktable()
            val size = b.optint(2).coerceIn(1, 256)
            val out = LuaTable()
            var chunkIndex = 1
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                val chunk = LuaTable()
                var n = 1
                while (n <= size && i <= 256) {
                    val cur = src.get(i)
                    if (cur.isnil()) break
                    chunk.set(n++, cur)
                    i++
                }
                out.set(chunkIndex++, chunk)
            }
            out
        },
        "sum" to LuaFn.o {
            val src = it.checktable()
            var total = 0.0
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (item.isnumber()) total += item.todouble()
                i++
            }
            LuaValue.valueOf(total)
        },
        "average" to LuaFn.o {
            val src = it.checktable()
            var total = 0.0
            var n = 0
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (item.isnumber()) {
                    total += item.todouble()
                    n++
                }
                i++
            }
            LuaValue.valueOf(if (n == 0) 0.0 else total / n)
        },
        "take" to LuaFn.t { a, b ->
            val src = a.checktable()
            val n = b.optint(1).coerceIn(0, 256)
            val out = LuaTable()
            for (i in 1..n) {
                val item = src.get(i)
                if (item.isnil()) break
                out.set(i, item)
            }
            out
        },
        "drop" to LuaFn.t { a, b ->
            val src = a.checktable()
            val skip = b.optint(1).coerceIn(0, 256)
            val out = LuaTable()
            var n = 1
            var i = skip + 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                out.set(n++, item)
                i++
            }
            out
        },
        "shuffle" to LuaFn.o {
            val src = it.checktable()
            val items = ArrayList<LuaValue>()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                items.add(item)
                i++
            }
            items.shuffle()
            LuaFn.fromJava(items)
        },
        "sample" to LuaFn.t { a, b ->
            val src = a.checktable()
            val items = ArrayList<LuaValue>()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                items.add(item)
                i++
            }
            LuaFn.fromJava(items.shuffled().take(b.optint(1).coerceIn(1, 256)))
        },
        "frequencies" to LuaFn.o {
            val src = it.checktable()
            val counts = LinkedHashMap<String, Int>()
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                val key = item.tojstring()
                counts[key] = (counts[key] ?: 0) + 1
                i++
            }
            LuaFn.fromJava(counts)
        },
        "partition" to LuaFn.t { a, b ->
            val src = a.checktable()
            val fn = b.checkfunction()
            val yes = LuaTable()
            val no = LuaTable()
            var yi = 1
            var ni = 1
            var i = 1
            while (i <= 256) {
                val item = src.get(i)
                if (item.isnil()) break
                if (fn.call(item, LuaValue.valueOf(i)).toboolean()) yes.set(yi++, item) else no.set(ni++, item)
                i++
            }
            val out = LuaTable()
            out.set(1, yes)
            out.set(2, no)
            out
        },
    )

    private fun jsonMod(): LuaTable = LuaFn.module(
        "encode" to LuaFn.o { LuaValue.valueOf(jsonEncode(it).take(32_768)) },
        "decode" to LuaFn.o { jsonDecode(it.checkjstring()) },
        "is_array" to LuaFn.o { LuaValue.valueOf(it.istable() && seqSize(it.checktable()) > 0 && it.checktable().get("id").isnil()) },
        "is_object" to LuaFn.o { LuaValue.valueOf(it.istable()) },
        "stringify" to LuaFn.o { LuaValue.valueOf(jsonEncode(it).take(32_768)) },
        "get" to LuaFn.t { a, b ->
            val decoded = jsonDecode(if (a.isstring()) a.checkjstring() else jsonEncode(a))
            if (!decoded.istable()) LuaValue.NIL else decoded.checktable().get(b)
        },
    )

    private fun timeMod(): LuaTable = LuaFn.module(
        "now" to LuaFn.z { LuaValue.valueOf((System.currentTimeMillis() / 1000L).toDouble()) },
        "now_ms" to LuaFn.z { LuaValue.valueOf(System.currentTimeMillis().toDouble()) },
        "iso" to LuaFn.z { LuaValue.valueOf(iso(System.currentTimeMillis())) },
        "year" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.YEAR)) },
        "month" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.MONTH) + 1) },
        "day" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.DAY_OF_MONTH)) },
        "hour" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.HOUR_OF_DAY)) },
        "minute" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.MINUTE)) },
        "second" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.SECOND)) },
        "weekday" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.DAY_OF_WEEK)) },
        "utc_offset" to LuaFn.z { LuaValue.valueOf(cal().get(Calendar.ZONE_OFFSET) / 1000) },
        "since" to LuaFn.o { LuaValue.valueOf((System.currentTimeMillis() / 1000.0) - it.todouble()) },
        "until" to LuaFn.o { LuaValue.valueOf(it.todouble() - (System.currentTimeMillis() / 1000.0)) },
        "is_future" to LuaFn.o { LuaValue.valueOf(it.todouble() > System.currentTimeMillis() / 1000.0) },
        "is_past" to LuaFn.o { LuaValue.valueOf(it.todouble() < System.currentTimeMillis() / 1000.0) },
        "add_seconds" to LuaFn.t { a, b -> LuaValue.valueOf(a.todouble() + b.todouble()) },
        "start_of_day" to LuaFn.z {
            val c = cal()
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            LuaValue.valueOf(c.timeInMillis / 1000.0)
        },
        "format" to LuaFn.t { a, b ->
            val ms = (a.todouble() * 1000).toLong()
            val pattern = b.optjstring("iso")
            if (pattern == "iso") LuaValue.valueOf(iso(ms)) else LuaValue.valueOf(iso(ms))
        },
    )

    private fun colorMod(): LuaTable = LuaFn.module(
        "is_hex" to LuaFn.o { LuaValue.valueOf(parseHex(it.tojstring()) != null) },
        "parse" to LuaFn.o {
            val rgb = parseHex(it.tojstring()) ?: return@o LuaValue.NIL
            LuaFn.fromJava(mapOf("r" to rgb[0], "g" to rgb[1], "b" to rgb[2], "hex" to hex(rgb[0], rgb[1], rgb[2])))
        },
        "hex" to LuaFn.r { a, b, c ->
            LuaValue.valueOf(hex(a.optint(0), b.optint(0), c.optint(0)))
        },
        "clamp" to LuaFn.o { LuaValue.valueOf(it.optint(0).coerceIn(0, 255)) },
        "from_rgb" to LuaFn.r { a, b, c -> LuaValue.valueOf(hex(a.optint(0), b.optint(0), c.optint(0))) },
        "luma" to LuaFn.o {
            val rgb = parseHex(it.tojstring()) ?: return@o LuaValue.NIL
            LuaValue.valueOf((0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255.0)
        },
        "lighten" to LuaFn.t { a, b -> mix(a.tojstring(), "#ffffff", b.optdouble(0.2)) },
        "darken" to LuaFn.t { a, b -> mix(a.tojstring(), "#000000", b.optdouble(0.2)) },
        "mix" to LuaFn.r { a, b, c -> mix(a.tojstring(), b.tojstring(), c.optdouble(0.5)) },
        "contrast" to LuaFn.t { a, b ->
            val left = parseHex(a.tojstring()) ?: return@t LuaValue.NIL
            val right = parseHex(b.tojstring()) ?: return@t LuaValue.NIL
            val d = abs(left[0] - right[0]) + abs(left[1] - right[1]) + abs(left[2] - right[2])
            LuaValue.valueOf(d / (255.0 * 3))
        },
        "css" to LuaFn.o {
            val rgb = parseHex(it.tojstring()) ?: return@o LuaValue.NIL
            LuaValue.valueOf("rgb(${rgb[0]}, ${rgb[1]}, ${rgb[2]})")
        },
        "to_rgb" to LuaFn.o {
            val rgb = parseHex(it.tojstring()) ?: return@o LuaValue.NIL
            LuaFn.fromJava(listOf(rgb[0], rgb[1], rgb[2]))
        },
    )

    private fun domainMod(): LuaTable = LuaFn.module(
        "normalize" to LuaFn.o { LuaValue.valueOf(DomainValidator.normalize(it.tojstring())) },
        "valid" to LuaFn.o { LuaValue.valueOf(DomainValidator.isValidPattern(it.tojstring()) && !it.tojstring().startsWith("*.")) },
        "valid_pattern" to LuaFn.o { LuaValue.valueOf(DomainValidator.isValidPattern(it.tojstring())) },
        "is_wildcard" to LuaFn.o { LuaValue.valueOf(DomainValidator.normalize(it.tojstring()).startsWith("*.")) },
        "reject_reason" to LuaFn.o {
            val reason = DomainValidator.rejectReason(it.tojstring())
            if (reason == null) LuaValue.NIL else LuaValue.valueOf(reason)
        },
        "labels" to LuaFn.o {
            val n = DomainValidator.normalize(it.tojstring()).removePrefix("*.")
            LuaFn.fromJava(n.split('.').filter { part -> part.isNotEmpty() })
        },
        "root" to LuaFn.o {
            val n = DomainValidator.normalize(it.tojstring()).removePrefix("*.")
            val labels = n.split('.')
            if (labels.size < 2) LuaValue.valueOf(n) else LuaValue.valueOf(labels.takeLast(2).joinToString("."))
        },
        "parent" to LuaFn.o {
            val n = DomainValidator.normalize(it.tojstring()).removePrefix("*.")
            val idx = n.indexOf('.')
            if (idx < 0) LuaValue.NIL else LuaValue.valueOf(n.substring(idx + 1))
        },
        "join" to LuaFn.t { a, b ->
            LuaValue.valueOf(listOf(a.tojstring(), b.tojstring()).joinToString(".").trim('.'))
        },
        "suffix" to LuaFn.o {
            val n = DomainValidator.normalize(it.tojstring())
            LuaValue.valueOf(n.substringAfterLast('.', n))
        },
        "matches" to LuaFn.t { a, b ->
            val host = DomainValidator.normalize(a.tojstring())
            val pattern = DomainValidator.normalize(b.tojstring())
            LuaValue.valueOf(
                host == pattern ||
                    (pattern.startsWith("*.") && (host.endsWith("." + pattern.drop(2)) || host == pattern.drop(2))),
            )
        },
        "registrable" to LuaFn.o {
            val n = DomainValidator.normalize(it.tojstring()).removePrefix("*.")
            val labels = n.split('.')
            if (labels.size < 2) LuaValue.valueOf(n) else LuaValue.valueOf(labels.takeLast(2).joinToString("."))
        },
    )

    private fun ipv4Mod(): LuaTable = LuaFn.module(
        "parse" to LuaFn.o {
            val packed = HostsFile.parseIpv4(it.tojstring()) ?: return@o LuaValue.NIL
            LuaValue.valueOf((packed.toLong() and 0xFFFFFFFFL).toDouble())
        },
        "format" to LuaFn.o { LuaValue.valueOf(HostsFile.formatIpv4(it.toint())) },
        "valid" to LuaFn.o { LuaValue.valueOf(HostsFile.parseIpv4(it.tojstring()) != null) },
        "allowed_host" to LuaFn.o {
            val packed = HostsFile.parseIpv4(it.tojstring()) ?: return@o LuaValue.FALSE
            LuaValue.valueOf(HostsFile.isAllowedIpv4(packed))
        },
        "octets" to LuaFn.o {
            val packed = HostsFile.parseIpv4(it.tojstring()) ?: return@o LuaValue.NIL
            val a = packed ushr 24 and 0xFF
            val b = packed ushr 16 and 0xFF
            val c = packed ushr 8 and 0xFF
            val d = packed and 0xFF
            LuaFn.fromJava(listOf(a, b, c, d))
        },
        "from_octets" to LuaFn.o {
            val list = LuaFn.stringList(it, 4).mapNotNull { part -> part.toIntOrNull() }
            if (list.size != 4 || list.any { n -> n !in 0..255 }) LuaValue.NIL
            else LuaValue.valueOf(HostsFile.formatIpv4((list[0] shl 24) or (list[1] shl 16) or (list[2] shl 8) or list[3]))
        },
        "private" to LuaFn.o { LuaValue.valueOf(classOf(it.tojstring()) == "private") },
        "loopback" to LuaFn.o { LuaValue.valueOf(classOf(it.tojstring()) == "loopback") },
        "link_local" to LuaFn.o { LuaValue.valueOf(classOf(it.tojstring()) == "link_local") },
        "multicast" to LuaFn.o { LuaValue.valueOf(classOf(it.tojstring()) == "multicast") },
        "public" to LuaFn.o { LuaValue.valueOf(classOf(it.tojstring()) == "public") },
        "tun_range" to LuaFn.o { LuaValue.valueOf(classOf(it.tojstring()) == "tun") },
        "broadcast" to LuaFn.o { LuaValue.valueOf(it.tojstring().trim() == "255.255.255.255") },
        "equal" to LuaFn.t { a, b ->
            LuaValue.valueOf(HostsFile.parseIpv4(a.tojstring()) != null && HostsFile.parseIpv4(a.tojstring()) == HostsFile.parseIpv4(b.tojstring()))
        },
        "to_int" to LuaFn.o {
            val packed = HostsFile.parseIpv4(it.tojstring()) ?: return@o LuaValue.NIL
            LuaValue.valueOf((packed.toLong() and 0xFFFFFFFFL).toDouble())
        },
        "from_int" to LuaFn.o { LuaValue.valueOf(HostsFile.formatIpv4(it.toint())) },
        "in_cidr" to LuaFn.r { a, b, c ->
            val ip = HostsFile.parseIpv4(a.tojstring()) ?: return@r LuaValue.FALSE
            val net = HostsFile.parseIpv4(b.tojstring()) ?: return@r LuaValue.FALSE
            val bits = c.optint(32).coerceIn(0, 32)
            val mask = if (bits == 0) 0 else (-1 shl (32 - bits))
            LuaValue.valueOf((ip and mask) == (net and mask))
        },
        "cidr_contains" to LuaFn.r { a, b, c ->
            val net = HostsFile.parseIpv4(a.tojstring()) ?: return@r LuaValue.FALSE
            val ip = HostsFile.parseIpv4(b.tojstring()) ?: return@r LuaValue.FALSE
            val bits = c.optint(32).coerceIn(0, 32)
            val mask = if (bits == 0) 0 else (-1 shl (32 - bits))
            LuaValue.valueOf((ip and mask) == (net and mask))
        },
    )

    private fun hashMod(): LuaTable = LuaFn.module(
        "sha256" to LuaFn.o { LuaValue.valueOf(digest("SHA-256", it.tojstring().toByteArray())) },
        "sha256_hex" to LuaFn.o { LuaValue.valueOf(digest("SHA-256", it.tojstring().toByteArray())) },
        "hex_encode" to LuaFn.o { LuaValue.valueOf(it.tojstring().toByteArray().joinToString("") { b -> "%02x".format(b) }.take(8_192)) },
        "hex_decode" to LuaFn.o {
            val hex = it.tojstring().filter { ch -> ch.isLetterOrDigit() }
            if (hex.length % 2 != 0 || hex.length > 8_192) return@o LuaValue.NIL
            val bytes = hex.chunked(2).map { part -> part.toInt(16).toByte() }.toByteArray()
            LuaValue.valueOf(String(bytes, Charsets.ISO_8859_1))
        },
        "base64_encode" to LuaFn.o {
            LuaValue.valueOf(Base64.getEncoder().encodeToString(it.tojstring().toByteArray().copyOf(min(it.tojstring().toByteArray().size, 24_576))))
        },
        "base64_decode" to LuaFn.o {
            runCatching { LuaValue.valueOf(String(Base64.getDecoder().decode(it.tojstring().take(32_768)))) }.getOrElse { LuaValue.NIL }
        },
    )

    private fun utilMod(): LuaTable = LuaFn.module(
        "guid" to LuaFn.z { LuaValue.valueOf(UUID.randomUUID().toString()) },
        "clamp" to LuaFn.r { a, b, c -> LuaValue.valueOf(a.todouble().coerceIn(b.todouble(), c.todouble())) },
        "lerp" to LuaFn.r { a, b, c -> LuaValue.valueOf(a.todouble() + (b.todouble() - a.todouble()) * c.todouble()) },
        "round" to LuaFn.o { LuaValue.valueOf(aRound(it.todouble())) },
        "sign" to LuaFn.o { LuaValue.valueOf(kotlin.math.sign(it.todouble())) },
        "bool" to LuaFn.o { LuaValue.valueOf(it.toboolean()) },
        "coalesce" to LuaFn.t { a, b -> if (a.isnil() || (a.isstring() && a.tojstring().isEmpty())) b else a },
        "typeof" to LuaFn.o { LuaValue.valueOf(it.typename()) },
        "is_number" to LuaFn.o { LuaValue.valueOf(it.isnumber()) },
        "is_string" to LuaFn.o { LuaValue.valueOf(it.isstring()) },
        "is_table" to LuaFn.o { LuaValue.valueOf(it.istable()) },
        "is_function" to LuaFn.o { LuaValue.valueOf(it.isfunction()) },
        "inspect" to LuaFn.o { LuaValue.valueOf(jsonEncode(it).take(1_000)) },
        "utf8_len" to LuaFn.o { LuaValue.valueOf(it.tojstring().length) },
        "bytes_len" to LuaFn.o { LuaValue.valueOf(it.tojstring().toByteArray().size) },
        "identity" to LuaFn.o { it },
        "min" to LuaFn.t { a, b -> LuaValue.valueOf(min(a.todouble(), b.todouble())) },
        "max" to LuaFn.t { a, b -> LuaValue.valueOf(max(a.todouble(), b.todouble())) },
        "format_bytes" to LuaFn.o { LuaValue.valueOf(formatBytes(it.todouble())) },
        "format_duration" to LuaFn.o { LuaValue.valueOf(formatDuration(it.todouble())) },
        "format_percent" to LuaFn.o { LuaValue.valueOf(formatPercent(it.todouble())) },
        "compact" to LuaFn.o { LuaValue.valueOf(compactNumber(it.todouble())) },
        "human_join" to LuaFn.t { a, b ->
            LuaValue.valueOf(humanJoin(LuaFn.stringList(a, 32), b.optjstring(", ")))
        },
        "bullet_lines" to LuaFn.o { LuaValue.valueOf(LuaFn.stringList(it, 32).joinToString("\n") { line -> "• $line" }) },
    )

    private fun semverMod(): LuaTable = LuaFn.module(
        "parse" to LuaFn.o {
            val parts = parseVer(it.tojstring())
            LuaFn.fromJava(mapOf("major" to parts[0], "minor" to parts[1], "patch" to parts[2]))
        },
        "major" to LuaFn.o { LuaValue.valueOf(parseVer(it.tojstring())[0]) },
        "minor" to LuaFn.o { LuaValue.valueOf(parseVer(it.tojstring())[1]) },
        "patch" to LuaFn.o { LuaValue.valueOf(parseVer(it.tojstring())[2]) },
        "compare" to LuaFn.t { a, b -> LuaValue.valueOf(compareVer(a.tojstring(), b.tojstring())) },
        "gt" to LuaFn.t { a, b -> LuaValue.valueOf(compareVer(a.tojstring(), b.tojstring()) > 0) },
        "gte" to LuaFn.t { a, b -> LuaValue.valueOf(compareVer(a.tojstring(), b.tojstring()) >= 0) },
        "lt" to LuaFn.t { a, b -> LuaValue.valueOf(compareVer(a.tojstring(), b.tojstring()) < 0) },
        "lte" to LuaFn.t { a, b -> LuaValue.valueOf(compareVer(a.tojstring(), b.tojstring()) <= 0) },
        "eq" to LuaFn.t { a, b -> LuaValue.valueOf(compareVer(a.tojstring(), b.tojstring()) == 0) },
        "valid" to LuaFn.o { LuaValue.valueOf(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(it.tojstring().trim())) },
        "format" to LuaFn.r { a, b, c -> LuaValue.valueOf("${a.optint(0)}.${b.optint(0)}.${c.optint(0)}") },
    )

    private fun pathMod(): LuaTable = LuaFn.module(
        "basename" to LuaFn.o { LuaValue.valueOf(it.tojstring().substringAfterLast('/').substringAfterLast('\\')) },
        "dirname" to LuaFn.o {
            val raw = it.tojstring()
            val cut = max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'))
            if (cut < 0) LuaValue.valueOf(".") else LuaValue.valueOf(raw.substring(0, cut).ifBlank { "/" })
        },
        "extname" to LuaFn.o {
            val name = it.tojstring().substringAfterLast('/').substringAfterLast('\\')
            val dot = name.lastIndexOf('.')
            if (dot <= 0) LuaValue.valueOf("") else LuaValue.valueOf(name.substring(dot))
        },
        "join" to LuaFn.t { a, b ->
            LuaValue.valueOf(listOf(a.tojstring().trimEnd('/', '\\'), b.tojstring().trimStart('/', '\\')).joinToString("/"))
        },
        "posix" to LuaFn.o { LuaValue.valueOf(it.tojstring().replace('\\', '/')) },
        "is_lua" to LuaFn.o { LuaValue.valueOf(it.tojstring().endsWith(".lua", ignoreCase = true)) },
        "is_json" to LuaFn.o { LuaValue.valueOf(it.tojstring().endsWith(".json", ignoreCase = true)) },
        "stem" to LuaFn.o {
            val name = it.tojstring().substringAfterLast('/').substringAfterLast('\\')
            LuaValue.valueOf(name.substringBeforeLast('.', name))
        },
    )

    private fun fmtMod(): LuaTable = LuaFn.module(
        "bytes" to LuaFn.o { LuaValue.valueOf(formatBytes(it.todouble())) },
        "duration" to LuaFn.o { LuaValue.valueOf(formatDuration(it.todouble())) },
        "percent" to LuaFn.o { LuaValue.valueOf(formatPercent(it.todouble())) },
        "compact" to LuaFn.o { LuaValue.valueOf(compactNumber(it.todouble())) },
        "uptime" to LuaFn.o { LuaValue.valueOf(formatDuration(it.todouble())) },
        "number" to LuaFn.o { LuaValue.valueOf(it.todouble().toString()) },
        "join" to LuaFn.t { a, b -> LuaValue.valueOf(humanJoin(LuaFn.stringList(a, 32), b.optjstring(", "))) },
        "bullets" to LuaFn.o { LuaValue.valueOf(LuaFn.stringList(it, 32).joinToString("\n") { line -> "• $line" }) },
    )

    private fun formatBytes(value: Double): String {
        val sign = if (value < 0) "-" else ""
        var n = abs(value)
        val units = arrayOf("B", "KB", "MB", "GB")
        var i = 0
        while (n >= 1024.0 && i < units.lastIndex) {
            n /= 1024.0
            i++
        }
        return if (i == 0) "$sign${n.toLong()} B" else "$sign${"%.1f".format(n)} ${units[i]}"
    }

    private fun formatDuration(seconds: Double): String {
        val total = abs(seconds).toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(h, m, s)
            m > 0 -> "%d:%02d".format(m, s)
            else -> "${s}s"
        }
    }

    private fun formatPercent(value: Double): String {
        val amount = if (value in 0.0..1.0) value * 100.0 else value
        return "${"%.0f".format(amount.coerceIn(0.0, 100.0))}%"
    }

    private fun compactNumber(value: Double): String {
        val n = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            n >= 1_000_000 -> "$sign${"%.1f".format(n / 1_000_000.0)}M"
            n >= 1_000 -> "$sign${"%.1f".format(n / 1_000.0)}K"
            else -> "$sign${n.toLong()}"
        }
    }

    private fun humanJoin(parts: List<String>, sep: String): String {
        return when (parts.size) {
            0 -> ""
            1 -> parts[0]
            2 -> "${parts[0]} and ${parts[1]}"
            else -> parts.dropLast(1).joinToString(sep) + ", and " + parts.last()
        }
    }

    private fun parseVer(raw: String): IntArray {
        val parts = raw.trim().split('.')
        return IntArray(3) { index -> parts.getOrNull(index)?.toIntOrNull()?.coerceIn(0, 999) ?: 0 }
    }

    private fun compareVer(left: String, right: String): Int {
        val a = parseVer(left)
        val b = parseVer(right)
        for (i in 0 until 3) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return 0
    }

    private fun aRound(value: Double): Int = value.roundToInt()

    private fun seqSize(table: LuaTable): Int {
        var i = 1
        while (i <= 256) {
            if (table.get(i).isnil()) return i - 1
            i++
        }
        return 256
    }

    private fun mapKeys(table: LuaTable): List<String> {
        val out = ArrayList<String>()
        table.keys().forEach { key -> out.add(key.tojstring()) }
        return out.take(256)
    }

    private fun values(table: LuaTable): LuaTable {
        val out = LuaTable()
        var n = 1
        table.keys().forEach { key ->
            if (n <= 256) out.set(n++, table.get(key))
        }
        return out
    }

    private fun copyTable(src: LuaTable): LuaTable {
        val out = LuaTable()
        src.keys().forEach { key -> out.set(key, src.get(key)) }
        return out
    }

    private fun slug(raw: String): String {
        return raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(80)
    }

    private fun words(raw: String): List<String> {
        val spaced = raw
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
        return spaced.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.take(40)
    }

    private fun camel(raw: String): String {
        return words(raw).mapIndexed { index, part ->
            val lower = part.lowercase(Locale.US)
            if (index == 0) lower else lower.replaceFirstChar { it.uppercaseChar() }
        }.joinToString("")
    }

    private fun pascal(raw: String): String {
        return words(raw).joinToString("") { part ->
            part.lowercase(Locale.US).replaceFirstChar { it.uppercaseChar() }
        }
    }

    private fun snake(raw: String): String = words(raw).joinToString("_") { it.lowercase(Locale.US) }.take(80)

    private fun kebab(raw: String): String = words(raw).joinToString("-") { it.lowercase(Locale.US) }.take(80)

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

    private fun cal(): Calendar = Calendar.getInstance()

    private fun iso(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return "%04d-%02d-%02dT%02d:%02d:%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND),
        )
    }

    private fun parseHex(raw: String): IntArray? {
        val hex = raw.trim().removePrefix("#")
        if (hex.length != 6 || hex.any { !it.isLetterOrDigit() }) return null
        return runCatching {
            intArrayOf(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
        }.getOrNull()
    }

    private fun hex(r: Int, g: Int, b: Int): String {
        return "#%02x%02x%02x".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun mix(left: String, right: String, t: Double): LuaValue {
        val a = parseHex(left) ?: return LuaValue.NIL
        val b = parseHex(right) ?: return LuaValue.NIL
        val w = t.coerceIn(0.0, 1.0)
        return LuaValue.valueOf(
            hex(
                (a[0] + (b[0] - a[0]) * w).roundToInt(),
                (a[1] + (b[1] - a[1]) * w).roundToInt(),
                (a[2] + (b[2] - a[2]) * w).roundToInt(),
            ),
        )
    }

    private fun classOf(raw: String): String {
        val packed = HostsFile.parseIpv4(raw) ?: return "invalid"
        val a = packed ushr 24 and 0xFF
        val b = packed ushr 16 and 0xFF
        val c = packed ushr 8 and 0xFF
        return when {
            a == 10 && b == 7 && c == 0 -> "tun"
            a == 127 -> "loopback"
            a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31) -> "private"
            a == 169 && b == 254 -> "link_local"
            a in 224..239 -> "multicast"
            a == 0 || a >= 240 -> "reserved"
            else -> "public"
        }
    }

    private fun digest(algo: String, bytes: ByteArray): String {
        val data = if (bytes.size > 32_768) bytes.copyOf(32_768) else bytes
        return MessageDigest.getInstance(algo).digest(data).joinToString("") { b -> "%02x".format(b) }
    }

    private fun jsonEncode(value: LuaValue, depth: Int = 0): String {
        if (depth > 8) return "null"
        return when {
            value.isnil() -> "null"
            value.isboolean() -> value.toboolean().toString()
            value.isnumber() -> value.todouble().toString()
            value.isstring() -> JSONObject.quote(value.tojstring().take(8_192))
            value.istable() -> {
                val table = value.checktable()
                val array = seqSize(table) > 0 && table.get(1).isnil().not() && mapKeys(table).all { it.toIntOrNull() != null }
                if (array) {
                    JSONArray().also { json ->
                        var i = 1
                        while (i <= 128) {
                            val item = table.get(i)
                            if (item.isnil()) break
                            json.put(JSONObject.wrap(jsonEncode(item, depth + 1)) ?: jsonEncode(item, depth + 1))
                            i++
                        }
                    }.toString()
                    val parts = ArrayList<String>()
                    var i = 1
                    while (i <= 128) {
                        val item = table.get(i)
                        if (item.isnil()) break
                        parts.add(jsonEncode(item, depth + 1))
                        i++
                    }
                    parts.joinToString(",", "[", "]")
                } else {
                    val parts = ArrayList<String>()
                    table.keys().take(128).forEach { key ->
                        parts.add(JSONObject.quote(key.tojstring()) + ":" + jsonEncode(table.get(key), depth + 1))
                    }
                    parts.joinToString(",", "{", "}")
                }
            }
            else -> JSONObject.quote(value.tojstring())
        }
    }

    private fun jsonDecode(raw: String): LuaValue {
        val trimmed = raw.trim().take(32_768)
        return try {
            when {
                trimmed.startsWith("{") -> jsonToLua(JSONObject(trimmed))
                trimmed.startsWith("[") -> jsonToLua(JSONArray(trimmed))
                else -> throw LuaError("Invalid JSON")
            }
        } catch (error: Exception) {
            throw LuaError("Invalid JSON")
        }
    }

    private fun jsonToLua(value: Any?, depth: Int = 0): LuaValue {
        if (depth > 8 || value == null || value == JSONObject.NULL) return LuaValue.NIL
        return when (value) {
            is JSONObject -> {
                val table = LuaTable()
                value.keys().asSequence().take(128).forEach { key ->
                    table.set(key, jsonToLua(value.get(key), depth + 1))
                }
                table
            }
            is JSONArray -> {
                val table = LuaTable()
                for (i in 0 until min(value.length(), 128)) {
                    table.set(i + 1, jsonToLua(value.get(i), depth + 1))
                }
                table
            }
            is Boolean -> LuaValue.valueOf(value)
            is Int -> LuaValue.valueOf(value)
            is Long -> LuaValue.valueOf(value.toDouble())
            is Double -> LuaValue.valueOf(value)
            is String -> LuaValue.valueOf(value)
            else -> LuaValue.valueOf(value.toString())
        }
    }
}
