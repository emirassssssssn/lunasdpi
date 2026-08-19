package com.lunasdev.lunasdpi.data

import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.DpiMode

object ServicePresets {
    fun discord(): DomainRule = DomainRule(
        id = "preset-discord",
        name = "Discord",
        enabled = true,
        strategy = DpiMode.AGGRESSIVE,
        domains = listOf(
            "discord.com",
            "*.discord.com",
            "discord.gg",
            "*.discord.gg",
            "discordapp.com",
            "*.discordapp.com",
            "discordapp.net",
            "*.discordapp.net",
            "discord.media",
            "*.discord.media",
            "discord.co",
            "*.discord.co",
            "discordcdn.com",
            "*.discordcdn.com",
            "discord.dev",
            "dis.gd",
            "discord.gift",
            "*.discord.gift",
            "discordstatus.com",
        ),
    )

    fun gaming(): DomainRule = DomainRule(
        id = "preset-gaming",
        name = "Gaming",
        enabled = true,
        strategy = DpiMode.BALANCED,
        domains = listOf(
            "steampowered.com",
            "*.steampowered.com",
            "steamcommunity.com",
            "*.riotgames.com",
        ),
    )

    fun socialMedia(): DomainRule = DomainRule(
        id = "preset-social",
        name = "Social Media",
        enabled = true,
        strategy = DpiMode.BALANCED,
        domains = listOf(
            "x.com",
            "*.x.com",
            "twitter.com",
            "*.twitter.com",
            "instagram.com",
            "*.instagram.com",
        ),
    )

    fun messaging(): DomainRule = DomainRule(
        id = "preset-messaging",
        name = "Messaging",
        enabled = true,
        strategy = DpiMode.BALANCED,
        domains = listOf(
            "telegram.org",
            "*.telegram.org",
            "t.me",
            "whatsapp.com",
            "*.whatsapp.com",
            "signal.org",
        ),
    )

    fun all(): List<DomainRule> = listOf(discord(), gaming(), socialMedia(), messaging())
}
