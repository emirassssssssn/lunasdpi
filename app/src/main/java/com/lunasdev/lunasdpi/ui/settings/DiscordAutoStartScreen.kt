package com.lunasdev.lunasdpi.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.DiscordAppScanner
import com.lunasdev.lunasdpi.data.InstalledDiscordApp
import com.lunasdev.lunasdpi.service.AppLaunchWatcher
import com.lunasdev.lunasdpi.service.BatteryExemption
import com.lunasdev.lunasdpi.service.DiscordWatchService
import com.lunasdev.lunasdpi.service.ForegroundApp
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.ErrorState
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.LunaSwitch
import com.lunasdev.lunasdpi.ui.components.LunaTextField
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.components.SectionHeader
import com.lunasdev.lunasdpi.ui.components.SettingRow
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.SettingsViewModel

@Composable
fun DiscordAutoStartScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pm = context.packageManager
    val colors = LunaTheme.colors
    var a11yEpoch by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledDiscordApp>>(emptyList()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { a11yEpoch += 1 }
    val watcherOn = remember(a11yEpoch) { AppLaunchWatcher.isEnabled(context) }
    val batteryIgnored = remember(a11yEpoch) { BatteryExemption.isIgnored(context) }
    val usageGranted = remember(a11yEpoch) { ForegroundApp.usageGranted(context) }
    LaunchedEffect(showAll) {
        apps = if (showAll) {
            DiscordAppScanner.launchable(pm)
        } else {
            DiscordAppScanner.detected(pm)
        }
    }
    val filtered = remember(apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.lowercase().contains(needle) || app.packageName.contains(needle)
            }
        }
    }
    val selectedLabel = remember(config.autoStartPackage, apps) {
        apps.find { it.packageName == config.autoStartPackage }?.label
            ?: runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(config.autoStartPackage, 0)).toString()
            }.getOrNull()
    }
    LunaScaffold(title = stringResource(R.string.discord_autostart_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = LunaSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                AppCard {
                    SettingRow(
                        title = stringResource(R.string.auto_start_discord),
                        description = stringResource(R.string.discord_autostart_pick_hint),
                        icon = Icons.Outlined.Forum,
                    ) {
                        LunaSwitch(
                            checked = config.autoStartOnDiscord,
                            onCheckedChange = { enable ->
                                vm.update { it.copy(autoStartOnDiscord = enable) }
                                DiscordWatchService.sync(context, enable)
                                if (enable && !AppLaunchWatcher.isEnabled(context)) {
                                    AppLaunchWatcher.openSettings(context)
                                }
                            },
                        )
                    }
                    SettingRow(
                        title = stringResource(R.string.discord_autostop_leave),
                        description = stringResource(R.string.discord_autostop_leave_desc),
                        icon = Icons.AutoMirrored.Outlined.Logout,
                    ) {
                        LunaSwitch(
                            checked = config.autoStopOnDiscordLeave,
                            onCheckedChange = { enable ->
                                vm.update { it.copy(autoStopOnDiscordLeave = enable) }
                            },
                        )
                    }
                }
            }
            if (config.autoStartOnDiscord && !watcherOn) {
                item {
                    ErrorState(
                        title = stringResource(R.string.auto_start_discord_grant),
                        body = stringResource(R.string.discord_autostart_a11y_body),
                        icon = Icons.Outlined.Forum,
                        actionLabel = stringResource(R.string.auto_start_discord_grant),
                        onAction = { AppLaunchWatcher.openSettings(context) },
                    )
                }
            }
            if (config.autoStartOnDiscord && !usageGranted) {
                item {
                    ErrorState(
                        title = stringResource(R.string.discord_watch_usage),
                        body = stringResource(R.string.discord_watch_usage_desc),
                        icon = Icons.Outlined.Visibility,
                        actionLabel = stringResource(R.string.discord_watch_usage_action),
                        onAction = { ForegroundApp.openUsageSettings(context) },
                    )
                }
            }
            if (config.autoStartOnDiscord && !batteryIgnored) {
                item {
                    ErrorState(
                        title = stringResource(R.string.discord_watch_battery),
                        body = stringResource(R.string.discord_watch_battery_desc),
                        icon = Icons.Outlined.BatterySaver,
                        actionLabel = stringResource(R.string.discord_watch_battery_action),
                        onAction = { BatteryExemption.request(context) },
                    )
                }
            }
            if (config.autoStartOnDiscord && config.autoStartPackage.isBlank()) {
                item {
                    ErrorState(
                        title = stringResource(R.string.discord_autostart_select_title),
                        body = stringResource(R.string.discord_autostart_select_body),
                    )
                }
            }
            item {
                SectionHeader(stringResource(R.string.discord_autostart_installed))
                if (!selectedLabel.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.discord_autostart_selected, selectedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LunaTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.discord_autostart_search),
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.discord_autostart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            } else {
                items(filtered, key = { it.packageName }) { app ->
                    DiscordAppRow(
                        app = app,
                        selected = app.packageName == config.autoStartPackage,
                        icon = remember(app.packageName) {
                            runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
                        },
                        onSelect = {
                            vm.update {
                                it.copy(
                                    autoStartOnDiscord = true,
                                    autoStartPackage = app.packageName,
                                )
                            }
                            DiscordWatchService.sync(context, true)
                            if (!AppLaunchWatcher.isEnabled(context)) {
                                AppLaunchWatcher.openSettings(context)
                            }
                        },
                    )
                }
            }
            item {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        if (showAll) {
                            stringResource(R.string.discord_autostart_show_detected)
                        } else {
                            stringResource(R.string.discord_autostart_show_all)
                        },
                    )
                }
                if (config.autoStartOnDiscord && !watcherOn) {
                    PrimaryButton(
                        text = stringResource(R.string.auto_start_discord_grant),
                        onClick = { AppLaunchWatcher.openSettings(context) },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DiscordAppRow(
    app: InstalledDiscordApp,
    selected: Boolean,
    icon: Drawable?,
    onSelect: () -> Unit,
) {
    val colors = LunaTheme.colors
    AppCard(onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (icon != null) {
                Image(
                    bitmap = remember(icon) { icon.toBitmap(width = 96, height = 96).asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.elevated),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = colors.accent),
            )
        }
    }
}
