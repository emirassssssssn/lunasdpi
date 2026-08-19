package com.lunasdev.lunasdpi.data

import com.lunasdev.lunasdpi.data.model.DomainRule

data class DomainMatch(
    val ruleId: String? = null,
    val ruleName: String? = null,
)

class DomainMatcher(private val rules: List<DomainRule>) {
    private val exact = HashMap<String, DomainRule>()
    private val wildcard = HashMap<String, DomainRule>()

    init {
        for (rule in rules) {
            if (!rule.enabled) continue
            for (raw in rule.domains) {
                val n = DomainValidator.normalize(raw)
                if (n.startsWith("*.")) {
                    wildcard[n.substring(2)] = rule
                } else if (n.isNotEmpty()) {
                    exact[n] = rule
                }
            }
        }
    }

    fun match(domain: String): DomainMatch {
        val n = DomainValidator.normalize(domain)
        if (n.isEmpty()) return DomainMatch()
        exact[n]?.let { return DomainMatch(it.id, it.name) }
        var start = 0
        while (true) {
            val dot = n.indexOf('.', start)
            if (dot < 0) break
            val suffix = n.substring(dot + 1)
            wildcard[suffix]?.let { return DomainMatch(it.id, it.name) }
            start = dot + 1
        }
        return DomainMatch()
    }
}
