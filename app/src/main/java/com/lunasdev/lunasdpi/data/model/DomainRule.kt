package com.lunasdev.lunasdpi.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DomainRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val domains: List<String> = emptyList(),
    val strategy: DpiMode = DpiMode.AUTOMATIC,
    val tcpFragmentation: Boolean = true,
    val fragmentSize: Int = 2,
    val httpHostCase: Boolean = true,
    val httpSpacing: Boolean = false,
    val httpMethodSpacing: Boolean = false,
    val persistentFragment: Boolean = false,
)

@Serializable
data class RulesFile(
    val version: Int = 1,
    val rules: List<ExportedRule> = emptyList(),
)

@Serializable
data class ExportedRule(
    val name: String,
    val enabled: Boolean = true,
    val strategy: String = "automatic",
    val domains: List<String> = emptyList(),
    val tcpFragmentation: Boolean? = null,
    val fragmentSize: Int? = null,
    val httpHostCase: Boolean? = null,
    val httpSpacing: Boolean? = null,
    val httpMethodSpacing: Boolean? = null,
)
