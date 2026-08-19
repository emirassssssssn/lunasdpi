package com.lunasdev.lunasdpi.plugin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lunasdev.lunasdpi.BuildConfig
import com.lunasdev.lunasdpi.data.RulesRepository
import com.lunasdev.lunasdpi.data.SettingsRepository
import com.lunasdev.lunasdpi.data.VpnStateRepository
import com.lunasdev.lunasdpi.vpn.VpnController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PluginHost(
    context: Context,
    private val scope: CoroutineScope,
    val registry: PluginRegistry,
    rules: RulesRepository,
    vpn: VpnController,
    private val vpnState: VpnStateRepository,
    private val hosts: HostsStore,
    settings: SettingsRepository,
) {
    private val app = context.applicationContext
    val logs = PluginLogStore(File(app.filesDir, "plugin-logs"))
    val notifier = PluginNotifier(app)
    val runtime = PluginRuntime(app, scope, registry, rules, vpn, vpnState, logs, notifier, hosts, settings)

    private val _pending = MutableStateFlow<UnpackedPlugin?>(null)
    val pending: StateFlow<UnpackedPlugin?> = _pending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun start() {
        scope.launch {
            registry.records.collect { records ->
                runtime.reconcile(records)
            }
        }
        scope.launch {
            vpnState.phase.collect { phase ->
                runtime.onVpnPhase(phase)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun stageBytes(bytes: ByteArray, sourceName: String) {
        val unpacked = PluginPackageImporter.unpack(bytes).copy(sourceName = sourceName)
        if (!PluginSecurity.appMeetsMinVersion(BuildConfig.VERSION_NAME, unpacked.manifest.minAppVersion)) {
            error("This plugin needs Lunas DPI ${unpacked.manifest.minAppVersion}.")
        }
        _pending.value = unpacked
        _message.value = null
    }

    fun stageUri(uri: Uri) {
        val name = displayName(uri)
        val bytes = app.contentResolver.openInputStream(uri)?.use { stream ->
            readLimited(stream, PluginSecurity.MAX_ARCHIVE_BYTES.toInt())
        } ?: error("Could not read the plugin file.")
        stageBytes(bytes, name)
    }

    fun cancelImport() {
        _pending.value = null
    }

    suspend fun confirmImport() {
        val unpacked = _pending.value ?: error("Nothing to install.")
        _pending.value = null
        try {
            val existing = registry.current()
            if (existing.size >= PluginSecurity.MAX_INSTALLED && existing.none { it.id == unpacked.manifest.id }) {
                error("You already have ${PluginSecurity.MAX_INSTALLED} plugins.")
            }
            val previous = existing.find { it.id == unpacked.manifest.id }
            PluginPackageImporter.installTo(registry.pluginDir(unpacked.manifest.id), unpacked)
            val requested = unpacked.manifest.permissions.map { it.manifestKey() }
            val granted = if (previous != null && requested.all { it in previous.granted }) {
                previous.granted
            } else {
                emptyList()
            }
            val enabled = previous?.enabled == true && granted.isNotEmpty() && requested.all { it in granted }
            registry.upsert(
                InstalledPluginRecord(
                    id = unpacked.manifest.id,
                    enabled = enabled,
                    granted = granted,
                    installedAt = previous?.installedAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    sha256 = unpacked.sha256,
                    sourceName = unpacked.sourceName.ifBlank { previous?.sourceName.orEmpty() },
                    lastError = "",
                ),
            )
            _message.value = unpacked.manifest.name
        } catch (error: Throwable) {
            _pending.value = unpacked
            throw error
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean, granted: List<String>? = null) {
        val record = registry.current().find { it.id == id } ?: return
        if (enabled) {
            val enabledCount = registry.current().count { it.enabled && it.id != id }
            if (enabledCount >= PluginSecurity.MAX_ENABLED) {
                error("At most ${PluginSecurity.MAX_ENABLED} plugins can run at once.")
            }
        }
        val nextGranted = granted ?: record.granted
        if (enabled && nextGranted.isEmpty()) {
            error("Grant the listed permissions before enabling this plugin.")
        }
        if (!enabled) {
            runtime.dropOwnedRules(id)
            hosts.clearPlugin(id)
        }
        registry.upsert(
            record.copy(
                enabled = enabled,
                granted = nextGranted,
                lastError = if (enabled) "" else record.lastError,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun uninstall(id: String) {
        runtime.dropOwnedRules(id)
        hosts.clearPlugin(id)
        PluginStorage(app, id).clear()
        logs.clear(id)
        registry.remove(id)
    }

    private fun displayName(uri: Uri): String {
        val fallback = uri.lastPathSegment.orEmpty()
        return runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else fallback
            } ?: fallback
        }.getOrDefault(fallback)
    }

    companion object {
        fun readLimited(stream: InputStream, max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8_192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > max) error("Plugin archive is larger than 2 MB.")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
