package com.lunasdev.lunasdpi.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordClientsTest {
    @Test
    fun matchesOfficialAndModdedClients() {
        assertThat(DiscordClients.matches("com.discord")).isTrue()
        assertThat(DiscordClients.matches("app.revenge")).isTrue()
        assertThat(DiscordClients.matches("app.kettu")).isTrue()
        assertThat(DiscordClients.matches("com.discord.canary")).isTrue()
        assertThat(DiscordClients.matches("dev.beefers.vendetta")).isTrue()
    }

    @Test
    fun ignoresManagersAndUnrelatedApps() {
        assertThat(DiscordClients.matches("app.revenge.manager")).isFalse()
        assertThat(DiscordClients.matches("cocobo1.pupu.manager")).isFalse()
        assertThat(DiscordClients.matches("com.lunasdev.lunasdpi")).isFalse()
        assertThat(DiscordClients.matches("")).isFalse()
    }

    @Test
    fun selectedPackageWinsOverHeuristics() {
        assertThat(DiscordClients.shouldWatch("com.discord", "app.kettu")).isFalse()
        assertThat(DiscordClients.shouldWatch("app.kettu", "app.kettu")).isTrue()
        assertThat(DiscordClients.shouldWatch("com.discord", "com.discord")).isTrue()
        assertThat(DiscordClients.shouldWatch("com.my.kettu.build", "com.my.kettu.build")).isTrue()
    }

    @Test
    fun emptySelectionFallsBackToKnownClients() {
        assertThat(DiscordClients.shouldWatch("com.discord", "")).isTrue()
        assertThat(DiscordClients.shouldWatch("com.android.chrome", "")).isFalse()
    }

    @Test
    fun looksLikeClientUsesLabelHints() {
        assertThat(DiscordClients.looksLikeClient("abc.custom", "Kettu")).isTrue()
        assertThat(DiscordClients.looksLikeClient("abc.custom", "Chrome")).isFalse()
        assertThat(DiscordClients.looksLikeClient("cocobo1.pupu.manager", "Kettu Manager")).isFalse()
    }

    @Test
    fun transientUiIgnoresSystemAndIme() {
        assertThat(DiscordClients.isTransientUi("com.android.systemui")).isTrue()
        assertThat(DiscordClients.isTransientUi("com.google.android.permissioncontroller")).isTrue()
        assertThat(DiscordClients.isTransientUi("com.google.android.inputmethod.latin")).isTrue()
        assertThat(DiscordClients.isTransientUi("com.lunasdev.lunasdpi")).isTrue()
        assertThat(DiscordClients.isTransientUi("com.android.chrome")).isFalse()
        assertThat(DiscordClients.isTransientUi("com.sec.android.app.launcher")).isFalse()
    }

    @Test
    fun classifyForegroundKeepsDiscordOverSystemUi() {
        assertThat(
            DiscordClients.classifyForeground(
                listOf("com.android.systemui", "com.discord"),
                "com.discord",
            ),
        ).isEqualTo(ForegroundKind.Discord)
        assertThat(
            DiscordClients.classifyForeground(
                listOf("com.android.systemui"),
                "com.discord",
            ),
        ).isEqualTo(ForegroundKind.Transient)
        assertThat(
            DiscordClients.classifyForeground(
                listOf("com.android.chrome"),
                "com.discord",
            ),
        ).isEqualTo(ForegroundKind.Other)
        assertThat(
            DiscordClients.classifyForeground(emptyList(), "com.discord"),
        ).isEqualTo(ForegroundKind.Transient)
    }
}
