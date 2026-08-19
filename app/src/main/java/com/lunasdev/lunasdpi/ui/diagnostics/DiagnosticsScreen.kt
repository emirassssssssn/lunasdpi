package com.lunasdev.lunasdpi.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.components.SecondaryButton
import com.lunasdev.lunasdpi.ui.components.StatusBadge
import com.lunasdev.lunasdpi.ui.components.StatusTone
import com.lunasdev.lunasdpi.ui.format.formatExact
import com.lunasdev.lunasdpi.ui.format.formatUptime
import com.lunasdev.lunasdpi.ui.format.statusRes
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.DiagnosticsViewModel

@Composable
fun DiagnosticsScreen(onBack: (() -> Unit)? = null, vm: DiagnosticsViewModel = viewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selfTest by remember { mutableStateOf<String?>(null) }
    val report = remember(snapshot, phase, error, selfTest) {
        buildString {
            appendLine("Lunas DPI diagnostics")
            appendLine("VPN: $phase")
            appendLine("TUN: ${if (snapshot.tunActive) "active" else "inactive"}")
            appendLine("Engine: ${if (snapshot.engineAlive) "running" else "stopped"}")
            appendLine("Strategy: ${snapshot.currentStrategy}")
            appendLine("Packets processed: ${snapshot.packetsProcessed}")
            appendLine("Packets modified: ${snapshot.packetsModified}")
            appendLine("Packets dropped: ${snapshot.packetsDropped}")
            appendLine("Active TCP: ${snapshot.activeTcp}")
            appendLine("Active UDP: ${snapshot.activeUdp}")
            appendLine("DNS queries: ${snapshot.dnsQueries}")
            appendLine("Errors: ${snapshot.nativeErrors}")
            appendLine("Last error: ${snapshot.lastError}")
            appendLine("App error: ${error?.title ?: "-"}")
            selfTest?.let { appendLine("Self-test: $it") }
        }
    }
    val tone = when (phase) {
        VpnPhase.CONNECTED -> StatusTone.Success
        VpnPhase.CONNECTING, VpnPhase.STOPPING -> StatusTone.Warning
        VpnPhase.ERROR, VpnPhase.REQUESTING_PERMISSION -> StatusTone.Error
        VpnPhase.DISCONNECTED -> StatusTone.Neutral
    }
    LunaScaffold(title = stringResource(R.string.diagnostics), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(stringResource(R.string.network_activity), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(if (snapshot.engineAlive) R.string.engine_running else R.string.engine_stopped),
                            style = MaterialTheme.typography.bodySmall,
                            color = LunaTheme.colors.textSecondary,
                        )
                    }
                    StatusBadge(text = stringResource(phase.statusRes()), tone = tone)
                }
                if (history.size >= 2) {
                    Spacer(Modifier.height(16.dp))
                    Sparkline(points = history)
                }
            }
            AppCard {
                MetricLine(stringResource(R.string.packets_processed), formatExact(snapshot.packetsProcessed), Icons.Outlined.Speed)
                MetricLine(stringResource(R.string.packets_modified), formatExact(snapshot.packetsModified), Icons.Outlined.Tune)
                MetricLine(stringResource(R.string.active_connections), (snapshot.activeTcp + snapshot.activeUdp).toString(), Icons.Outlined.Hub)
                MetricLine(stringResource(R.string.dns_requests), formatExact(snapshot.dnsQueries), Icons.Outlined.Dns)
                MetricLine(stringResource(R.string.uptime), formatUptime(snapshot.uptimeSeconds), Icons.Outlined.Schedule)
            }
            selfTest?.let {
                AppCard {
                    Text(stringResource(R.string.self_test_result), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textSecondary)
                }
            }
            PrimaryButton(text = stringResource(R.string.run_self_test), onClick = { selfTest = vm.selfTest() })
            SecondaryButton(
                text = stringResource(R.string.copy_diagnostics),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Lunas DPI diagnostics", report))
                },
            )
            SecondaryButton(
                text = stringResource(R.string.export_diagnostics),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.export_diagnostics)))
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = LunaTheme.colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.padding(start = 8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = LunaTheme.colors.textSecondary)
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Sparkline(points: List<Long>) {
    val color = LunaTheme.colors.accent
    Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.minOrNull() ?: 0L
        val max = points.maxOrNull() ?: 1L
        val range = (max - min).coerceAtLeast(1L).toFloat()
        val step = size.width / (points.lastIndex)
        var prev = Offset(0f, size.height)
        points.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value - min).toFloat() / range) * size.height
            val next = Offset(x, y)
            if (index > 0) {
                drawLine(color = color, start = prev, end = next, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            prev = next
        }
    }
}
