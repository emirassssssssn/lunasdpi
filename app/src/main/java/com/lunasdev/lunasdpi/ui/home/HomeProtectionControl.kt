package com.lunasdev.lunasdpi.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.ui.format.statusRes
import com.lunasdev.lunasdpi.ui.theme.LunaTheme

@Composable
fun HomeProtectionControl(
    phase: VpnPhase,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LunaTheme.colors
    val motion = LunaTheme.motion
    val busy = phase == VpnPhase.CONNECTING || phase == VpnPhase.STOPPING
    val connected = phase == VpnPhase.CONNECTED
    val chrome = protectionChrome(phase)
    val spec = tween<Color>(durationMillis = motion.slow, easing = motion.emphasized)
    val tone by animateColorAsState(chrome.tone, spec, label = "protectionTone")
    val core by animateColorAsState(chrome.core, spec, label = "protectionCore")
    val glowAlpha by animateFloatAsState(
        targetValue = chrome.glowAlpha,
        animationSpec = tween(motion.slow, easing = motion.emphasized),
        label = "protectionGlow",
    )
    val moonCut by animateFloatAsState(
        targetValue = if (connected) 0f else 1f,
        animationSpec = tween(motion.slow, easing = motion.emphasized),
        label = "protectionMoon",
    )
    val sweep by animateFloatAsState(
        targetValue = chrome.sweep,
        animationSpec = tween(motion.slow, easing = motion.emphasized),
        label = "protectionSweep",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !busy) 0.94f else 1f,
        animationSpec = tween(motion.fast, easing = motion.easing),
        label = "protectionPress",
    )
    val haptic = LocalHapticFeedback.current
    val status = stringResource(phase.statusRes())
    val action = when (phase) {
        VpnPhase.CONNECTED -> stringResource(R.string.stop_protection)
        VpnPhase.CONNECTING -> stringResource(R.string.starting_protection)
        VpnPhase.STOPPING -> stringResource(R.string.status_stopping)
        VpnPhase.REQUESTING_PERMISSION -> stringResource(R.string.status_permission)
        VpnPhase.ERROR -> stringResource(R.string.status_error)
        VpnPhase.DISCONNECTED -> stringResource(R.string.start_protection)
    }
    val hint = when (phase) {
        VpnPhase.CONNECTED -> stringResource(R.string.protection_tap_stop)
        VpnPhase.CONNECTING -> stringResource(R.string.starting_protection)
        VpnPhase.STOPPING -> stringResource(R.string.status_stopping)
        VpnPhase.REQUESTING_PERMISSION -> stringResource(R.string.status_permission)
        VpnPhase.ERROR -> stringResource(R.string.protection_tap_start)
        VpnPhase.DISCONNECTED -> stringResource(R.string.protection_tap_start)
    }
    val statusColor = when (phase) {
        VpnPhase.CONNECTED -> colors.success
        VpnPhase.CONNECTING, VpnPhase.STOPPING -> colors.warning
        VpnPhase.ERROR, VpnPhase.REQUESTING_PERMISSION -> colors.error
        VpnPhase.DISCONNECTED -> colors.textPrimary
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(216.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(216.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(tone.copy(alpha = glowAlpha), Color.Transparent),
                        ),
                    ),
            )
            ProtectionRing(tone = tone, sweep = sweep)
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(tone.copy(alpha = 0.28f), core),
                        ),
                    )
                    .border(1.dp, tone.copy(alpha = 0.42f), CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(color = tone.copy(alpha = 0.35f)),
                        enabled = !busy,
                        role = Role.Button,
                        onClickLabel = action,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        },
                    )
                    .semantics {
                        contentDescription = action
                        stateDescription = status
                    },
                contentAlignment = Alignment.Center,
            ) {
                ProtectionMoon(color = tone, cut = moonCut)
            }
        }
        Text(
            text = status,
            style = MaterialTheme.typography.headlineMedium,
            color = statusColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProtectionRing(tone: Color, sweep: Float) {
    Canvas(modifier = Modifier.size(168.dp)) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2f + 1.5.dp.toPx()
        val diameter = size.minDimension - inset * 2f
        val topLeft = Offset(inset, inset)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = tone.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 1.dp.toPx()),
        )
        if (sweep >= 359f) {
            drawCircle(
                color = tone,
                radius = diameter / 2f,
                style = Stroke(width = stroke),
            )
        } else {
            drawArc(
                color = tone,
                startAngle = -78f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ProtectionMoon(color: Color, cut: Float) {
    Canvas(modifier = Modifier.size(44.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(center, radius))
            if (cut > 0.02f) {
                val hole = radius * (0.72f * cut)
                val shift = Offset(radius * 0.38f * cut, -radius * 0.08f * cut)
                addOval(Rect(center + shift, hole))
            }
        }
        drawPath(path, color)
    }
}

private data class ProtectionChrome(
    val tone: Color,
    val core: Color,
    val glowAlpha: Float,
    val sweep: Float,
)

@Composable
private fun protectionChrome(phase: VpnPhase): ProtectionChrome {
    val colors = LunaTheme.colors
    return when (phase) {
        VpnPhase.CONNECTED -> ProtectionChrome(
            tone = colors.success,
            core = colors.success.copy(alpha = 0.16f),
            glowAlpha = 0.34f,
            sweep = 360f,
        )
        VpnPhase.CONNECTING, VpnPhase.STOPPING -> ProtectionChrome(
            tone = colors.warning,
            core = colors.warning.copy(alpha = 0.12f),
            glowAlpha = 0.22f,
            sweep = 232f,
        )
        VpnPhase.ERROR, VpnPhase.REQUESTING_PERMISSION -> ProtectionChrome(
            tone = colors.error,
            core = colors.error.copy(alpha = 0.12f),
            glowAlpha = 0.20f,
            sweep = 210f,
        )
        VpnPhase.DISCONNECTED -> ProtectionChrome(
            tone = colors.accent,
            core = colors.elevated,
            glowAlpha = 0.16f,
            sweep = 292f,
        )
    }
}
