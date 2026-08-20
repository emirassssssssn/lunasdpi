package com.lunasdev.lunasdpi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.DpiMode
import com.lunasdev.lunasdpi.data.model.VpnPhase
import com.lunasdev.lunasdpi.ui.format.labelRes
import com.lunasdev.lunasdpi.ui.format.statusRes
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme

@Composable
fun LunaBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LunaTheme.colors.background),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunaScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LunaTheme.colors
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        contentWindowInsets = if (onBack != null) WindowInsets.navigationBars else WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
            )
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(LunaSpacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LunaTheme.colors
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = colors.card,
        contentColor = colors.textPrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun LunaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = AppCard(modifier = modifier, content = content)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LunaTheme.colors.textMuted,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = SectionHeader(text, modifier)

@Composable
fun Glyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LunaTheme.colors.accent,
    container: Color = LunaTheme.colors.accent.copy(alpha = 0.14f),
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(container, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = LunaTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.elevated,
            disabledContentColor = colors.textMuted,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LunaTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LunaTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val colors = LunaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Glyph(icon)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    SettingRow(title = label) {
        LunaSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
fun LunaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = LunaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.elevated,
            uncheckedBorderColor = colors.border,
        ),
    )
}

@Composable
fun NavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    description: String? = null,
    icon: ImageVector? = null,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Glyph(icon)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LunaTheme.colors.textMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(value, style = MaterialTheme.typography.titleMedium)
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textSecondary)
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = LunaTheme.colors.textMuted,
            )
        }
    }
}

@Composable
fun ActionBanner(
    title: String,
    body: String,
    actionLabel: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = LunaTheme.colors
    AppCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Glyph(icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onAccent,
                    modifier = Modifier
                        .background(colors.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

enum class StatusTone { Neutral, Success, Warning, Error, Info, Accent }

@Composable
fun StatusBadge(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    val fg = when (tone) {
        StatusTone.Neutral -> colors.textSecondary
        StatusTone.Success -> colors.success
        StatusTone.Warning -> colors.warning
        StatusTone.Error -> colors.error
        StatusTone.Info -> colors.info
        StatusTone.Accent -> colors.accent
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun StrategyBadge(mode: DpiMode, modifier: Modifier = Modifier) {
    StatusBadge(text = stringResource(mode.labelRes()), tone = StatusTone.Accent, modifier = modifier)
}

@Composable
fun ConnectionIndicator(phase: VpnPhase, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    val tone = when (phase) {
        VpnPhase.CONNECTED -> colors.success
        VpnPhase.CONNECTING, VpnPhase.STOPPING -> colors.warning
        VpnPhase.ERROR, VpnPhase.REQUESTING_PERMISSION -> colors.error
        VpnPhase.DISCONNECTED -> colors.textMuted
    }
    val active = phase == VpnPhase.CONNECTED
    val connecting = phase == VpnPhase.CONNECTING || phase == VpnPhase.STOPPING
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StatusDot(active = active, connecting = connecting, color = tone)
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(phase.statusRes()),
            style = MaterialTheme.typography.titleLarge,
            color = if (active) colors.success else colors.textPrimary,
        )
    }
}

@Composable
fun StatusPulse(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LunaTheme.colors.success,
) {
    StatusDot(
        active = active,
        connecting = false,
        color = color,
        modifier = modifier,
        size = 10.dp,
    )
}

@Composable
private fun StatusDot(
    active: Boolean,
    connecting: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val fill = active || connecting
    Box(modifier = modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (fill) {
                        Modifier.background(color, CircleShape)
                    } else {
                        Modifier.border(1.5.dp, color, CircleShape)
                    },
                ),
        )
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = LunaTheme.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = LunaTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = LunaTheme.colors.textPrimary,
        )
    }
}

@Composable
fun DomainChip(text: String, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    Row(
        modifier = modifier
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .background(colors.elevated, RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = if (onRemove != null) 2.dp else 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(14.dp),
                    tint = colors.textMuted,
                )
            }
        }
    }
}

@Composable
fun LunaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    fontFamily: FontFamily? = null,
    enabled: Boolean = true,
) {
    val colors = LunaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = if (fontFamily != null) {
            MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.elevated,
            unfocusedContainerColor = colors.card,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            cursorColor = colors.accent,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textMuted,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedSupportingTextColor = colors.textMuted,
            unfocusedSupportingTextColor = colors.textMuted,
        ),
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LunaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Glyph(icon, tint = colors.textSecondary, container = colors.elevated)
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            CompactButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LunaTheme.colors
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Glyph(icon, tint = colors.warning, container = colors.warning.copy(alpha = 0.14f))
                Spacer(Modifier.width(12.dp))
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .background(colors.error, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(12.dp))
                    CompactButton(text = actionLabel, onClick = onAction)
                }
            }
        }
    }
}

@Composable
fun AddDomainButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.add_domain), style = MaterialTheme.typography.labelLarge, color = colors.accent)
    }
}
