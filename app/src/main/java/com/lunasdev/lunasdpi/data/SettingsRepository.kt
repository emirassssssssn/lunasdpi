package com.lunasdev.lunasdpi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lunasdev.lunasdpi.data.model.DpiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "luna_settings")

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val config: Flow<DpiConfig> = appContext.settingsStore.data.map { prefs ->
        val raw = prefs[KEY_CONFIG]
        if (raw.isNullOrBlank()) {
            DpiConfig().migrated()
        } else {
            runCatching { json.decodeFromString(DpiConfig.serializer(), raw) }
                .getOrDefault(DpiConfig())
                .migrated()
        }
    }

    val onboardingDone: Flow<Boolean> = appContext.settingsStore.data.map { it[KEY_ONBOARDING] ?: false }

    suspend fun current(): DpiConfig {
        val loaded = config.first()
        if (loaded.settingsRevision < DpiConfig.CURRENT_REVISION) {
            update { loaded }
            return config.first()
        }
        return loaded
    }

    suspend fun isOnboardingDone(): Boolean = onboardingDone.first()

    suspend fun update(transform: (DpiConfig) -> DpiConfig) {
        appContext.settingsStore.edit { prefs ->
            val current = prefs[KEY_CONFIG]?.let {
                runCatching { json.decodeFromString(DpiConfig.serializer(), it) }.getOrNull()
            }?.migrated() ?: DpiConfig().migrated()
            prefs[KEY_CONFIG] = json.encodeToString(
                DpiConfig.serializer(),
                transform(current).validated().copy(settingsRevision = DpiConfig.CURRENT_REVISION),
            )
        }
    }

    suspend fun setOnboardingDone() {
        appContext.settingsStore.edit { it[KEY_ONBOARDING] = true }
    }

    companion object {
        private val KEY_CONFIG = stringPreferencesKey("dpi_config_json")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
    }
}
