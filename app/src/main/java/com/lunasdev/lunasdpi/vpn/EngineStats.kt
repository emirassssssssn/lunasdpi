package com.lunasdev.lunasdpi.vpn

data class EngineStats(
    val packetsProcessed: Long,
    val packetsModified: Long,
    val packetsDropped: Long,
    val bytesIn: Long,
    val bytesOut: Long,
    val dnsQueries: Long,
    val activeTcp: Int,
    val activeUdp: Int,
    val nativeErrors: Int,
    val lastError: String,
    val currentStrategy: String,
    val engineAlive: Boolean,
)
