package com.lunasdev.lunasdpi.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.ErrorState
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.LunaSwitch
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.components.SecondaryButton
import com.lunasdev.lunasdpi.ui.components.SectionHeader
import com.lunasdev.lunasdpi.ui.components.SettingRow
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.PluginsViewModel

@Composable
fun PluginDetailScreen(
    pluginId: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onUninstalled: () -> Unit,
    vm: PluginsViewModel = viewModel(),
) {
    val plugins by vm.plugins.collectAsStateWithLifecycle()
    val plugin = plugins.find { it.manifest.id == pluginId }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = LunaTheme.colors
    LunaScaffold(
        title = plugin?.manifest?.name ?: stringResource(R.string.plugins),
        onBack = onBack,
        subtitle = plugin?.manifest?.id,
    ) { padding ->
        if (plugin == null) {
            Column(Modifier.padding(padding).padding(horizontal = LunaSpacing.screen)) {
                ErrorState(
                    title = stringResource(R.string.plugins_missing_title),
                    body = stringResource(R.string.plugins_missing_body),
                )
            }
            return@LunaScaffold
        }
        val dir = vm.pluginDir(plugin.manifest.id)
        var logTick by remember { mutableStateOf(0) }
        val log = remember(plugin.record.updatedAt, plugin.record.lastError, logTick) { vm.log(plugin.manifest.id) }
        val canSettings = plugin.record.enabled &&
            plugin.manifest.settings != null &&
            PluginPermission.UI_SETTINGS in plugin.manifest.permissions
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginRepoHeader(
                manifest = plugin.manifest,
                dir = dir,
                version = plugin.manifest.version,
                enabled = plugin.record.enabled,
                error = plugin.record.lastError.isNotBlank(),
            )
            if (plugin.record.lastError.isNotBlank()) {
                ErrorState(title = stringResource(R.string.plugins_error_badge), body = plugin.record.lastError)
            }
            AppCard {
                SettingRow(
                    title = stringResource(R.string.plugins_enable),
                    description = stringResource(R.string.plugins_enable_desc),
                ) {
                    LunaSwitch(
                        checked = plugin.record.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                vm.setEnabled(
                                    plugin.manifest.id,
                                    true,
                                    plugin.manifest.permissions.map { it.manifestKey() },
                                )
                            } else {
                                vm.setEnabled(plugin.manifest.id, false)
                            }
                        },
                    )
                }
            }
            if (canSettings) {
                PrimaryButton(text = stringResource(R.string.plugins_open_settings), onClick = onSettings)
            } else if (plugin.manifest.settings != null && !plugin.record.enabled) {
                Text(
                    stringResource(R.string.plugins_settings_need_enable),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            if (plugin.record.enabled) {
                SecondaryButton(
                    text = stringResource(R.string.plugins_reload),
                    onClick = { vm.reload(plugin.manifest.id) },
                )
            }
            SectionHeader(stringResource(R.string.plugins_permissions))
            AppCard {
                if (plugin.manifest.permissions.isEmpty()) {
                    Text(
                        stringResource(R.string.plugins_permissions_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                } else {
                    plugin.manifest.permissions.forEachIndexed { index, permission ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        PermissionRow(permission = permission, granted = permission.manifestKey() in plugin.record.granted)
                    }
                }
            }
            SectionHeader(stringResource(R.string.plugins_integrity))
            AppCard {
                MetaRow(stringResource(R.string.plugins_id), plugin.manifest.id)
                Spacer(Modifier.height(8.dp))
                MetaRow(stringResource(R.string.plugins_api), plugin.manifest.apiLevel.toString())
                Spacer(Modifier.height(8.dp))
                MetaRow(stringResource(R.string.plugins_min_app), plugin.manifest.minAppVersion)
                if (plugin.record.sourceName.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MetaRow(stringResource(R.string.plugins_source), plugin.record.sourceName)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.plugins_hash), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        plugin.record.sha256.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                    )
                }
            }
            val homepage = plugin.manifest.homepage
            if (!homepage.isNullOrBlank()) {
                SecondaryButton(
                    text = stringResource(R.string.plugins_homepage),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(homepage))
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            SectionHeader(stringResource(R.string.plugins_log))
            AppCard {
                if (log.isBlank()) {
                    Text(
                        stringResource(R.string.plugins_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            if (log.isNotBlank()) {
                SecondaryButton(
                    text = stringResource(R.string.plugins_log_clear),
                    onClick = {
                        vm.clearLog(plugin.manifest.id)
                        logTick += 1
                    },
                )
            }
            SecondaryButton(text = stringResource(R.string.plugins_uninstall), onClick = { confirm = true })
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirm && plugin != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.plugins_uninstall)) },
            text = { Text(stringResource(R.string.plugins_uninstall_confirm, plugin.manifest.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.uninstall(plugin.manifest.id)
                        confirm = false
                        onUninstalled()
                    },
                ) { Text(stringResource(R.string.plugins_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = colors.card,
        )
    }
}

@Composable
private fun PermissionRow(permission: PluginPermission, granted: Boolean) {
    val colors = LunaTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(permission.titleRes()), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(permission.bodyRes()), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (granted) R.string.plugins_granted else R.string.plugins_not_granted),
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) colors.success else colors.textMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background((if (granted) colors.success else colors.textMuted).copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LunaTheme.colors.textMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
