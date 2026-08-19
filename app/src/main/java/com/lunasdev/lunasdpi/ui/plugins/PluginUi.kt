package com.lunasdev.lunasdpi.ui.plugins

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.plugin.InstalledPlugin
import com.lunasdev.lunasdpi.plugin.PluginPermission
import com.lunasdev.lunasdpi.plugin.ValidatedManifest
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.LunaSwitch
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import java.io.File

@Composable
fun PluginAvatar(
    manifest: ValidatedManifest,
    pluginDir: File,
    size: Dp = 40.dp,
    pngBytes: ByteArray? = null,
) {
    val bitmap = remember(manifest.id, manifest.icon, pngBytes) {
        decodePngBytes(pngBytes) ?: decodePluginPng(pluginDir, manifest.icon)
    }
    val shape = RoundedCornerShape(10.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = manifest.name,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .border(1.dp, LunaTheme.colors.border, shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val colors = LunaTheme.colors
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(colors.accent.copy(alpha = 0.14f))
                .border(1.dp, colors.accent.copy(alpha = 0.22f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = manifest.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.accent,
            )
        }
    }
}

fun decodePluginPng(dir: File, icon: String?): android.graphics.Bitmap? {
    if (icon.isNullOrBlank() || !icon.endsWith(".png")) return null
    val file = File(dir, icon)
    if (!file.isFile || file.length() > 256 * 1024) return null
    return decodePngBytes(file.readBytes())
}

fun decodePngBytes(bytes: ByteArray?): android.graphics.Bitmap? {
    if (bytes == null || bytes.isEmpty() || bytes.size > 256 * 1024) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    if (bounds.outWidth > 1024 || bounds.outHeight > 1024) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

@Composable
fun PluginOwnerLine(author: String, id: String, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    val slug = id.substringAfterLast('.')
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = author,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = " / ",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = slug,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun PluginTopicChip(text: String, modifier: Modifier = Modifier, tone: String = "accent") {
    val colors = LunaTheme.colors
    val color = pluginToneColor(tone, colors)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun PluginAlertBox(text: String, tone: String, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    val color = pluginToneColor(tone, colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(color),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

fun pluginToneColor(tone: String, colors: com.lunasdev.lunasdpi.ui.theme.LunaColors) = when (tone.lowercase()) {
    "success", "ok", "green" -> colors.success
    "warning", "warn", "yellow" -> colors.warning
    "error", "danger", "red" -> colors.error
    "info", "blue" -> colors.info
    else -> colors.accent
}

@Composable
fun PluginHubStat(
    value: String,
    label: String,
    accent: Boolean = false,
    error: Boolean = false,
) {
    val colors = LunaTheme.colors
    val color = when {
        error -> colors.error
        accent -> colors.success
        else -> colors.textPrimary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

@Composable
fun PluginStatusDot(enabled: Boolean, error: Boolean, modifier: Modifier = Modifier) {
    val colors = LunaTheme.colors
    val color = when {
        error -> colors.error
        enabled -> colors.success
        else -> colors.textMuted
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun PluginTopicRow(permissions: List<PluginPermission>, extra: Int = 0) {
    val shown = permissions.take(3)
    if (shown.isEmpty() && extra <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { permission ->
            PluginTopicChip(text = stringResource(permission.titleRes()))
        }
        val overflow = (permissions.size - shown.size) + extra
        if (overflow > 0) {
            Text(
                text = "+$overflow",
                style = MaterialTheme.typography.labelSmall,
                color = LunaTheme.colors.textMuted,
            )
        }
    }
}

@Composable
fun PluginMetaBar(
    author: String,
    version: String,
    enabled: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
    permissionCount: Int = 0,
) {
    val colors = LunaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PluginStatusDot(enabled = enabled, error = error)
        Text(
            text = stringResource(if (error) R.string.plugins_error_badge else if (enabled) R.string.plugins_running else R.string.plugins_stopped),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                error -> colors.error
                enabled -> colors.success
                else -> colors.textMuted
            },
        )
        Text("·", color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            text = "v$version",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            fontFamily = FontFamily.Monospace,
        )
        if (permissionCount > 0) {
            Text("·", color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                text = stringResource(R.string.plugins_permissions_n, permissionCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        } else {
            Text("·", color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
fun PluginRepoCard(
    plugin: InstalledPlugin,
    dir: File,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LunaTheme.colors
    val accent = when {
        plugin.record.lastError.isNotBlank() -> colors.error
        plugin.record.enabled -> colors.accent
        else -> colors.border
    }
    AppCard(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    PluginAvatar(manifest = plugin.manifest, pluginDir = dir, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PluginOwnerLine(author = plugin.manifest.author, id = plugin.manifest.id)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            plugin.manifest.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LunaSwitch(checked = plugin.record.enabled, onCheckedChange = onToggle)
                }
                if (plugin.manifest.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        plugin.manifest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PluginTopicRow(permissions = plugin.manifest.permissions)
                Spacer(Modifier.height(10.dp))
                PluginMetaBar(
                    author = plugin.manifest.author,
                    version = plugin.manifest.version,
                    enabled = plugin.record.enabled,
                    error = plugin.record.lastError.isNotBlank(),
                    permissionCount = plugin.manifest.permissions.size,
                )
                if (plugin.record.lastError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        plugin.record.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PluginRepoHeader(
    manifest: ValidatedManifest,
    dir: File,
    version: String = manifest.version,
    enabled: Boolean = false,
    error: Boolean = false,
    pngBytes: ByteArray? = null,
    extra: @Composable () -> Unit = {},
) {
    val colors = LunaTheme.colors
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (error) colors.error else if (enabled) colors.accent else colors.border),
            )
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    PluginAvatar(manifest = manifest, pluginDir = dir, size = 56.dp, pngBytes = pngBytes)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PluginOwnerLine(author = manifest.author, id = manifest.id)
                        Spacer(Modifier.height(4.dp))
                        Text(manifest.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(8.dp))
                        PluginMetaBar(
                            author = manifest.author,
                            version = version,
                            enabled = enabled,
                            error = error,
                            permissionCount = manifest.permissions.size,
                        )
                    }
                }
                if (manifest.description.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(manifest.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                if (manifest.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    PluginTopicRow(permissions = manifest.permissions)
                }
                extra()
            }
        }
    }
}

fun PluginPermission.titleRes(): Int = when (this) {
    PluginPermission.STORAGE -> R.string.plugins_permission_storage
    PluginPermission.UI_SETTINGS -> R.string.plugins_permission_ui
    PluginPermission.RULES_READ -> R.string.plugins_permission_rules_read
    PluginPermission.RULES_WRITE -> R.string.plugins_permission_rules_write
    PluginPermission.VPN_READ -> R.string.plugins_permission_vpn_read
    PluginPermission.VPN_CONTROL -> R.string.plugins_permission_vpn_control
    PluginPermission.NOTIFY -> R.string.plugins_permission_notify
    PluginPermission.HOSTS_WRITE -> R.string.plugins_permission_hosts_write
    PluginPermission.APP_READ -> R.string.plugins_permission_app_read
}

fun PluginPermission.bodyRes(): Int = when (this) {
    PluginPermission.STORAGE -> R.string.plugins_permission_storage_desc
    PluginPermission.UI_SETTINGS -> R.string.plugins_permission_ui_desc
    PluginPermission.RULES_READ -> R.string.plugins_permission_rules_read_desc
    PluginPermission.RULES_WRITE -> R.string.plugins_permission_rules_write_desc
    PluginPermission.VPN_READ -> R.string.plugins_permission_vpn_read_desc
    PluginPermission.VPN_CONTROL -> R.string.plugins_permission_vpn_control_desc
    PluginPermission.NOTIFY -> R.string.plugins_permission_notify_desc
    PluginPermission.HOSTS_WRITE -> R.string.plugins_permission_hosts_write_desc
    PluginPermission.APP_READ -> R.string.plugins_permission_app_read_desc
}
