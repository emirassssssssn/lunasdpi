package com.lunasdev.lunasdpi.plugin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.pluginStore by preferencesDataStore(name = "luna_plugins")

class PluginRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val records: Flow<List<InstalledPluginRecord>> = appContext.pluginStore.data.map { prefs ->
        decode(prefs[KEY])
    }

    fun pluginsDir(): File = File(appContext.filesDir, "plugins")

    fun pluginDir(id: String): File {
        check(PluginSecurity.validateId(id) == null) { "Invalid plugin id." }
        return File(pluginsDir(), id)
    }

    suspend fun current(): List<InstalledPluginRecord> = records.first()

    suspend fun setRecords(items: List<InstalledPluginRecord>) {
        appContext.pluginStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(ListSerializer(InstalledPluginRecord.serializer()), items)
        }
    }

    suspend fun upsert(record: InstalledPluginRecord) {
        setRecords(
            current().filterNot { it.id == record.id } + record,
        )
    }

    suspend fun remove(id: String) {
        setRecords(current().filterNot { it.id == id })
        pluginDir(id).deleteRecursively()
    }

    fun loadManifest(id: String): ValidatedManifest? {
        val file = File(pluginDir(id), "manifest.json")
        if (!file.isFile) return null
        val files = pluginDir(id).walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(pluginDir(id)).path.replace('\\', '/') }
            .toSet()
        return runCatching { PluginManifestParser.parse(file.readText(), files) }.getOrNull()
    }

    suspend fun installed(): List<InstalledPlugin> {
        return current().mapNotNull { record ->
            val manifest = loadManifest(record.id) ?: return@mapNotNull null
            InstalledPlugin(record = record, manifest = manifest)
        }
    }

    private fun decode(raw: String?): List<InstalledPluginRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(InstalledPluginRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        private val KEY = stringPreferencesKey("installed")
    }
}
