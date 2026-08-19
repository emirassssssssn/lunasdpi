package com.lunasdev.lunasdpi.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class DnsMode {
    AUTOMATIC,
    SYSTEM,
    CUSTOM,
    ;

    fun nativeOrdinal(): Int = when (this) {
        AUTOMATIC -> 0
        SYSTEM -> 1
        CUSTOM -> 2
    }

    companion object {
        fun fromNative(value: Int): DnsMode = when (value) {
            1 -> SYSTEM
            2 -> CUSTOM
            else -> AUTOMATIC
        }

        fun fromStorage(value: String): DnsMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: AUTOMATIC
    }
}
