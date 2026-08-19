package com.lunasdev.lunasdpi.ui.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.EmptyState
import com.lunasdev.lunasdpi.ui.components.Glyph
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.LunaTextField
import com.lunasdev.lunasdpi.ui.settings.ChoiceChip
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.PluginsViewModel

private enum class PluginFilter { All, On, Off }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginHubScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onImportReady: () -> Unit,
    vm: PluginsViewModel = viewModel(),
) {
    val plugins by vm.plugins.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val colors = LunaTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PluginFilter.All) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }
    val imported = banner?.let { stringResource(R.string.plugins_imported, it) }
    LaunchedEffect(imported) {
        if (imported != null) {
            snackbar.showSnackbar(imported)
            vm.consumeBanner()
        }
    }
    LaunchedEffect(error) {
        val text = error ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeError()
    }
    LaunchedEffect(pending) {
        if (pending != null) onImportReady()
    }
    val visible = remember(plugins, query, filter) {
        val needle = query.trim()
        plugins.filter { plugin ->
            val matchesFilter = when (filter) {
                PluginFilter.All -> true
                PluginFilter.On -> plugin.record.enabled
                PluginFilter.Off -> !plugin.record.enabled
            }
            val matchesQuery = needle.isEmpty() ||
                plugin.manifest.name.contains(needle, ignoreCase = true) ||
                plugin.manifest.author.contains(needle, ignoreCase = true) ||
                plugin.manifest.id.contains(needle, ignoreCase = true) ||
                plugin.manifest.description.contains(needle, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }
    LunaScaffold(
        title = stringResource(R.string.plugins),
        onBack = onBack,
        subtitle = stringResource(R.string.plugins_subtitle),
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }) {
                Icon(Icons.Outlined.FileOpen, contentDescription = stringResource(R.string.plugins_import))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ) {
                Icon(Icons.Outlined.FileOpen, contentDescription = stringResource(R.string.plugins_import))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = LunaSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val running = plugins.count { it.record.enabled && it.record.lastError.isBlank() }
                val failed = plugins.count { it.record.lastError.isNotBlank() }
                AppCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PluginHubStat(value = plugins.size.toString(), label = stringResource(R.string.plugins_stat_installed))
                        PluginHubStat(value = running.toString(), label = stringResource(R.string.plugins_stat_running), accent = true)
                        PluginHubStat(value = failed.toString(), label = stringResource(R.string.plugins_stat_failed), error = failed > 0)
                    }
                }
            }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Glyph(Icons.Outlined.Shield)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.plugins_security_title), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.plugins_security_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            item {
                LunaTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.plugins_search),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ChoiceChip(
                        selected = filter == PluginFilter.All,
                        label = stringResource(R.string.plugins_filter_all),
                        onClick = { filter = PluginFilter.All },
                    )
                    ChoiceChip(
                        selected = filter == PluginFilter.On,
                        label = stringResource(R.string.plugins_filter_on),
                        onClick = { filter = PluginFilter.On },
                    )
                    ChoiceChip(
                        selected = filter == PluginFilter.Off,
                        label = stringResource(R.string.plugins_filter_off),
                        onClick = { filter = PluginFilter.Off },
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(
                            if (plugins.isEmpty()) R.string.plugins_empty_title else R.string.plugins_empty_filter_title,
                        ),
                        body = stringResource(
                            if (plugins.isEmpty()) R.string.plugins_empty_body else R.string.plugins_empty_filter_body,
                        ),
                        icon = Icons.Outlined.Extension,
                        actionLabel = if (plugins.isEmpty()) stringResource(R.string.plugins_import) else null,
                        onAction = if (plugins.isEmpty()) {
                            { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }
                        } else {
                            null
                        },
                    )
                }
            } else {
                items(visible, key = { it.manifest.id }) { plugin ->
                    PluginRepoCard(
                        plugin = plugin,
                        dir = vm.pluginDir(plugin.manifest.id),
                        onOpen = { onOpen(plugin.manifest.id) },
                        onToggle = { enabled ->
                            if (enabled && plugin.record.granted.isEmpty()) {
                                onOpen(plugin.manifest.id)
                            } else {
                                vm.setEnabled(plugin.manifest.id, enabled)
                            }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}
