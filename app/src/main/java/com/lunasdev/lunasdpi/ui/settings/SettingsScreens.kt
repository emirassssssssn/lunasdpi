package com.lunasdev.lunasdpi.ui.settings

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.DnsMode
import com.lunasdev.lunasdpi.data.model.DpiMode
import com.lunasdev.lunasdpi.data.model.Ipv6Mode
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.Glyph
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.LunaSwitch
import com.lunasdev.lunasdpi.ui.components.LunaTextField
import com.lunasdev.lunasdpi.ui.components.SectionHeader
import com.lunasdev.lunasdpi.ui.components.SettingRow
import com.lunasdev.lunasdpi.ui.format.labelRes
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onDpi: () -> Unit,
    onDns: () -> Unit,
    onVpn: () -> Unit,
    onAdvanced: () -> Unit,
    onRules: () -> Unit,
    onDiagnostics: () -> Unit,
    onPrivacy: () -> Unit,
    onApps: () -> Unit,
    onDiscordAutoStart: () -> Unit,
    onPlugins: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    LunaScaffold(title = stringResource(R.string.settings)) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(stringResource(R.string.section_general))
            AppCard {
                SettingRow(
                    title = stringResource(R.string.start_on_boot),
                    description = stringResource(R.string.start_on_boot_desc),
                    icon = Icons.Outlined.PowerSettingsNew,
                ) {
                    LunaSwitch(checked = config.startOnBoot, onCheckedChange = {
                        vm.update { current -> current.copy(startOnBoot = current.startOnBoot.not()) }
                    })
                }
                SettingRow(
                    title = stringResource(R.string.auto_reconnect),
                    description = stringResource(R.string.auto_reconnect_desc),
                    icon = Icons.Outlined.Sync,
                ) {
                    LunaSwitch(checked = config.autoReconnect, onCheckedChange = {
                        vm.update { current -> current.copy(autoReconnect = current.autoReconnect.not()) }
                    })
                }
                SettingRow(
                    title = stringResource(R.string.notification_silent),
                    description = stringResource(R.string.notification_silent_desc),
                    icon = Icons.Outlined.NotificationsOff,
                ) {
                    LunaSwitch(checked = config.notificationSilent, onCheckedChange = {
                        vm.update { current -> current.copy(notificationSilent = current.notificationSilent.not()) }
                    })
                }
            }
            LinkSetting(
                title = stringResource(R.string.discord_autostart_title),
                value = discordClientLabel(config.autoStartPackage),
                description = stringResource(R.string.discord_autostart_pick_hint),
                icon = Icons.Outlined.Forum,
                onClick = onDiscordAutoStart,
            )
            SectionHeader(stringResource(R.string.section_protection))
            LinkSetting(
                title = stringResource(R.string.dpi_settings),
                value = stringResource(config.mode.labelRes()),
                description = stringResource(R.string.protection_mode_desc),
                icon = Icons.Outlined.Shield,
                onClick = onDpi,
            )
            LinkSetting(
                title = stringResource(R.string.custom_bypass_rules),
                value = null,
                icon = Icons.Outlined.Tune,
                onClick = onRules,
            )
            SectionHeader(stringResource(R.string.section_dns))
            LinkSetting(
                title = stringResource(R.string.dns_settings),
                value = stringResource(config.dnsMode.labelRes()),
                description = stringResource(R.string.dns_mode_desc),
                icon = Icons.Outlined.Dns,
                onClick = onDns,
            )
            SectionHeader(stringResource(R.string.section_traffic))
            LinkSetting(
                title = stringResource(R.string.vpn_settings),
                value = stringResource(config.ipv6Mode.labelRes()),
                description = stringResource(R.string.routing_desc),
                icon = Icons.Outlined.Route,
                onClick = onVpn,
            )
            LinkSetting(
                title = stringResource(R.string.per_app_vpn),
                value = stringResource(config.perAppMode.labelRes()),
                icon = Icons.Outlined.Apps,
                onClick = onApps,
            )
            SectionHeader(stringResource(R.string.section_extensions))
            LinkSetting(
                title = stringResource(R.string.plugins),
                value = null,
                description = stringResource(R.string.plugins_settings_row_desc),
                icon = Icons.Outlined.Extension,
                onClick = onPlugins,
            )
            SectionHeader(stringResource(R.string.section_advanced))
            LinkSetting(
                title = stringResource(R.string.diagnostics),
                value = null,
                icon = Icons.Outlined.Insights,
                onClick = onDiagnostics,
            )
            LinkSetting(
                title = stringResource(R.string.advanced),
                value = null,
                icon = Icons.Outlined.Tune,
                onClick = onAdvanced,
            )
            LinkSetting(
                title = stringResource(R.string.privacy),
                value = null,
                icon = Icons.Outlined.PrivacyTip,
                onClick = onPrivacy,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun DpiSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val colors = LunaTheme.colors
    LunaScaffold(title = stringResource(R.string.dpi_settings), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeChips(selected = config.mode, onSelect = { mode -> vm.update { it.copy(mode = mode) } })
            AppCard {
                SettingRow(
                    title = stringResource(R.string.tcp_fragmentation),
                    description = stringResource(R.string.tcp_fragmentation_desc),
                    icon = Icons.Outlined.Tune,
                ) {
                    LunaSwitch(checked = config.tcpFragmentation, onCheckedChange = {
                        vm.update { current -> current.copy(tcpFragmentation = current.tcpFragmentation.not()) }
                    })
                }
                Text(
                    "${stringResource(R.string.fragment_size)}  ${config.fragmentSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                LunaSlider(
                    value = config.fragmentSize.toFloat(),
                    onValueChange = { value -> vm.update { it.copy(fragmentSize = value.toInt().coerceIn(1, 256)) } },
                    valueRange = 1f..32f,
                )
                SettingRow(
                    title = stringResource(R.string.http_host_case),
                    description = stringResource(R.string.http_host_case_desc),
                ) {
                    LunaSwitch(checked = config.httpHostCase, onCheckedChange = {
                        vm.update { current -> current.copy(httpHostCase = current.httpHostCase.not()) }
                    })
                }
                SettingRow(
                    title = stringResource(R.string.http_spacing),
                    description = stringResource(R.string.http_spacing_desc),
                ) {
                    LunaSwitch(checked = config.httpSpacing, onCheckedChange = {
                        vm.update { current -> current.copy(httpSpacing = current.httpSpacing.not()) }
                    })
                }
                SettingRow(
                    title = stringResource(R.string.http_method_spacing),
                    description = stringResource(R.string.http_method_spacing_desc),
                ) {
                    LunaSwitch(checked = config.httpMethodSpacing, onCheckedChange = {
                        vm.update { current -> current.copy(httpMethodSpacing = current.httpMethodSpacing.not()) }
                    })
                }
                SettingRow(
                    title = stringResource(R.string.persistent_fragment),
                    description = stringResource(R.string.persistent_fragment_desc),
                ) {
                    LunaSwitch(checked = config.persistentFragment, onCheckedChange = {
                        vm.update { current -> current.copy(persistentFragment = current.persistentFragment.not()) }
                    })
                }
                SettingRow(
                    title = stringResource(R.string.block_quic),
                    description = stringResource(R.string.block_quic_desc),
                    icon = Icons.Outlined.Shield,
                ) {
                    LunaSwitch(checked = config.blockQuic, onCheckedChange = {
                        vm.update { current -> current.copy(blockQuic = current.blockQuic.not()) }
                    })
                }
            }
            Text(
                stringResource(R.string.dpi_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun DnsSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    LunaScaffold(title = stringResource(R.string.dns_settings), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                DnsMode.entries.forEach { mode ->
                    ChoiceChip(
                        selected = config.dnsMode == mode,
                        label = stringResource(mode.labelRes()),
                        onClick = { vm.update { it.copy(dnsMode = mode) } },
                    )
                }
            }
            LunaTextField(
                value = config.customDns.joinToString("\n"),
                onValueChange = { text ->
                    vm.update { cfg ->
                        cfg.copy(customDns = text.lines().map { it.trim() }.filter { it.isNotEmpty() })
                    }
                },
                label = stringResource(R.string.custom_dns),
                supportingText = stringResource(R.string.custom_dns_hint),
                singleLine = false,
                minLines = 3,
            )
            SectionHeader(stringResource(R.string.dns_presets))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    selected = config.customDns == listOf("1.1.1.1", "9.9.9.9"),
                    label = "1.1.1.1",
                    onClick = {
                        vm.update { it.copy(dnsMode = DnsMode.CUSTOM, customDns = listOf("1.1.1.1", "9.9.9.9")) }
                    },
                )
                ChoiceChip(
                    selected = config.customDns == listOf("9.9.9.9"),
                    label = "9.9.9.9",
                    onClick = { vm.update { it.copy(dnsMode = DnsMode.CUSTOM, customDns = listOf("9.9.9.9")) } },
                )
                ChoiceChip(
                    selected = config.customDns == listOf("8.8.8.8", "8.8.4.4"),
                    label = "8.8.8.8",
                    onClick = {
                        vm.update { it.copy(dnsMode = DnsMode.CUSTOM, customDns = listOf("8.8.8.8", "8.8.4.4")) }
                    },
                )
            }
        }
    }
}

