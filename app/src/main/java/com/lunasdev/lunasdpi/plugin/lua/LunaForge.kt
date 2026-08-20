package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.plugin.PluginLimits
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.ArrayDeque
import java.util.BitSet
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.random.Random

internal object LunaForge {
    private const val MAX = 256

    val typeNames = listOf(
        "Schedule", "Circuit", "Bloom", "Validator", "Ruleset", "Weighted", "Health",
        "Ledger", "Ranker", "Preset", "Facets", "Tokens", "Spark", "UnionFind", "Ini",
        "Highlight", "Plural", "Workflow", "Migration", "Watchdog", "Scorecard", "Sample",
        "Kanban", "JsonPtr", "Recur",
    )

    fun install(luna: LuaTable) {
        luna.set("Schedule", scheduleType())
        luna.set("Circuit", circuitType())
        luna.set("Bloom", bloomType())
        luna.set("Validator", validatorType())
        luna.set("Ruleset", rulesetType())
        luna.set("Weighted", weightedType())
        luna.set("Health", healthType())
        luna.set("Ledger", ledgerType())
        luna.set("Ranker", rankerType())
        luna.set("Preset", presetType())
        luna.set("Facets", facetsType())
        luna.set("Tokens", tokensType())
        luna.set("Spark", sparkType())
        luna.set("UnionFind", unionFindType())
        luna.set("Ini", iniType())
        luna.set("Highlight", highlightType())
        luna.set("Plural", pluralType())
        luna.set("Workflow", workflowType())
        luna.set("Migration", migrationType())
        luna.set("Watchdog", watchdogType())
        luna.set("Scorecard", scorecardType(luna))
        luna.set("Sample", sampleType())
        luna.set("Kanban", kanbanType(luna))
        luna.set("JsonPtr", jsonPtrType())
        luna.set("Recur", recurType())
        val forge = LuaTable()
        typeNames.forEach { name -> forge.set(name, luna.get(name)) }
        luna.set("forge", forge)
        val kit = luna.get("kit")
        if (kit.istable()) {
            val table = kit.checktable()
            typeNames.forEach { name -> table.set(name, luna.get(name)) }
        }
        val systems = luna.get("systems")
        if (systems.istable()) {
            val table = systems.checktable()
            table.set("forge", LuaValue.TRUE)
            table.set("schedule", LuaValue.TRUE)
            table.set("bloom", LuaValue.TRUE)
            table.set("circuit", LuaValue.TRUE)
            table.set("validate", LuaValue.TRUE)
            table.set("ruleset", LuaValue.TRUE)
            table.set("ledger", LuaValue.TRUE)
        }
    }

    fun nestOnto(client: LuaTable, luna: LuaTable) {
        typeNames.forEach { name -> client.set(name, luna.get(name)) }
        client.set("forge", luna.get("forge"))
    }

