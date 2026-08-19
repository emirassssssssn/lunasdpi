package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.plugin.PLUGIN_API_LEVEL
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.PluginSecurity
import com.lunasdev.lunasdpi.plugin.PluginStorage
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

internal object LunaClient {
    fun install(luna: LuaTable, pluginId: String, bridge: PluginNativeBridge) {
        val client = LuaTable()
        nest(client, luna)
        Bind(client, luna, pluginId, bridge).attach()
        luna.set("Client", client)
        luna.set("client", client)
        luna.set("REST", client.get("rest"))
    }

    private fun nest(client: LuaTable, luna: LuaTable) {
        listOf(
            "user", "permissions", "events", "storage", "app", "i18n", "clock", "log", "notify",
            "vpn", "rules", "hosts", "ui", "string", "table", "json", "time", "color", "domain",
            "ipv4", "hash", "util", "semver", "path", "fmt", "Collection", "Events",
            "PageBuilder", "EmbedBuilder", "RuleBuilder", "HostsBuilder", "IntentsBitField",
            "PermissionFlagsBits",
        ).forEach { name ->
            client.set(name, luna.get(name))
        }
    }

    private class Bind(
        private val client: LuaTable,
        private val luna: LuaTable,
        private val pluginId: String,
        private val bridge: PluginNativeBridge,
    ) {
        private val readyAt = System.currentTimeMillis() / 1000.0

        fun attach() {
            identity()
            events()
            permissions()
            storage()
            logging()
            notifications()
            vpn()
            rules()
            hosts()
            app()
            clock()
            i18n()
            format()
            validate()
            domain()
            net()
            hash()
            json()
            color()
            time()
            collection()
            ui()
            builders()
            rest()
            util()
        }

        private fun z(name: String, block: () -> LuaValue) {
            client.set(name, LuaFn.z(block))
        }

        private fun o(name: String, block: (LuaValue) -> LuaValue) {
            client.set(name, LuaFn.m1(client, block))
        }

        private fun t(name: String, block: (LuaValue, LuaValue) -> LuaValue) {
            client.set(name, LuaFn.m2(client, block))
        }

        private fun r(name: String, block: (LuaValue, LuaValue, LuaValue) -> LuaValue) {
            client.set(name, LuaFn.m3(client, block))
        }

        private fun call(module: String, method: String, vararg args: LuaValue): LuaValue {
            return LuaFn.invoke(luna.get(module), method, *args)
        }

        private fun identity() {
            z("id") { LuaValue.valueOf(bridge.pluginId()) }
            z("username") { LuaValue.valueOf(bridge.pluginName()) }
            z("displayName") { LuaValue.valueOf(bridge.pluginName()) }
            z("author") { LuaValue.valueOf(bridge.pluginAuthor()) }
            z("version") { LuaValue.valueOf(bridge.pluginVersion()) }
            z("tag") { LuaValue.valueOf("${bridge.pluginName()}@${bridge.pluginVersion()}") }
            z("locale") { LuaValue.valueOf(bridge.locale()) }
            z("apiLevel") { LuaValue.valueOf(PLUGIN_API_LEVEL) }
            z("toJSON") { call("user", "toJSON") }
            z("application") {
                LuaFn.fromJava(
                    mapOf(
                        "name" to "Lunas DPI",
                        "version" to bridge.appVersion(),
                        "id" to "com.lunasdev.lunasdpi",
                    ),
                )
            }
            z("options") {
                LuaFn.fromJava(
                    mapOf(
                        "max_rules" to 16,
                        "max_hosts" to HostsFile.MAX_PER_PLUGIN,
                        "max_timers" to 4,
                        "min_timer_ms" to 2_000,
                        "max_timer_ms" to 120_000,
                        "max_storage_keys" to 64,
                        "max_storage_chars" to PluginStorage.MAX_VALUE_CHARS,
                        "sandbox" to true,
                    ),
                )
            }
            z("readyAt") { LuaValue.valueOf(readyAt) }
            z("isReady") { LuaValue.TRUE }
            z("pluginId") { LuaValue.valueOf(pluginId) }
        }

        private fun events() {
            t("on") { name, fn -> call("events", "on", name, fn) }
            t("once") { name, fn -> call("events", "once", name, fn) }
            t("off") { name, fn -> call("events", "off", name, fn) }
            t("addListener") { name, fn -> call("events", "on", name, fn) }
            t("removeListener") { name, fn -> call("events", "off", name, fn) }
            o("removeAllListeners") { call("events", "removeAllListeners", it) }
            o("listenerCount") { call("events", "listenerCount", it) }
            z("eventNames") { call("events", "eventNames") }
            t("prependListener") { name, fn -> call("events", "on", name, fn) }
            o("emit") { name ->
                bridge.events().emit(name.tojstring())
                LuaValue.TRUE
            }
        }

        private fun permissions() {
            o("hasPermission") { call("permissions", "has", it) }
            o("hasIntent") { call("permissions", "has", it) }
            z("intentsBitfield") { call("permissions", "bitfield") }
            z("grantedPermissions") { call("permissions", "toArray") }
            o("missingPermission") { call("permissions", "missing", it) }
            o("anyPermission") { call("permissions", "any", it) }
            o("allPermissions") { call("permissions", "all", it) }
            z("permissionFlags") { luna.get("PermissionFlagsBits") }
        }

        private fun storage() {
            o("storeGet") { call("storage", "get", it) }
            t("storeSet") { key, value -> call("storage", "set", key, value) }
            o("storeDelete") { call("storage", "remove", it) }
            o("storeHas") { call("storage", "has", it) }
            z("storeKeys") { call("storage", "keys") }
            z("storeSize") { call("storage", "size") }
            o("storeGetNumber") { call("storage", "get_number", it) }
            t("storeSetNumber") { key, value -> call("storage", "set_number", key, value) }
            o("storeGetBool") { call("storage", "get_bool", it) }
            t("storeSetBool") { key, value -> call("storage", "set_bool", key, value) }
            o("storeGetJSON") { call("storage", "get_json", it) }
            t("storeSetJSON") { key, value -> call("storage", "set_json", key, value) }
            t("storeIncr") { key, value -> call("storage", "incr", key, value) }
            z("storeClear") { call("storage", "clear") }
        }

        private fun logging() {
            o("logDebug") { call("log", "debug", it) }
            o("logInfo") { call("log", "info", it) }
            o("logWarn") { call("log", "warn", it) }
            o("logError") { call("log", "error", it) }
            o("logPrint") { call("log", "print", it) }
            t("logAt") { level, message -> call("log", "log", level, message) }
        }

        private fun notifications() {
            t("notifyShow") { title, text -> call("notify", "show", title, text) }
            t("notifyInfo") { title, text -> call("notify", "info", title, text) }
            t("notifySuccess") { title, text -> call("notify", "success", title, text) }
            t("notifyWarn") { title, text -> call("notify", "warn", title, text) }
            t("notifyError") { title, text -> call("notify", "error", title, text) }
        }

        private fun vpn() {
            z("vpnState") { call("vpn", "state") }
            z("vpnPhase") { call("vpn", "phase") }
            z("vpnConnected") { call("vpn", "connected") }
            z("vpnDisconnected") { LuaValue.valueOf(bridge.vpnPhase() == "disconnected") }
            z("vpnActive") { call("vpn", "is_active") }
            z("vpnSnapshot") { call("vpn", "snapshot") }
            z("vpnStats") { call("vpn", "stats") }
            z("vpnUptime") { call("vpn", "uptime") }
            z("vpnAlive") { call("vpn", "alive") }
            z("vpnTun") { call("vpn", "tun") }
            z("vpnPackets") { call("vpn", "packets") }
            z("vpnDropped") { call("vpn", "dropped") }
            z("vpnBytesIn") { call("vpn", "bytes_in") }
            z("vpnBytesOut") { call("vpn", "bytes_out") }
            z("vpnDnsQueries") { call("vpn", "dns_queries") }
            z("vpnStrategy") { call("vpn", "strategy") }
            z("vpnStart") { call("vpn", "request_start") }
            z("vpnStop") { call("vpn", "request_stop") }
            z("vpnConnect") { call("vpn", "connect") }
            z("vpnDisconnect") { call("vpn", "disconnect") }
            z("vpnFetch") { call("vpn", "fetch") }
        }

        private fun rules() {
            o("createRule") { call("rules", "create", it) }
            o("upsertRule") { call("rules", "upsert", it) }
            t("editRule") { id, patch -> call("rules", "edit", id, patch) }
            o("deleteRule") { call("rules", "delete", it) }
            o("getRule") { call("rules", "get", it) }
            o("findRule") { luna.get("rules").get("cache").get("find").call(it) }
            z("listRules") { call("rules", "list") }
            z("countRules") { call("rules", "count") }
            o("hasRule") { call("rules", "has", it) }
            o("enableRule") { call("rules", "enable", it) }
            o("disableRule") { call("rules", "disable", it) }
            z("clearRules") { call("rules", "clear") }
            z("fetchRules") { call("rules", "fetch") }
            o("resolveRule") { call("rules", "resolve", it) }
        }

        private fun hosts() {
            o("setHostsText") { call("hosts", "set_text", it) }
            o("setHosts") { call("hosts", "set", it) }
            t("addHost") { host, ip -> call("hosts", "add", host, ip) }
            o("removeHost") { call("hosts", "remove", it) }
            o("getHost") { call("hosts", "get", it) }
            o("resolveHost") { call("hosts", "resolve", it) }
            o("hasHost") { call("hosts", "has", it) }
            z("listHosts") { call("hosts", "list") }
            z("countHosts") { call("hosts", "count") }
            z("clearHosts") { call("hosts", "clear") }
            o("parseHosts") { call("hosts", "parse", it) }
            z("hostsToText") { call("hosts", "to_text") }
            z("fetchHosts") { call("hosts", "fetch") }
            o("replaceHosts") { call("hosts", "set", it) }
        }

        private fun app() {
            z("appVersion") { call("app", "version") }
            z("appName") { call("app", "name") }
            z("appConfig") { call("app", "config") }
            z("appMode") { call("app", "mode") }
            z("appDnsMode") { call("app", "dns_mode") }
            z("appMtu") { call("app", "mtu") }
            z("appIpv6Mode") { call("app", "ipv6_mode") }
            z("appBlockQuic") { call("app", "block_quic") }
            z("appLogLevel") { call("app", "log_level") }
            z("appPerAppMode") { call("app", "per_app_mode") }
            z("appJSON") { call("app", "toJSON") }
            z("appLocale") { call("app", "locale") }
        }

        private fun clock() {
            z("now") { call("clock", "now") }
            z("nowMs") { call("clock", "now_ms") }
            z("isoNow") { call("clock", "iso") }
            t("setTimeout") { ms, fn -> call("clock", "setTimeout", ms, fn) }
            t("setInterval") { ms, fn -> call("clock", "setInterval", ms, fn) }
            o("clearTimeout") { call("clock", "clearTimeout", it) }
            o("clearInterval") { call("clock", "clearInterval", it) }
            t("after") { ms, fn -> call("clock", "after", ms, fn) }
        }

        private fun i18n() {
            t("t") { key, fallback -> call("i18n", "t", key, fallback) }
            t("translate") { key, fallback -> call("i18n", "translate", key, fallback) }
            o("hasTranslation") { call("i18n", "has", it) }
            z("language") { call("i18n", "language") }
        }

        private fun format() {
            o("formatBytes") { call("fmt", "bytes", it) }
            o("formatDuration") { call("fmt", "duration", it) }
            o("formatPercent") { call("fmt", "percent", it) }
            o("formatNumber") { call("fmt", "number", it) }
            o("formatUptime") { call("fmt", "uptime", it) }
            o("compactNumber") { call("fmt", "compact", it) }
            t("humanJoin") { list, sep -> call("fmt", "join", list, sep) }
            o("bulletLines") { call("fmt", "bullets", it) }
            t("truncate") { text, n -> call("string", "truncate", text, n) }
            r("padStart") { text, n, pad -> call("string", "pad_start", text, n, pad) }
            r("padEnd") { text, n, pad -> call("string", "pad_end", text, n, pad) }
            o("slugify") { call("string", "slug", it) }
            o("titleCase") { call("string", "title_case", it) }
            o("lower") { call("string", "lower", it) }
            o("upper") { call("string", "upper", it) }
            o("trim") { call("string", "trim", it) }
        }

        private fun validate() {
            o("isDomain") { call("domain", "valid", it) }
            o("isDomainPattern") { call("domain", "valid_pattern", it) }
            o("isWildcard") { call("domain", "is_wildcard", it) }
            o("isIpv4") { call("ipv4", "valid", it) }
            o("isAllowedIpv4") { call("ipv4", "allowed_host", it) }
            o("isPrivateIpv4") { call("ipv4", "private", it) }
            o("isLoopbackIpv4") { call("ipv4", "loopback", it) }
            o("isHexColor") { call("color", "is_hex", it) }
            o("isSemver") { call("semver", "valid", it) }
            o("isHostsLine") { LuaValue.valueOf(hostsLineOk(it.tojstring())) }
            o("isGithubUrl") {
                val url = it.tojstring()
                LuaValue.valueOf(url.isNotBlank() && PluginSecurity.validateHomepage(url) == null)
            }
            o("isBlank") { LuaValue.valueOf(it.tojstring().isBlank()) }
            o("isNotBlank") { LuaValue.valueOf(it.tojstring().isNotBlank()) }
            o("rejectDomain") { call("domain", "reject_reason", it) }
            o("isNumber") { call("util", "is_number", it) }
            o("isTable") { call("util", "is_table", it) }
            o("isString") { call("util", "is_string", it) }
            o("isFunction") { call("util", "is_function", it) }
        }

        private fun domain() {
            o("normalizeDomain") { call("domain", "normalize", it) }
            o("domainRoot") { call("domain", "root", it) }
            o("domainParent") { call("domain", "parent", it) }
            o("domainLabels") { call("domain", "labels", it) }
            t("domainMatches") { host, pattern -> call("domain", "matches", host, pattern) }
            o("domainSuffix") { call("domain", "suffix", it) }
            t("domainJoin") { a, b -> call("domain", "join", a, b) }
            o("domainRegistrable") { call("domain", "registrable", it) }
        }

        private fun net() {
            o("parseIpv4") { call("ipv4", "parse", it) }
            o("formatIpv4") { call("ipv4", "format", it) }
            o("ipv4Octets") { call("ipv4", "octets", it) }
            r("inCidr") { ip, net, bits -> call("ipv4", "in_cidr", ip, net, bits) }
            o("classifyIpv4") {
                val raw = it.tojstring()
                when {
                    call("ipv4", "tun_range", it).toboolean() -> LuaValue.valueOf("tun")
                    call("ipv4", "loopback", it).toboolean() -> LuaValue.valueOf("loopback")
                    call("ipv4", "private", it).toboolean() -> LuaValue.valueOf("private")
                    call("ipv4", "link_local", it).toboolean() -> LuaValue.valueOf("link_local")
                    call("ipv4", "multicast", it).toboolean() -> LuaValue.valueOf("multicast")
                    call("ipv4", "public", it).toboolean() -> LuaValue.valueOf("public")
                    else -> LuaValue.valueOf(if (HostsFile.parseIpv4(raw) == null) "invalid" else "reserved")
                }
            }
            t("ipv4Equal") { a, b -> call("ipv4", "equal", a, b) }
            r("cidrContains") { net, ip, bits -> call("ipv4", "cidr_contains", net, ip, bits) }
        }

        private fun hash() {
            o("sha256") { call("hash", "sha256", it) }
            o("hexEncode") { call("hash", "hex_encode", it) }
            o("hexDecode") { call("hash", "hex_decode", it) }
            o("base64Encode") { call("hash", "base64_encode", it) }
            o("base64Decode") { call("hash", "base64_decode", it) }
        }

        private fun json() {
            o("jsonEncode") { call("json", "encode", it) }
            o("jsonDecode") { call("json", "decode", it) }
            t("jsonGet") { json, key -> call("json", "get", json, key) }
            o("jsonStringify") { call("json", "stringify", it) }
        }

        private fun color() {
            o("parseColor") { call("color", "parse", it) }
            r("mixColor") { a, b, t -> call("color", "mix", a, b, t) }
            t("lightenColor") { hex, amount -> call("color", "lighten", hex, amount) }
            t("darkenColor") { hex, amount -> call("color", "darken", hex, amount) }
            t("contrastColor") { a, b -> call("color", "contrast", a, b) }
            o("lumaColor") { call("color", "luma", it) }
            o("colorCss") { call("color", "css", it) }
            r("colorHex") { r, g, b -> call("color", "hex", r, g, b) }
        }

        private fun time() {
            z("year") { call("time", "year") }
            z("month") { call("time", "month") }
            z("day") { call("time", "day") }
            z("hour") { call("time", "hour") }
            z("weekday") { call("time", "weekday") }
            z("startOfDay") { call("time", "start_of_day") }
            o("since") { call("time", "since", it) }
            o("isFuture") { call("time", "is_future", it) }
            o("isPast") { call("time", "is_past", it) }
            t("addSeconds") { ts, delta -> call("time", "add_seconds", ts, delta) }
        }

        private fun collection() {
            z("collection") { LuaFn.invoke(luna.get("Collection"), "new") }
            o("collectionFrom") { LuaFn.invoke(luna.get("Collection"), "from", it) }
        }

        private fun ui() {
            o("page") { call("ui", "page", it) }
            t("section") { title, items -> call("ui", "section", title, items) }
            o("heading") { call("ui", "heading", it) }
            o("alert") { call("ui", "alert", it) }
            o("note") { call("ui", "note", it) }
            o("code") { call("ui", "code", it) }
            o("textarea") { call("ui", "textarea", it) }
            o("toggle") { call("ui", "switch", it) }
            o("button") { call("ui", "button", it) }
            o("kv") { call("ui", "kv", it) }
            o("progress") { call("ui", "progress", it) }
            o("link") { call("ui", "link", it) }
            o("badge") { call("ui", "badge", it) }
            o("divider") { call("ui", "divider", it) }
            o("spacer") { call("ui", "spacer", it) }
            o("checkbox") { call("ui", "checkbox", it) }
            o("numberField") { call("ui", "number", it) }
            o("select") { call("ui", "select", it) }
            o("slider") { call("ui", "slider", it) }
            o("embed") { call("ui", "embed", it) }
        }

        private fun builders() {
            z("pageBuilder") { LuaFn.invoke(luna.get("PageBuilder"), "new") }
            z("embedBuilder") { LuaFn.invoke(luna.get("EmbedBuilder"), "new") }
            z("ruleBuilder") { LuaFn.invoke(luna.get("RuleBuilder"), "new") }
            z("hostsBuilder") { LuaFn.invoke(luna.get("HostsBuilder"), "new") }
        }

        private fun rest() {
            val rest = restTable()
            client.set("rest", rest)
            o("restGet") { rest.get("get").call(it) }
            t("restPut") { path, body -> rest.get("put").call(path, body) }
            t("restPost") { path, body -> rest.get("post").call(path, body) }
            t("restPatch") { path, body -> rest.get("patch").call(path, body) }
            o("restDelete") { rest.get("delete").call(it) }
        }

        private fun restTable(): LuaTable {
            fun route(path: String): Pair<String, String> {
                val trimmed = path.trim().trim('/').lowercase()
                val head = trimmed.substringBefore('/')
                val tail = trimmed.substringAfter('/', "")
                return head to tail
            }
            return LuaFn.module(
                "get" to LuaFn.o { raw ->
                    val (head, tail) = route(raw.tojstring())
                    when (head) {
                        "me", "user" -> call("user", "toJSON")
                        "vpn" -> when (tail) {
                            "", "snapshot", "stats" -> call("vpn", "snapshot")
                            "phase", "state" -> call("vpn", "phase")
                            else -> throw LuaError("Unknown GET route: vpn/$tail")
                        }
                        "rules" -> if (tail.isEmpty()) call("rules", "list") else call("rules", "get", LuaValue.valueOf(tail))
                        "hosts" -> if (tail.isEmpty()) call("hosts", "list") else call("hosts", "get", LuaValue.valueOf(tail))
                        "app" -> when (tail) {
                            "", "config" -> call("app", "config")
                            "mode", "mtu", "version", "locale" -> call("app", tail)
                            "dns_mode", "ipv6_mode", "log_level", "per_app_mode", "block_quic" -> call("app", tail)
                            else -> throw LuaError("Unknown GET route: app/$tail")
                        }
                        "storage" -> if (tail.isEmpty()) call("storage", "keys") else call("storage", "get", LuaValue.valueOf(tail))
                        "permissions" -> call("permissions", "toArray")
                        else -> throw LuaError("Unknown GET route: $head")
                    }
                },
                "put" to LuaFn.t { path, body ->
                    val (head, tail) = route(path.tojstring())
                    when (head) {
                        "hosts" -> if (body.isstring()) call("hosts", "set_text", body) else call("hosts", "set", body)
                        "storage" -> {
                            if (tail.isEmpty()) throw LuaError("PUT storage requires /storage/{key}")
                            call("storage", "set", LuaValue.valueOf(tail), body)
                        }
                        "rules" -> call("rules", "upsert", body)
                        else -> throw LuaError("Unknown PUT route: $head")
                    }
                },
                "post" to LuaFn.t { path, body ->
                    val (head, _) = route(path.tojstring())
                    when (head) {
                        "rules" -> call("rules", "create", body)
                        "hosts" -> call("hosts", "set", body)
                        "notify" -> {
                            val payload = if (body.istable()) body.checktable() else LuaTable().also { it.set("text", body) }
                            call("notify", "show", LuaValue.valueOf(payload.get("title").optjstring("Plugin")), LuaValue.valueOf(payload.get("text").optjstring("")))
                        }
                        "vpn" -> {
                            val action = body.tojstring()
                            if (action == "start" || action == "connect") call("vpn", "request_start")
                            else if (action == "stop" || action == "disconnect") call("vpn", "request_stop")
                            else throw LuaError("POST vpn body must be start or stop")
                        }
                        else -> throw LuaError("Unknown POST route: $head")
                    }
                },
                "patch" to LuaFn.t { path, body ->
                    val (head, tail) = route(path.tojstring())
                    when {
                        head == "rules" && tail.isNotEmpty() -> call("rules", "edit", LuaValue.valueOf(tail), body)
                        else -> throw LuaError("Unknown PATCH route: $head")
                    }
                },
                "delete" to LuaFn.o { raw ->
                    val (head, tail) = route(raw.tojstring())
                    when (head) {
                        "rules" -> if (tail.isEmpty()) call("rules", "clear") else call("rules", "delete", LuaValue.valueOf(tail))
                        "hosts" -> if (tail.isEmpty()) call("hosts", "clear") else call("hosts", "remove", LuaValue.valueOf(tail))
                        "storage" -> {
                            if (tail.isEmpty()) throw LuaError("DELETE storage requires /storage/{key}")
                            call("storage", "remove", LuaValue.valueOf(tail))
                        }
                        else -> throw LuaError("Unknown DELETE route: $head")
                    }
                },
            )
        }

        private fun util() {
            z("guid") { call("util", "guid") }
            r("clamp") { n, min, max -> call("util", "clamp", n, min, max) }
            r("lerp") { a, b, t -> call("util", "lerp", a, b, t) }
            o("round") { call("util", "round", it) }
            t("coalesce") { a, b -> call("util", "coalesce", a, b) }
            o("typeof") { call("util", "typeof", it) }
            t("min") { a, b -> call("util", "min", a, b) }
            t("max") { a, b -> call("util", "max", a, b) }
        }

        private fun hostsLineOk(raw: String): Boolean {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return true
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return false
            if (HostsFile.parseIpv4(parts[0]) == null) return false
            return DomainValidator.isValidPattern(DomainValidator.normalize(parts[1]))
        }
    }
}