@Composable
fun VpnSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    LunaScaffold(title = stringResource(R.string.vpn_settings), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ipv6_mode), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ipv6Mode.entries.forEach { mode ->
                    ChoiceChip(
                        selected = config.ipv6Mode == mode,
                        label = stringResource(mode.labelRes()),
                        onClick = { vm.update { it.copy(ipv6Mode = mode) } },
                    )
                }
            }
            Text(
                "${stringResource(R.string.mtu)}  ${config.mtu}",
                style = MaterialTheme.typography.bodySmall,
                color = LunaTheme.colors.textSecondary,
            )
            LunaSlider(
                value = config.mtu.toFloat(),
                onValueChange = { value -> vm.update { it.copy(mtu = value.toInt().coerceIn(576, 1500)) } },
                valueRange = 576f..1500f,
            )
            Text(stringResource(R.string.ipv6_hint), style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textMuted)
        }
    }
}

@Composable
fun AdvancedSettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    LunaScaffold(title = stringResource(R.string.advanced), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                SettingRow(
                    title = stringResource(R.string.log_level),
                    description = stringResource(R.string.log_level_desc),
                ) { }
                Text(
                    config.logLevel.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
                LunaSlider(
                    value = config.logLevel.toFloat(),
                    onValueChange = { value -> vm.update { it.copy(logLevel = value.toInt().coerceIn(0, 3)) } },
                    valueRange = 0f..3f,
                    steps = 2,
                )
            }
            AppCard {
                Text(stringResource(R.string.engine_info), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.engine_info_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = LunaTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    SettingRow(title = label) {
        LunaSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun LinkSetting(
    title: String,
    value: String?,
    icon: ImageVector,
    description: String? = null,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Glyph(icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textSecondary)
                }
                if (value != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = LunaTheme.colors.accent)
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = LunaTheme.colors.textMuted)
        }
    }
}

@Composable
private fun ModeChips(selected: DpiMode, onSelect: (DpiMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        DpiMode.entries.forEach { mode ->
            ChoiceChip(
                selected = selected == mode,
                label = stringResource(mode.labelRes()),
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
fun ChoiceChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = LunaTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.extraSmall,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.card,
            labelColor = colors.textSecondary,
            selectedContainerColor = colors.accent.copy(alpha = 0.16f),
            selectedLabelColor = colors.textPrimary,
            disabledContainerColor = colors.card,
            disabledLabelColor = colors.textMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.border,
            selectedBorderColor = colors.accent.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun LunaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
) {
    val colors = LunaTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.border,
        ),
    )
}

@Composable
private fun discordClientLabel(packageName: String): String {
    if (packageName.isBlank()) {
        return stringResource(R.string.discord_autostart_none)
    }
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}
