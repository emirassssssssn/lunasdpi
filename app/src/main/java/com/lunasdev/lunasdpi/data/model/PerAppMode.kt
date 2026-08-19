package com.lunasdev.lunasdpi.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class PerAppMode {
    ALL,
    SELECTED,
    EXCLUDED,
    ;

    companion object {
        fun fromStorage(value: String): PerAppMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ALL
    }
}
