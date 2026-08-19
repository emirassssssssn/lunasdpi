package com.lunasdev.lunasdpi.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Ipv6Mode {
    OFF,
    BLOCK,
    ;

    fun nativeOrdinal(): Int = when (this) {
        OFF -> 0
        BLOCK -> 1
    }

    companion object {
        fun fromStorage(value: String): Ipv6Mode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BLOCK
    }
}
