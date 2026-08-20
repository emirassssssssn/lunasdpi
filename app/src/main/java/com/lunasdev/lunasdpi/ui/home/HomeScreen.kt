package com.lunasdev.lunasdpi.ui.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.service.AppLaunchWatcher
import com.lunasdev.lunasdpi.service.ForegroundApp
import com.lunasdev.lunasdpi.service.ProtectionStartRequest
import com.lunasdev.lunasdpi.ui.components.ActionBanner
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.ErrorState
import com.lunasdev.lunasdpi.ui.components.MetricCard
import com.lunasdev.lunasdpi.ui.components.NavRow
import com.lunasdev.lunasdpi.ui.components.StatusBadge
import com.lunasdev.lunasdpi.ui.components.StatusTone
import com.lunasdev.lunasdpi.ui.format.formatCount
import com.lunasdev.lunasdpi.ui.format.labelRes
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onMode: () -> Unit,
    onDns: () -> Unit,
    onDiscordAutoStart: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.start() else vm.onPermissionDenied()
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.startWithVpnCheck(permissionLauncher::launch) }

    val requestStart = rememberUpdatedState {
        maybeStart(context, vm, permissionLauncher::launch, notifLauncher::launch)
    }
    var a11yEpoch by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { a11yEpoch += 1 }
    val pendingStart by ProtectionStartRequest.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingStart) {
        if (pendingStart && ProtectionStartRequest.consume()) {
            requestStart.value()
        }
    }
    val watcherOn = remember(a11yEpoch) { AppLaunchWatcher.isEnabled(context) }
    val usageOn = remember(a11yEpoch) { ForegroundApp.usageGranted(context) }
    val connected = phase == VpnPhase.CONNECTED
    val watchingReady = config.autoStartOnDiscord &&
        (watcherOn || usageOn) &&
        config.autoStartPackage.isNotBlank()
    val needsDiscordSetup = config.autoStartOnDiscord &&
        !connected &&
        ((!watcherOn && !usageOn) || config.autoStartPackage.isBlank())
    val colors = LunaTheme.colors
    val wide = LocalConfiguration.current.screenWidthDp >= 680
    val scroll = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = LunaSpacing.screen, vertical = 8.dp)
        .statusBarsPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (wide) {
            Row(modifier = scroll, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    HomeHeader(watching = watchingReady)
                    HomeHero(
                        phase = phase,
                        onToggle = {
                            if (connected) vm.stop() else requestStart.value()
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    HomeSecondary(
                        vm = vm,
                        showDiscordSetup = needsDiscordSetup,
                        watching = watchingReady,
                        onDiscordSetup = onDiscordAutoStart,
                        phase = phase,
                        onGrantVpn = { requestStart.value() },
                        modeLabel = stringResource(config.mode.labelRes()),
                        modeDesc = stringResource(
                            if (config.tcpFragmentation) R.string.tcp_enabled else R.string.tcp_disabled,
                        ),
                        dnsLabel = stringResource(config.dnsMode.labelRes()),
                        onMode = onMode,
                        onDns = onDns,
                    )
                }
            }
        } else {
            Column(modifier = scroll) {
                HomeHeader(watching = watchingReady)
                HomeHero(
                    phase = phase,
                    onToggle = {
                        if (connected) vm.stop() else requestStart.value()
                    },
                )
                Spacer(Modifier.height(8.dp))
                HomeSecondary(
                    vm = vm,
                    showDiscordSetup = needsDiscordSetup,
                    watching = watchingReady,
                    onDiscordSetup = onDiscordAutoStart,
                    phase = phase,
                    onGrantVpn = { requestStart.value() },
                    modeLabel = stringResource(config.mode.labelRes()),
                    modeDesc = stringResource(
                        if (config.tcpFragmentation) R.string.tcp_enabled else R.string.tcp_disabled,
                    ),
                    dnsLabel = stringResource(config.dnsMode.labelRes()),
                    onMode = onMode,
                    onDns = onDns,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    error?.let { err ->
        var showTech by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = vm::dismissError,
            containerColor = colors.card,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(err.title) },
            text = {
                Column {
                    Text(err.message)
                    if (!err.technicalDetails.isNullOrBlank()) {
                        TextButton(onClick = { showTech = !showTech }) {
                            Text(
                                if (showTech) {
                                    stringResource(R.string.hide_details)
                                } else {
                                    stringResource(R.string.technical_details)
                                },
                            )
                        }
                        if (showTech) {
                            Text(err.technicalDetails, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = vm::dismissError,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                ) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
private fun HomeHeader(watching: Boolean) {
    val colors = LunaTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            if (watching) {
                Spacer(Modifier.width(10.dp))
                StatusBadge(text = stringResource(R.string.discord_watching_chip), tone = StatusTone.Accent)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.network_protection), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.local_dpi_processing),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun HomeHero(
    phase: VpnPhase,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        HomeProtectionControl(
            phase = phase,
            onToggle = onToggle,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HomeSecondary(
    vm: HomeViewModel,
    showDiscordSetup: Boolean,
    watching: Boolean,
    onDiscordSetup: () -> Unit,
    phase: VpnPhase,
    onGrantVpn: () -> Unit,
    modeLabel: String,
    modeDesc: String,
    dnsLabel: String,
    onMode: () -> Unit,
    onDns: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showDiscordSetup) {
            ActionBanner(
                title = stringResource(R.string.discord_autostart_title),
                body = stringResource(R.string.auto_start_home_banner),
                actionLabel = stringResource(R.string.discord_autostart_configure_short),
                icon = Icons.Outlined.Forum,
                onClick = onDiscordSetup,
            )
        } else if (watching) {
            NavRow(
                title = stringResource(R.string.discord_autostart_title),
                value = stringResource(R.string.discord_watch_ready),
                description = stringResource(R.string.discord_watch_ready_desc),
                icon = Icons.Outlined.Forum,
                onClick = onDiscordSetup,
            )
        }
        if (phase == VpnPhase.REQUESTING_PERMISSION) {
            ErrorState(
                title = stringResource(R.string.vpn_permission_title),
                body = stringResource(R.string.vpn_permission_body),
                icon = Icons.Outlined.VpnKey,
                actionLabel = stringResource(R.string.grant_permission),
                onAction = onGrantVpn,
            )
        }
        NavRow(
            title = stringResource(R.string.current_strategy),
            value = modeLabel,
            description = modeDesc,
            icon = Icons.Outlined.Tune,
            onClick = onMode,
        )
        NavRow(
            title = stringResource(R.string.dns),
            value = dnsLabel,
            icon = Icons.Outlined.Dns,
            onClick = onDns,
        )
        NetworkActivityCard(vm = vm)
    }
}

@Composable
private fun NetworkActivityCard(vm: HomeViewModel) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    AppCard {
        Text(
            stringResource(R.string.network_activity).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LunaTheme.colors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = stringResource(R.string.packets),
                value = formatCount(snapshot.packetsProcessed),
                icon = Icons.Outlined.Speed,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            MetricCard(
                label = stringResource(R.string.connections),
                value = (snapshot.activeTcp + snapshot.activeUdp).toString(),
                icon = Icons.Outlined.Hub,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun maybeStart(
    context: android.content.Context,
    vm: HomeViewModel,
    launchVpn: (android.content.Intent) -> Unit,
    launchNotif: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launchNotif(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
    }
    vm.startWithVpnCheck(launchVpn)
}

private fun HomeViewModel.startWithVpnCheck(launchVpn: (android.content.Intent) -> Unit) {
    val prepare = prepareIntent()
    if (prepare != null) launchVpn(prepare) else start()
}
