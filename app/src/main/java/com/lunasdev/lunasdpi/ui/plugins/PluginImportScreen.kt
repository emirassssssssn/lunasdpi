package com.lunasdev.lunasdpi.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.components.SecondaryButton
import com.lunasdev.lunasdpi.ui.components.SectionHeader
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.PluginsViewModel

@Composable
fun PluginImportScreen(
    onBack: () -> Unit,
    onInstalled: () -> Unit,
    vm: PluginsViewModel = viewModel(),
) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val plugins by vm.plugins.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(banner) {
        if (banner != null) onInstalled()
    }
    LaunchedEffect(error) {
        val text = error ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeError()
    }
    LunaScaffold(
        title = stringResource(R.string.plugins_review_title),
        onBack = {
            vm.cancelImport()
            onBack()
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val unpacked = pending
        if (unpacked == null) {
            Column(Modifier.padding(padding).padding(horizontal = LunaSpacing.screen)) {
                Text(stringResource(R.string.plugins_review_empty), color = LunaTheme.colors.textSecondary)
            }
            return@LunaScaffold
        }
        val replacing = plugins.any { it.manifest.id == unpacked.manifest.id }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginRepoHeader(
                manifest = unpacked.manifest,
                dir = java.io.File(""),
                version = unpacked.manifest.version,
                pngBytes = unpacked.manifest.icon?.let { unpacked.files[it] },
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.plugins_files, unpacked.files.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = LunaTheme.colors.textMuted,
                )
            }
            if (replacing) {
                AppCard {
                    Text(stringResource(R.string.plugins_replace_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.plugins_replace_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = LunaTheme.colors.textSecondary,
                    )
                }
            }
            AppCard {
                Text(stringResource(R.string.plugins_review_warning), style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textSecondary)
            }
            SectionHeader(stringResource(R.string.plugins_permissions))
            AppCard {
                val permissions = unpacked.manifest.permissions
                if (permissions.isEmpty()) {
                    Text(
                        stringResource(R.string.plugins_permissions_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = LunaTheme.colors.textSecondary,
                    )
                } else {
                    permissions.forEachIndexed { index, permission ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        PermissionBlock(permission)
                    }
                }
            }
            Text(
                stringResource(R.string.plugins_review_grant_note),
                style = MaterialTheme.typography.bodySmall,
                color = LunaTheme.colors.textMuted,
            )
            PrimaryButton(
                text = stringResource(if (replacing) R.string.plugins_replace else R.string.plugins_install),
                onClick = { vm.confirmImport() },
            )
            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = {
                    vm.cancelImport()
                    onBack()
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionBlock(permission: PluginPermission) {
    Text(stringResource(permission.titleRes()), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(permission.bodyRes()), style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textSecondary)
}