    private fun scheduleType(): LuaTable {
        val type = LuaTable()
        type.set("now", LuaFn.z {
            val cal = Calendar.getInstance()
            LuaFn.fromJava(
                mapOf(
                    "hour" to cal.get(Calendar.HOUR_OF_DAY),
                    "minute" to cal.get(Calendar.MINUTE),
                    "weekday" to isoDow(cal),
                    "weekend" to (isoDow(cal) >= 6),
                    "day" to cal.get(Calendar.DAY_OF_MONTH),
                    "month" to cal.get(Calendar.MONTH) + 1,
                ),
            )
        })
        type.set("hour", LuaFn.z { LuaValue.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) })
        type.set("weekday", LuaFn.z { LuaValue.valueOf(isoDow(Calendar.getInstance())) })
        type.set("weekend", LuaFn.z { LuaValue.valueOf(isoDow(Calendar.getInstance()) >= 6) })
        type.set("weekdays", LuaFn.z { LuaValue.valueOf(isoDow(Calendar.getInstance()) in 1..5) })
        type.set("between", LuaFn.t { fromH, toH ->
            LuaValue.valueOf(hourBetween(fromH.toint(), toH.toint()))
        })
        type.set("cron", LuaFn.o { LuaValue.valueOf(cronMatch(it.tojstring())) })
        type.set("window", LuaFn.r { fromH, toH, days ->
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Schedule"))
            t.set("active", LuaFn.z {
                val hourOk = hourBetween(fromH.toint(), toH.toint())
                val allowed = LuaFn.stringList(days, 7).mapNotNull { it.toIntOrNull() }
                val dow = isoDow(Calendar.getInstance())
                val dayOk = allowed.isEmpty() || dow in allowed
                LuaValue.valueOf(hourOk && dayOk)
            })
            t.set("describe", LuaFn.z {
                LuaValue.valueOf("${fromH.toint()}:00–${toH.toint()}:00")
            })
            t
        })
        return type
    }

    private fun circuitType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.t { threshold, cooldownMs ->
            val cap = threshold.optint(3).coerceIn(1, 32)
            val wait = cooldownMs.optdouble(15_000.0).toLong().coerceIn(1_000, 600_000)
            var fails = 0
            var openedAt = 0L
            var half = false
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Circuit"))
            fun stateName(): String {
                val now = System.currentTimeMillis()
                return when {
                    fails < cap -> "closed"
                    half -> "half"
                    now - openedAt >= wait -> {
                        half = true
                        "half"
                    }
                    else -> "open"
                }
            }
            t.set("allow", LuaFn.z {
                val state = stateName()
                LuaValue.valueOf(state != "open")
            })
            t.set("success", LuaFn.z {
                fails = 0
                half = false
                openedAt = 0L
                t
            })
            t.set("fail", LuaFn.z {
                fails += 1
                if (fails >= cap) {
                    openedAt = System.currentTimeMillis()
                    half = false
                }
                t
            })
            t.set("state", LuaFn.z { LuaValue.valueOf(stateName()) })
            t.set("failures", LuaFn.z { LuaValue.valueOf(fails) })
            t
        })
        return type
    }

    private fun bloomType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { bitsArg ->
            val bits = bitsArg.optint(2048).coerceIn(256, 8192)
            val set = BitSet(bits)
            var inserted = 0
            fun hashes(raw: String): IntArray {
                val h1 = raw.hashCode()
                val h2 = raw.reversed().hashCode() xor -1640531527
                return IntArray(3) { i ->
                    val mixed = h1.toLong() + i.toLong() * h2.toLong()
                    (mixed and 0x7fffffffL).toInt() % bits
                }
            }
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Bloom"))
            t.set("add", LuaFn.m1(t) { value ->
                hashes(value.tojstring()).forEach { set.set(it) }
                inserted += 1
                t
            })
            t.set("has", LuaFn.m1(t) { value ->
                LuaValue.valueOf(hashes(value.tojstring()).all { set.get(it) })
            })
            t.set("add_many", LuaFn.m1(t) { list ->
                LuaFn.stringList(list, MAX).forEach { item ->
                    hashes(item).forEach { set.set(it) }
                    inserted += 1
                }
                t
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(inserted) })
            t.set("clear", LuaFn.z {
                set.clear()
                inserted = 0
                t
            })
            t
        })
        return type
    }

    private fun validatorType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            data class Rule(val field: String, val kind: String, val extra: LuaValue)
            val rules = mutableListOf<Rule>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Validator"))
            fun add(field: LuaValue, kind: String, extra: LuaValue = LuaValue.NIL): LuaTable {
                if (rules.size < 64) rules.add(Rule(field.tojstring().take(40), kind, extra))
                return t
            }
            t.set("required", LuaFn.m1(t) { add(it, "required") })
            t.set("domain", LuaFn.m1(t) { add(it, "domain") })
            t.set("ipv4", LuaFn.m1(t) { add(it, "ipv4") })
            t.set("min", LuaFn.m2(t) { field, n -> add(field, "min", n) })
            t.set("max", LuaFn.m2(t) { field, n -> add(field, "max", n) })
            t.set("one_of", LuaFn.m2(t) { field, list -> add(field, "one_of", list) })
            t.set("glob", LuaFn.m2(t) { field, pattern -> add(field, "glob", pattern) })
            t.set("len", LuaFn.m3(t) { field, minN, maxN ->
                val extra = LuaTable()
                extra.set("min", minN)
                extra.set("max", maxN)
                add(field, "len", extra)
            })
            t.set("run", LuaFn.m1(t) { src ->
                val errors = LinkedHashMap<String, String>()
                val table = if (src.istable()) src.checktable() else LuaTable()
                rules.forEach { rule ->
                    if (errors.containsKey(rule.field)) return@forEach
                    val value = table.get(rule.field)
                    val text = if (value.isnil()) "" else value.tojstring()
                    val message = when (rule.kind) {
                        "required" -> if (text.isBlank()) "required" else null
                        "domain" -> if (!DomainValidator.isValidPattern(DomainValidator.normalize(text))) "invalid domain" else null
                        "ipv4" -> if (HostsFile.parseIpv4(text) == null) "invalid ipv4" else null
                        "min" -> if (!value.isnumber() || value.todouble() < rule.extra.todouble()) "min ${rule.extra}" else null
                        "max" -> if (!value.isnumber() || value.todouble() > rule.extra.todouble()) "max ${rule.extra}" else null
                        "one_of" -> {
                            val allowed = LuaFn.stringList(rule.extra, 32)
                            if (text !in allowed) "must be one of" else null
                        }
                        "glob" -> if (!glob(rule.extra.tojstring(), text)) "mismatch" else null
                        "len" -> {
                            val minN = rule.extra.get("min").optint(0)
                            val maxN = rule.extra.get("max").optint(400)
                            if (text.length < minN || text.length > maxN) "length" else null
                        }
                        else -> null
                    }
                    if (message != null) errors[rule.field] = message
                }
                LuaFn.fromJava(mapOf("ok" to errors.isEmpty(), "errors" to errors))
            })
            t
        })
        return type
    }

    private fun rulesetType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val rules = mutableListOf<Pair<String, String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Ruleset"))
            fun add(kind: String, pattern: LuaValue): LuaTable {
                if (rules.size < MAX) rules.add(kind to pattern.tojstring().take(120))
                return t
            }
            t.set("glob", LuaFn.m1(t) { add("glob", it) })
            t.set("domain", LuaFn.m1(t) { add("domain", it) })
            t.set("prefix", LuaFn.m1(t) { add("prefix", it) })
            t.set("cidr", LuaFn.m1(t) { add("cidr", it) })
            t.set("exact", LuaFn.m1(t) { add("exact", it) })
            t.set("test", LuaFn.m1(t) { LuaValue.valueOf(why(rules, it.tojstring()) != null) })
            t.set("why", LuaFn.m1(t) { LuaValue.valueOf(why(rules, it.tojstring()) ?: "") })
            t.set("size", LuaFn.z { LuaValue.valueOf(rules.size) })
            t.set("clear", LuaFn.z {
                rules.clear()
                t
            })
            t
        })
        return type
    }

    private fun why(rules: List<Pair<String, String>>, raw: String): String? {
        val host = DomainValidator.normalize(raw)
        val ip = HostsFile.parseIpv4(raw)
        rules.forEach { (kind, pattern) ->
            val hit = when (kind) {
                "exact" -> raw.equals(pattern, ignoreCase = true) || host == DomainValidator.normalize(pattern)
                "prefix" -> host.startsWith(pattern.lowercase(Locale.US)) || raw.startsWith(pattern)
                "glob" -> glob(pattern, raw) || glob(pattern, host)
                "domain" -> domainMatches(host, DomainValidator.normalize(pattern))
                "cidr" -> ip != null && cidrContains(ip, pattern)
                else -> false
            }
            if (hit) return "$kind:$pattern"
        }
        return null
    }

    private fun weightedType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val items = mutableListOf<Pair<LuaValue, Int>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Weighted"))
            t.set("add", LuaFn.m2(t) { value, weight ->
                if (items.size < 64) items.add(value to weight.optint(1).coerceIn(1, 10_000))
                t
            })
            t.set("pick", LuaFn.z {
                val total = items.sumOf { it.second }
                if (total <= 0) return@z LuaValue.NIL
                var roll = Random.nextInt(total)
                items.forEach { (value, weight) ->
                    roll -= weight
                    if (roll < 0) return@z value
                }
                items.lastOrNull()?.first ?: LuaValue.NIL
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
            t
        })
        return type
    }

    private fun healthType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val checks = LinkedHashMap<String, Pair<Boolean, String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Health"))
            t.set("ok", LuaFn.m3(t) { name, pass, detail ->
                if (checks.size < 32 || checks.containsKey(name.tojstring())) {
                    checks[name.tojstring().take(40)] = pass.toboolean() to detail.optjstring("").take(80)
                }
                t
            })
            t.set("fail", LuaFn.m2(t) { name, detail ->
                checks[name.tojstring().take(40)] = false to detail.optjstring("").take(80)
                t
            })
            t.set("all", LuaFn.z { LuaValue.valueOf(checks.isNotEmpty() && checks.values.all { it.first }) })
            t.set("any_fail", LuaFn.z { LuaValue.valueOf(checks.values.any { !it.first }) })
            t.set("snapshot", LuaFn.z {
                LuaFn.fromJava(checks.map { (k, v) -> mapOf("name" to k, "ok" to v.first, "detail" to v.second) })
            })
            t.set("worst", LuaFn.z {
                LuaValue.valueOf(
                    when {
                        checks.isEmpty() -> "info"
                        checks.values.any { !it.first } -> "danger"
                        else -> "success"
                    },
                )
            })
            t
        })
        return type
    }

    private fun ledgerType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val balances = LinkedHashMap<String, Int>()
            val log = ArrayDeque<Map<String, Any>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Ledger"))
            fun record(op: String, account: String, amount: Int) {
                if (log.size >= 64) log.removeFirst()
                log.addLast(mapOf("op" to op, "account" to account, "amount" to amount, "balance" to (balances[account] ?: 0)))
            }
            t.set("credit", LuaFn.m2(t) { account, amount ->
                val key = account.tojstring().take(40)
                val n = amount.optint(1).coerceIn(0, 10_000)
                balances[key] = (balances[key] ?: 0) + n
                record("credit", key, n)
                LuaValue.valueOf((balances[key] ?: 0).toDouble())
            })
            t.set("debit", LuaFn.m2(t) { account, amount ->
                val key = account.tojstring().take(40)
                val n = amount.optint(1).coerceIn(0, 10_000)
                val cur = balances[key] ?: 0
                if (cur < n) return@m2 LuaValue.FALSE
                balances[key] = cur - n
                record("debit", key, n)
                LuaValue.TRUE
            })
            t.set("balance", LuaFn.m1(t) { LuaValue.valueOf((balances[it.tojstring()] ?: 0).toDouble()) })
            t.set("accounts", LuaFn.z { LuaFn.fromJava(balances.keys.toList()) })
            t.set("entries", LuaFn.z { LuaFn.fromJava(log.toList()) })
            t
        })
        return type
    }

    private fun rankerType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val scores = LinkedHashMap<String, Double>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Ranker"))
            t.set("set", LuaFn.m2(t) { id, score ->
                if (scores.size < MAX || scores.containsKey(id.tojstring())) {
                    scores[id.tojstring().take(80)] = score.todouble()
                }
                t
            })
            t.set("bump", LuaFn.m2(t) { id, delta ->
                val key = id.tojstring().take(80)
                scores[key] = (scores[key] ?: 0.0) + delta.optdouble(1.0)
                t
            })
            t.set("get", LuaFn.m1(t) { LuaValue.valueOf(scores[it.tojstring()] ?: 0.0) })
            t.set("top", LuaFn.m1(t) { n ->
                LuaFn.fromJava(
                    scores.entries.sortedByDescending { it.value }.take(n.optint(5).coerceIn(1, 32))
                        .map { mapOf("id" to it.key, "score" to it.value) },
                )
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(scores.size) })
            t
        })
        return type
    }

    private fun presetType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val slots = LinkedHashMap<String, Map<String, String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Preset"))
            t.set("save", LuaFn.m2(t) { name, table ->
                if (slots.size < 16 || slots.containsKey(name.tojstring())) {
                    slots[name.tojstring().take(40)] = tableToStringMap(table)
                }
                t
            })
            t.set("load", LuaFn.m1(t) {
                val hit = slots[it.tojstring()] ?: return@m1 LuaValue.NIL
                LuaFn.fromJava(hit)
            })
            t.set("has", LuaFn.m1(t) { LuaValue.valueOf(slots.containsKey(it.tojstring())) })
            t.set("keys", LuaFn.z { LuaFn.fromJava(slots.keys.toList()) })
            t.set("delete", LuaFn.m1(t) { LuaValue.valueOf(slots.remove(it.tojstring()) != null) })
            t
        })
        return type
    }

    private fun facetsType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val rows = mutableListOf<Map<String, String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Facets"))
            t.set("add", LuaFn.m1(t) { row ->
                if (rows.size < MAX) rows.add(tableToStringMap(row))
                t
            })
            t.set("count", LuaFn.m1(t) { field ->
                val key = field.tojstring()
                val counts = LinkedHashMap<String, Int>()
                rows.forEach { row ->
                    val value = row[key] ?: return@forEach
                    counts[value] = (counts[value] ?: 0) + 1
                }
                LuaFn.fromJava(counts)
            })
            t.set("values", LuaFn.m1(t) { field ->
                LuaFn.fromJava(rows.mapNotNull { it[field.tojstring()] }.distinct().take(64))
            })
            t.set("where", LuaFn.m2(t) { field, value ->
                val key = field.tojstring()
                val needle = value.tojstring()
                LuaFn.fromJava(rows.filter { it[key] == needle }.take(64))
            })
            t.set("size", LuaFn.z { LuaValue.valueOf(rows.size) })
            t
        })
        return type
    }

    private fun tokensType(): LuaTable {
        val type = LuaTable()
        type.set("words", LuaFn.o {
            LuaFn.fromJava(it.tojstring().split(Regex("\\s+")).filter { tok -> tok.isNotBlank() }.take(MAX))
        })
        type.set("lines", LuaFn.o {
            LuaFn.fromJava(it.tojstring().lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }.take(MAX))
        })
        type.set("csv", LuaFn.o {
            LuaFn.fromJava(it.tojstring().split(',').map { cell -> cell.trim().trim('"') }.filter { cell -> cell.isNotEmpty() }.take(MAX))
        })
        type.set("hosts", LuaFn.o {
            LuaFn.fromJava(
                it.tojstring().lines().map { line -> line.substringBefore('#').trim() }
                    .flatMap { line -> line.split(Regex("\\s+")) }
                    .map { tok -> DomainValidator.normalize(tok) }
                    .filter { tok ->
                        DomainValidator.isValidPattern(tok) && HostsFile.parseIpv4(tok) == null
                    }
                    .distinct()
                    .take(MAX),
            )
        })
        return type
    }

    private fun sparkType(): LuaTable {
        val type = LuaTable()
        type.set("of", LuaFn.o { list ->
            val nums = arrayNumbers(list)
            if (nums.isEmpty()) return@o LuaValue.valueOf("")
            val minV = nums.min()
            val maxV = nums.max()
            val span = (maxV - minV).let { if (it == 0.0) 1.0 else it }
            val blocks = "▁▂▃▄▅▆▇█"
            LuaValue.valueOf(
                nums.take(64).joinToString("") { n ->
                    val idx = (((n - minV) / span) * (blocks.length - 1)).toInt().coerceIn(0, blocks.lastIndex)
                    blocks[idx].toString()
                },
            )
        })
        return type
    }

    private fun unionFindType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val parent = LinkedHashMap<String, String>()
            fun find(raw: String): String {
                val key = raw.take(80)
                val p = parent[key] ?: key.also { parent[key] = key }
                if (p != key) {
                    val root = find(p)
                    parent[key] = root
                    return root
                }
                return p
            }
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("UnionFind"))
            t.set("union", LuaFn.m2(t) { a, b ->
                val ra = find(a.tojstring())
                val rb = find(b.tojstring())
                parent[ra] = rb
                t
            })
            t.set("find", LuaFn.m1(t) { LuaValue.valueOf(find(it.tojstring())) })
            t.set("same", LuaFn.m2(t) { a, b -> LuaValue.valueOf(find(a.tojstring()) == find(b.tojstring())) })
            t
        })
        return type
    }

    private fun iniType(): LuaTable {
        val type = LuaTable()
        type.set("parse", LuaFn.o { src ->
            val out = LuaTable()
            var section = out
            src.tojstring().lines().take(200).forEach { raw ->
                val line = raw.substringBefore(';').substringBefore('#').trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("[") && line.endsWith("]") -> {
                        val name = line.trim('[', ']').take(40)
                        val next = LuaTable()
                        out.set(name, next)
                        section = next
                    }
                    '=' in line -> {
                        val kv = line.split('=', limit = 2)
                        section.set(kv[0].trim().take(40), LuaValue.valueOf(kv[1].trim().take(200)))
                    }
                }
            }
            out
        })
        type.set("get", LuaFn.t { root, path ->
            val parts = path.tojstring().split('.', limit = 2)
            if (parts.size == 1) root.get(parts[0]) else root.get(parts[0]).get(parts[1])
        })
        return type
    }

    private fun highlightType(): LuaTable {
        val type = LuaTable()
        type.set("wrap", LuaFn.t { text, needle ->
            val src = text.tojstring().take(400)
            val q = needle.tojstring()
            if (q.isBlank()) return@t LuaValue.valueOf(src)
            LuaValue.valueOf(src.replace(q, "«$q»", ignoreCase = true).take(500))
        })
        return type
    }

    private fun pluralType(): LuaTable {
        val type = LuaTable()
        type.set("en", LuaFn.r { n, one, many ->
            val count = n.todouble()
            LuaValue.valueOf(if (count == 1.0) one.tojstring() else many.tojstring())
        })
        type.set("join", LuaFn.r { n, one, many ->
            val count = n.todouble()
            val word = if (count == 1.0) one.tojstring() else many.tojstring()
            LuaValue.valueOf("${if (count == count.toInt().toDouble()) count.toInt() else count} $word")
        })
        return type
    }

    private fun workflowType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { names ->
            val steps = LuaFn.stringList(names, PluginLimits.MAX_UI_SECTIONS).ifEmpty { listOf("start") }
            var index = 0
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Workflow"))
            t.set("next", LuaFn.z {
                index = (index + 1).coerceAtMost(steps.lastIndex)
                LuaValue.valueOf(steps[index])
            })
            t.set("prev", LuaFn.z {
                index = (index - 1).coerceAtLeast(0)
                LuaValue.valueOf(steps[index])
            })
            t.set("at", LuaFn.m1(t) { name ->
                val i = steps.indexOf(name.tojstring())
                if (i >= 0) index = i
                t
            })
            t.set("index", LuaFn.z { LuaValue.valueOf(index + 1) })
            t.set("name", LuaFn.z { LuaValue.valueOf(steps[index]) })
            t.set("names", LuaFn.z { LuaFn.fromJava(steps) })
            t.set("first", LuaFn.z { LuaValue.valueOf(index == 0) })
            t.set("last", LuaFn.z { LuaValue.valueOf(index == steps.lastIndex) })
            t
        })
        return type
    }

    private fun migrationType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val steps = LinkedHashMap<Int, LuaValue>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Migration"))
            t.set("step", LuaFn.m2(t) { version, fn ->
                steps[version.toint().coerceIn(1, 64)] = fn
                t
            })
            t.set("run", LuaFn.m1(t) { current ->
                var version = current.optint(0)
                steps.keys.sorted().forEach { target ->
                    if (target > version) {
                        runCatching { steps[target]?.call() }
                        version = target
                    }
                }
                LuaValue.valueOf(version)
            })
            t
        })
        return type
    }

    private fun watchdogType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { ttl ->
            val wait = ttl.optdouble(30_000.0).toLong().coerceIn(1_000, 600_000)
            var last = System.currentTimeMillis()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Watchdog"))
            t.set("ping", LuaFn.z {
                last = System.currentTimeMillis()
                t
            })
            t.set("alive", LuaFn.z { LuaValue.valueOf(System.currentTimeMillis() - last <= wait) })
            t.set("age", LuaFn.z { LuaValue.valueOf((System.currentTimeMillis() - last) / 1000.0) })
            t
        })
        return type
    }

    private fun scorecardType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val scores = LinkedHashMap<String, Double>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Scorecard"))
            t.set("add", LuaFn.m2(t) { name, value ->
                if (scores.size < 32 || scores.containsKey(name.tojstring())) {
                    scores[name.tojstring().take(40)] = value.todouble().coerceIn(0.0, 1.0)
                }
                t
            })
            t.set("score", LuaFn.z {
                if (scores.isEmpty()) LuaValue.valueOf(0.0) else LuaValue.valueOf(scores.values.average())
            })
            t.set("toJSON", LuaFn.z { LuaFn.fromJava(scores) })
            t.set("to_ui", LuaFn.z {
                val ui = luna.get("ui")
                val out = LuaTable()
                scores.entries.take(PluginLimits.MAX_UI_ITEMS).forEachIndexed { i, (name, value) ->
                    val spec = LuaTable()
                    spec.set("label", LuaValue.valueOf(name))
                    spec.set("value", LuaValue.valueOf(value))
                    spec.set("max", LuaValue.valueOf(1))
                    out.set(i + 1, runCatching { ui.get("score").call(spec) }.getOrDefault(spec))
                }
                out
            })
            t
        })
        return type
    }

    private fun sampleType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { cap ->
            val capacity = cap.optint(16).coerceIn(1, MAX)
            val items = mutableListOf<LuaValue>()
            var seen = 0
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Sample"))
            t.set("offer", LuaFn.m1(t) { value ->
                seen += 1
                if (items.size < capacity) items.add(value) else {
                    val i = Random.nextInt(seen)
                    if (i < capacity) items[i] = value
                }
                t
            })
            t.set("values", LuaFn.z { LuaFn.fromJava(items.map { luaToJava(it) }) })
            t.set("size", LuaFn.z { LuaValue.valueOf(items.size) })
            t
        })
        return type
    }

    private fun kanbanType(luna: LuaTable): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.z {
            val cols = LinkedHashMap<String, MutableList<String>>()
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Kanban"))
            t.set("column", LuaFn.m1(t) {
                if (cols.size < 8) cols.getOrPut(it.tojstring().take(24)) { mutableListOf() }
                t
            })
            t.set("card", LuaFn.m2(t) { column, title ->
                val list = cols.getOrPut(column.tojstring().take(24)) { mutableListOf() }
                if (list.size < 32) list.add(title.tojstring().take(80))
                t
            })
            t.set("toJSON", LuaFn.z { LuaFn.fromJava(cols) })
            t.set("to_ui", LuaFn.z {
                val ui = luna.get("ui")
                val out = LuaTable()
                var i = 1
                cols.forEach { (col, cards) ->
                    cards.forEach { card ->
                        val spec = LuaTable()
                        spec.set("title", LuaValue.valueOf(card))
                        spec.set("trailing", LuaValue.valueOf(col))
                        out.set(i, runCatching { ui.get("list_item").call(spec) }.getOrDefault(spec))
                        i += 1
                    }
                }
                out
            })
            t
        })
        return type
    }

    private fun jsonPtrType(): LuaTable {
        val type = LuaTable()
        type.set("get", LuaFn.t { root, path ->
            var cur = root
            path.tojstring().trim('/').split('/').filter { it.isNotBlank() }.take(12).forEach { part ->
                if (!cur.istable()) return@t LuaValue.NIL
                cur = part.toIntOrNull()?.let { cur.get(it) } ?: cur.get(part)
            }
            cur
        })
        type.set("set", LuaFn.r { root, path, value ->
            if (!root.istable()) return@r root
            val parts = path.tojstring().trim('/').split('/').filter { it.isNotBlank() }.take(12)
            if (parts.isEmpty()) return@r root
            var cur = root.checktable()
            parts.dropLast(1).forEach { part ->
                val next = part.toIntOrNull()?.let { cur.get(it) } ?: cur.get(part)
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
            root
        })
        return type
    }

    private fun recurType(): LuaTable {
        val type = LuaTable()
        type.set("new", LuaFn.o { seconds ->
            val wait = seconds.optdouble(60.0).toLong().coerceIn(1, 86_400) * 1000
            var last = 0L
            val t = LuaTable()
            t.set("__kind", LuaValue.valueOf("Recur"))
            t.set("due", LuaFn.z {
                val now = System.currentTimeMillis()
                if (last == 0L || now - last >= wait) {
                    last = now
                    LuaValue.TRUE
                } else {
                    LuaValue.FALSE
                }
            })
            t.set("reset", LuaFn.z {
                last = 0L
                t
            })
            t
        })
        return type
    }

    private fun isoDow(cal: Calendar): Int {
        val d = cal.get(Calendar.DAY_OF_WEEK)
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }

    private fun hourBetween(from: Int, to: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val a = from.coerceIn(0, 23)
        val b = to.coerceIn(0, 24)
        return if (a <= b) hour in a until b else hour >= a || hour < b
    }

    private fun cronMatch(expr: String): Boolean {
        val fields = expr.trim().split(Regex("\\s+"))
        if (fields.size < 5) return false
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.MINUTE)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val iso = isoDow(cal)
        val cronDow = if (iso == 7) 0 else iso
        return matchCronField(fields[0], minute, 0, 59) &&
            matchCronField(fields[1], hour, 0, 23) &&
            matchCronField(fields[2], dom, 1, 31) &&
            matchCronField(fields[3], month, 1, 12) &&
            (matchCronField(fields[4], cronDow, 0, 7) || (iso == 7 && matchCronField(fields[4], 7, 0, 7)))
    }

    private fun matchCronField(field: String, value: Int, min: Int, max: Int): Boolean {
        if (field == "*") return true
        return field.split(',').any { part ->
            when {
                part.startsWith("*/") -> {
                    val step = part.drop(2).toIntOrNull() ?: return@any false
                    step > 0 && (value - min) % step == 0
                }
                '-' in part -> {
                    val ends = part.split('-', limit = 2)
                    val lo = ends[0].toIntOrNull() ?: return@any false
                    val hi = ends.getOrNull(1)?.toIntOrNull() ?: return@any false
                    value in lo.coerceIn(min, max)..hi.coerceIn(min, max)
                }
                else -> part.toIntOrNull() == value
            }
        }
    }

    private fun domainMatches(host: String, pattern: String): Boolean {
        return host == pattern ||
            (pattern.startsWith("*.") && (host.endsWith("." + pattern.drop(2)) || host == pattern.drop(2)))
    }

    private fun cidrContains(ip: Int, raw: String): Boolean {
        val parts = raw.trim().split('/', limit = 2)
        val net = HostsFile.parseIpv4(parts[0]) ?: return false
        val bits = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 32) ?: 32
        val mask = if (bits == 0) 0 else (-1 shl (32 - bits))
        return (ip and mask) == (net and mask)
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

    private fun arrayNumbers(value: LuaValue): List<Double> {
        if (!value.istable()) return emptyList()
        val table = value.checktable()
        val out = mutableListOf<Double>()
        for (i in 1..table.length().coerceAtMost(64)) {
            val item = table.get(i)
            if (item.isnumber()) out.add(item.todouble())
        }
        return out
    }

    private fun luaToJava(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        else -> value.tojstring()
    }
}
