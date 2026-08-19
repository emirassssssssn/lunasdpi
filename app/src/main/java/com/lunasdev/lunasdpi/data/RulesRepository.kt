package com.lunasdev.lunasdpi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.DpiMode
import com.lunasdev.lunasdpi.data.model.ExportedRule
import com.lunasdev.lunasdpi.data.model.RulesFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.rulesStore by preferencesDataStore(name = "luna_rules")

class RulesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val rules: Flow<List<DomainRule>> = appContext.rulesStore.data.map { prefs ->
        decode(prefs[KEY_RULES])
    }

    suspend fun current(): List<DomainRule> = rules.first()

    suspend fun setRules(rules: List<DomainRule>) {
        appContext.rulesStore.edit { prefs ->
            prefs[KEY_RULES] = json.encodeToString(ListSerializer(DomainRule.serializer()), rules)
        }
    }

    suspend fun update(transform: (List<DomainRule>) -> List<DomainRule>) {
        appContext.rulesStore.edit { prefs ->
            val next = transform(decode(prefs[KEY_RULES]))
            prefs[KEY_RULES] = json.encodeToString(ListSerializer(DomainRule.serializer()), next)
        }
    }

    suspend fun upsert(rule: DomainRule) {
        val sanitized = rule.copy(
            name = rule.name.trim().ifBlank { "Custom rule" },
            domains = rule.domains.map { DomainValidator.normalize(it) }
                .filter { DomainValidator.isValidPattern(it) }
                .distinct(),
            fragmentSize = rule.fragmentSize.coerceIn(1, 256),
        )
        update { current ->
            val next = current.toMutableList()
            val index = next.indexOfFirst { it.id == sanitized.id }
            if (index >= 0) next[index] = sanitized else next.add(sanitized)
            next
        }
    }

    suspend fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        update { current -> current.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    suspend fun setAllEnabled(enabled: Boolean) {
        update { current -> current.map { it.copy(enabled = enabled) } }
    }

    fun exportJson(rules: List<DomainRule>): String {
        val file = RulesFile(
            rules = rules.map { rule ->
                ExportedRule(
                    name = rule.name,
                    enabled = rule.enabled,
                    strategy = rule.strategy.name.lowercase(),
                    domains = rule.domains,
                    tcpFragmentation = rule.tcpFragmentation,
                    fragmentSize = rule.fragmentSize,
                    httpHostCase = rule.httpHostCase,
                    httpSpacing = rule.httpSpacing,
                    httpMethodSpacing = rule.httpMethodSpacing,
                )
            },
        )
        return prettyJson.encodeToString(RulesFile.serializer(), file)
    }

    fun importJson(raw: String): Result<List<DomainRule>> {
        return runCatching {
            val file = json.decodeFromString(RulesFile.serializer(), raw)
            file.rules.mapNotNull { item ->
                val domains = item.domains.map { DomainValidator.normalize(it) }
                    .filter { DomainValidator.isValidPattern(it) }
                if (item.name.isBlank() || domains.isEmpty()) {
                    null
                } else {
                    DomainRule(
                        name = item.name.trim(),
                        enabled = item.enabled,
                        strategy = DpiMode.fromStorage(item.strategy),
                        domains = domains,
                        tcpFragmentation = item.tcpFragmentation ?: true,
                        fragmentSize = (item.fragmentSize ?: 2).coerceIn(1, 256),
                        httpHostCase = item.httpHostCase ?: true,
                        httpSpacing = item.httpSpacing ?: false,
                        httpMethodSpacing = item.httpMethodSpacing ?: false,
                    )
                }
            }
        }
    }

    private fun decode(raw: String?): List<DomainRule> {
        val loaded = if (raw.isNullOrBlank()) {
            listOf(ServicePresets.discord())
        } else {
            runCatching { json.decodeFromString(ListSerializer(DomainRule.serializer()), raw) }
                .getOrElse { listOf(ServicePresets.discord()) }
        }
        return mergeBuiltInDiscord(loaded)
    }

    companion object {
        private val KEY_RULES = stringPreferencesKey("domain_rules_json")

        fun mergeBuiltInDiscord(rules: List<DomainRule>): List<DomainRule> {
            val latest = ServicePresets.discord()
            var found = false
            val merged = rules.map { rule ->
                if (!rule.name.equals("Discord", ignoreCase = true)) {
                    rule
                } else {
                    found = true
                    val domains = (rule.domains + latest.domains)
                        .map { DomainValidator.normalize(it) }
                        .filter { DomainValidator.isValidPattern(it) }
                        .distinct()
                    val strategy = if (rule.strategy == DpiMode.BALANCED || rule.strategy == DpiMode.AUTOMATIC) {
                        DpiMode.AGGRESSIVE
                    } else {
                        rule.strategy
                    }
                    rule.copy(domains = domains, strategy = strategy)
                }
            }
            return if (found) merged else rules
        }
    }
}
