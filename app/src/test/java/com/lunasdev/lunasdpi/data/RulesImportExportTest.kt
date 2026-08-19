package com.lunasdev.lunasdpi.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.data.model.DpiConfig
import com.lunasdev.lunasdpi.data.model.DpiMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RulesImportExportTest {
    @Test
    fun importRejectsUrlsAndKeepsKnownFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = RulesRepository(context)
        val json = """
            {
              "version": 1,
              "rules": [
                {
                  "name": "Discord",
                  "enabled": true,
                  "strategy": "balanced",
                  "domains": ["discord.com", "*.discord.com", "https://evil.example", "discord.com/path"]
                }
              ]
            }
        """.trimIndent()
        val result = repo.importJson(json).getOrThrow()
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Discord")
        assertThat(result[0].strategy).isEqualTo(DpiMode.BALANCED)
        assertThat(result[0].domains).containsExactly("discord.com", "*.discord.com")
    }

    @Test
    fun configClampsImpossibleSizes() {
        val cfg = DpiConfig(fragmentSize = 9999, mtu = 50, logLevel = 99).validated()
        assertThat(cfg.fragmentSize).isEqualTo(DpiConfig.MAX_FRAGMENT)
        assertThat(cfg.mtu).isEqualTo(DpiConfig.MIN_MTU)
        assertThat(cfg.logLevel).isEqualTo(3)
    }

    @Test
    fun migrateBlocksIpv6AndQuic() {
        val cfg = DpiConfig(settingsRevision = 0).migrated()
        assertThat(cfg.ipv6Mode).isEqualTo(com.lunasdev.lunasdpi.data.model.Ipv6Mode.BLOCK)
        assertThat(cfg.blockQuic).isTrue()
        assertThat(cfg.settingsRevision).isEqualTo(DpiConfig.CURRENT_REVISION)
        assertThat(DpiConfig.AUTOMATIC_RESOLVERS).contains("8.8.8.8")
    }

    @Test
    fun mergeBuiltInDiscordAddsGatewayWildcard() {
        val old = listOf(
            com.lunasdev.lunasdpi.data.model.DomainRule(
                id = "preset-discord",
                name = "Discord",
                domains = listOf("discord.com", "*.discord.com"),
            ),
        )
        val merged = RulesRepository.mergeBuiltInDiscord(old)
        assertThat(merged.single().domains).contains("*.discord.gg")
        assertThat(merged.single().strategy).isEqualTo(DpiMode.AGGRESSIVE)
    }

    @Test
    fun upsertThenDeleteRemovesRule() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = RulesRepository(context)
        val rule = com.lunasdev.lunasdpi.data.model.DomainRule(
            id = "custom-rule",
            name = "Custom",
            domains = listOf("example.com"),
        )
        repo.upsert(rule)
        assertThat(repo.current().any { it.id == "custom-rule" }).isTrue()
        repo.delete("custom-rule")
        assertThat(repo.current().any { it.id == "custom-rule" }).isFalse()
    }

    @Test
    fun setAllEnabledTogglesEveryRule() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = RulesRepository(context)
        repo.upsert(
            com.lunasdev.lunasdpi.data.model.DomainRule(
                id = "a",
                name = "A",
                enabled = true,
                domains = listOf("a.com"),
            ),
        )
        repo.upsert(
            com.lunasdev.lunasdpi.data.model.DomainRule(
                id = "b",
                name = "B",
                enabled = true,
                domains = listOf("b.com"),
            ),
        )
        repo.setAllEnabled(false)
        assertThat(repo.current().filter { it.id == "a" || it.id == "b" }.all { !it.enabled }).isTrue()
    }
}
