package com.lunasdev.lunasdpi.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class DpiMode {
    AUTOMATIC,
    BASIC,
    BALANCED,
    AGGRESSIVE,
    CUSTOM,
    ;

    fun nativeOrdinal(): Int = when (this) {
        AUTOMATIC -> 0
        BASIC -> 1
        BALANCED -> 2
        AGGRESSIVE -> 3
        CUSTOM -> 4
    }

    companion object {
        fun fromNative(value: Int): DpiMode = when (value) {
            1 -> BASIC
            2 -> BALANCED
            3 -> AGGRESSIVE
            4 -> CUSTOM
            else -> AUTOMATIC
        }

        fun fromStorage(value: String): DpiMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: AUTOMATIC
    }
}
