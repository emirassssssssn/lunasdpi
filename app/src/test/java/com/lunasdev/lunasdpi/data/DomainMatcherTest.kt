package com.lunasdev.lunasdpi.data

import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.data.model.DomainRule
import org.junit.Test

class DomainMatcherTest {
    private val matcher = DomainMatcher(
        listOf(
            DomainRule(id = "d", name = "Discord", domains = listOf("discord.com", "*.discord.com", "discord.gg")),
            DomainRule(id = "e", name = "Example", domains = listOf("example.com")),
            DomainRule(id = "off", name = "Off", enabled = false, domains = listOf("disabled.com")),
        ),
    )

    @Test
    fun exactMatchDoesNotImplySubdomains() {
        assertThat(matcher.match("example.com").ruleName).isEqualTo("Example")
        assertThat(matcher.match("cdn.example.com").ruleId).isNull()
    }

    @Test
    fun wildcardMatchesSubdomainsOnly() {
        assertThat(matcher.match("discord.com").ruleName).isEqualTo("Discord")
        assertThat(matcher.match("cdn.discord.com").ruleName).isEqualTo("Discord")
        assertThat(matcher.match("gateway.discord.com").ruleName).isEqualTo("Discord")
    }

    @Test
    fun disabledRulesAreIgnored() {
        assertThat(matcher.match("disabled.com").ruleId).isNull()
    }

    @Test
    fun unmatchedHostsAreIgnored() {
        assertThat(matcher.match("instagram.com").ruleId).isNull()
        assertThat(matcher.match("telegram.org").ruleId).isNull()
    }

    @Test
    fun onlyEnabledRobloxDoesNotMatchDiscord() {
        val roblox = DomainMatcher(
            listOf(
                DomainRule(id = "r", name = "Roblox", enabled = true, domains = listOf("roblox.com", "*.roblox.com")),
                DomainRule(
                    id = "d",
                    name = "Discord",
                    enabled = false,
                    domains = listOf("discord.com", "*.discord.com"),
                ),
            ),
        )
        assertThat(roblox.match("roblox.com").ruleName).isEqualTo("Roblox")
        assertThat(roblox.match("discord.com").ruleId).isNull()
        assertThat(roblox.match("gateway.discord.com").ruleId).isNull()
    }

    @Test
    fun wildcardMatchesDiscordGgSubdomains() {
        val discord = DomainMatcher(
            listOf(
                DomainRule(
                    id = "d",
                    name = "Discord",
                    domains = listOf("discord.com", "*.discord.com", "*.discord.gg"),
                ),
            ),
        )
        assertThat(discord.match("gateway.discord.gg").ruleName).isEqualTo("Discord")
    }
}
