package com.lunasdev.lunasdpi.data.model

enum class VpnPhase {
    DISCONNECTED,
    REQUESTING_PERMISSION,
    CONNECTING,
    CONNECTED,
    STOPPING,
    ERROR,
}

data class UserFacingError(
    val title: String,
    val message: String,
    val technicalDetails: String? = null,
)

data class EngineSnapshot(
    val packetsProcessed: Long = 0,
    val packetsModified: Long = 0,
    val packetsDropped: Long = 0,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val dnsQueries: Long = 0,
    val activeTcp: Int = 0,
    val activeUdp: Int = 0,
    val nativeErrors: Int = 0,
    val lastError: String = "",
    val currentStrategy: String = "automatic",
    val engineAlive: Boolean = false,
    val tunActive: Boolean = false,
    val uptimeSeconds: Long = 0,
)
