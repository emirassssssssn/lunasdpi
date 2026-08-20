package com.lunasdev.lunasdpi.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.lunasdev.lunasdpi.data.HostsFile
import com.lunasdev.lunasdpi.plugin.PluginSecurity
import com.lunasdev.lunasdpi.plugin.PluginUiItem
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.LunaSwitch
import com.lunasdev.lunasdpi.ui.components.LunaTextField
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.components.SecondaryButton
import com.lunasdev.lunasdpi.ui.components.SectionHeader
import com.lunasdev.lunasdpi.ui.components.SettingRow
import com.lunasdev.lunasdpi.ui.settings.ChoiceChip
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.PluginsViewModel

@Composable
fun PluginSettingsScreen(
    pluginId: String,
    onBack: () -> Unit,
    vm: PluginsViewModel = viewModel(),
) {
    val page by vm.settingsPage.collectAsStateWithLifecycle()
    val plugins by vm.plugins.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val plugin = plugins.find { it.manifest.id == pluginId }
    val snackbar = remember { SnackbarHostState() }
    val textOverrides = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(pluginId) {
        textOverrides.clear()
        vm.loadSettings(pluginId)
    }
    LaunchedEffect(error) {
        val text = error ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeError()
    }
    LunaScaffold(
        title = page?.title ?: plugin?.manifest?.name ?: stringResource(R.string.plugins_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = page
            if (current == null) {
                Text(stringResource(R.string.plugins_settings_loading), color = LunaTheme.colors.textSecondary)
            } else {
                if (current.description.isNotBlank()) {
                    AppCard {
                        Text(current.description, style = MaterialTheme.typography.bodySmall, color = LunaTheme.colors.textSecondary)
                    }
                }
                current.sections.forEach { section ->
                    if (section.title.isNotBlank()) {
                        SectionHeader(section.title)
                    }
                    AppCard {
                        if (section.description.isNotBlank()) {
                            Text(
                                section.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = LunaTheme.colors.textMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        section.items.forEachIndexed { index, item ->
                            if (index > 0 && item !is PluginUiItem.Spacer && item !is PluginUiItem.Divider) {
                                Spacer(Modifier.height(10.dp))
                            }
                            PluginSettingItem(
                                item = item,
                                textValue = textOverrides[itemId(item)] ?: textDefault(item),
                                onText = { id, value ->
                                    textOverrides[id] = value
                                    vm.changeSetting(pluginId, id, value, reload = false)
                                },
                                onChange = { id, value ->
                                    vm.changeSetting(pluginId, id, value, reload = true)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PluginSettingItem(
    item: PluginUiItem,
    textValue: String,
    onText: (String, String) -> Unit,
    onChange: (String, Any) -> Unit,
) {
    val colors = LunaTheme.colors
    when (item) {
        is PluginUiItem.Note -> {
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        is PluginUiItem.Heading -> {
            val style = when (item.level) {
                1 -> MaterialTheme.typography.titleMedium
                2 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelLarge
            }
            Text(item.text, style = style)
        }
        is PluginUiItem.Divider -> {
            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))
        }
        is PluginUiItem.Spacer -> {
            Spacer(Modifier.height(8.dp))
        }
        is PluginUiItem.Badge -> {
            PluginTopicChip(text = item.text, tone = item.tone)
        }
        is PluginUiItem.Code -> {
            Text(
                item.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevated)
                    .padding(10.dp),
            )
        }
        is PluginUiItem.Alert -> {
            PluginAlertBox(text = item.text, tone = item.tone)
        }
        is PluginUiItem.KeyValue -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Text(item.value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        is PluginUiItem.Progress -> {
            val pct = (item.value.coerceIn(0f, 1f) * 100f).toInt()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text("$pct%", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.value.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.border,
            )
        }
        is PluginUiItem.Link -> {
            val context = LocalContext.current
            Text(
                item.text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.clickable {
                    if (PluginSecurity.validateHomepage(item.url) == null) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                    }
                },
            )
        }
        is PluginUiItem.Switch -> {
            SettingRow(title = item.title, description = item.body.takeIf { it.isNotBlank() }) {
                LunaSwitch(
                    checked = item.value,
                    onCheckedChange = { onChange(item.id, it) },
                    enabled = item.enabled,
                )
            }
        }
        is PluginUiItem.Checkbox -> {
            SettingRow(title = item.title, description = item.body.takeIf { it.isNotBlank() }) {
                Checkbox(
                    checked = item.value,
                    onCheckedChange = { onChange(item.id, it) },
                    enabled = item.enabled,
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                )
            }
        }
        is PluginUiItem.TextField -> {
            LunaTextField(
                value = textValue,
                onValueChange = { next ->
                    onText(item.id, next.take(HostsFile.MAX_TEXT_CHARS))
                },
                label = item.title,
                supportingText = item.placeholder.takeIf { it.isNotEmpty() },
                singleLine = !item.multiline,
                minLines = if (item.multiline) 8 else 1,
                fontFamily = if (item.multiline) FontFamily.Monospace else null,
                enabled = item.enabled,
            )
        }
        is PluginUiItem.NumberField -> {
            LunaTextField(
                value = textValue,
                onValueChange = { next ->
                    val filtered = next.filter { it.isDigit() || it == '.' || it == '-' }
                    onText(item.id, filtered)
                },
                label = item.title,
                singleLine = true,
                enabled = item.enabled,
            )
        }
        is PluginUiItem.Select -> {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                item.options.forEach { option ->
                    ChoiceChip(
                        selected = option == item.value,
                        label = option,
                        onClick = { if (item.enabled) onChange(item.id, option) },
                    )
                }
            }
        }
        is PluginUiItem.Slider -> {
            var local by remember(item.id, item.value) { mutableFloatStateOf(item.value) }
            Text("${item.title}  ${local.toInt()}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = local,
                onValueChange = { if (item.enabled) local = it },
                onValueChangeFinished = { if (item.enabled) onChange(item.id, local) },
                enabled = item.enabled,
                valueRange = item.min..item.max,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.border,
                ),
            )
        }
        is PluginUiItem.Button -> {
            if (item.destructive) {
                SecondaryButton(text = item.title, onClick = { onChange(item.id, true) }, enabled = item.enabled)
            } else {
                PrimaryButton(text = item.title, onClick = { onChange(item.id, true) }, enabled = item.enabled)
            }
        }
        is PluginUiItem.Stat -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.value,
                    style = MaterialTheme.typography.titleLarge,
                    color = pluginToneColor(item.tone, colors),
                )
                if (item.hint.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(item.hint, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
        }
        is PluginUiItem.ListItem -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    if (item.body.isNotBlank()) {
                        Text(item.body, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
                if (item.trailing.isNotBlank()) {
                    PluginTopicChip(text = item.trailing, tone = item.tone)
                }
            }
        }
        is PluginUiItem.Empty -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevated)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.text, style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                if (item.hint.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.hint, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
        }
        is PluginUiItem.Chips -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                item.labels.forEach { label ->
                    PluginTopicChip(text = label)
                }
            }
        }
        is PluginUiItem.Quote -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevated)
                    .padding(12.dp),
            ) {
                Text(item.text, style = MaterialTheme.typography.bodySmall)
                if (item.cite.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("— ${item.cite}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }
        is PluginUiItem.Fold -> {
            var open by remember(item.title, item.body) { mutableStateOf(item.open) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevated)
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = !open },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(if (open) "▾" else "▸", color = colors.textMuted)
                }
                if (open && item.body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(item.body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
        is PluginUiItem.Steps -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                item.labels.forEachIndexed { index, label ->
                    val step = index + 1
                    PluginTopicChip(
                        text = "$step. $label",
                        tone = when {
                            step == item.current -> "accent"
                            step < item.current -> "success"
                            else -> "info"
                        },
                    )
                }
            }
        }
        is PluginUiItem.Timeline -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item.events.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("•", color = colors.accent)
                        Text(event, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        is PluginUiItem.Score -> {
            val ratio = if (item.max <= 0f) 0f else (item.value / item.max).coerceIn(0f, 1f)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (item.max == 1f) "${(ratio * 100f).toInt()}%" else "${item.value.toInt()} / ${item.max.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
        }
        is PluginUiItem.Compare -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.leftLabel, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(item.left, style = MaterialTheme.typography.titleSmall)
                }
                Text("→", color = colors.textMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(item.rightLabel, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(item.right, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        is PluginUiItem.Faq -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(item.question, style = MaterialTheme.typography.titleSmall)
                if (item.answer.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.answer, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
        is PluginUiItem.Status -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.text, style = MaterialTheme.typography.titleSmall)
                    if (item.detail.isNotBlank()) {
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
                PluginTopicChip(text = item.tone, tone = item.tone)
            }
        }
    }
}

private fun itemId(item: PluginUiItem): String = when (item) {
    is PluginUiItem.Switch -> item.id
    is PluginUiItem.Checkbox -> item.id
    is PluginUiItem.TextField -> item.id
    is PluginUiItem.NumberField -> item.id
    is PluginUiItem.Select -> item.id
    is PluginUiItem.Slider -> item.id
    is PluginUiItem.Button -> item.id
    is PluginUiItem.Note,
    is PluginUiItem.Heading,
    is PluginUiItem.Divider,
    is PluginUiItem.Spacer,
    is PluginUiItem.Badge,
    is PluginUiItem.Code,
    is PluginUiItem.Alert,
    is PluginUiItem.KeyValue,
    is PluginUiItem.Progress,
    is PluginUiItem.Link,
    is PluginUiItem.Stat,
    is PluginUiItem.ListItem,
    is PluginUiItem.Empty,
    is PluginUiItem.Chips,
    is PluginUiItem.Quote,
    is PluginUiItem.Fold,
    is PluginUiItem.Steps,
    is PluginUiItem.Timeline,
    is PluginUiItem.Score,
    is PluginUiItem.Compare,
    is PluginUiItem.Faq,
    is PluginUiItem.Status,
    -> ""
}

private fun textDefault(item: PluginUiItem): String = when (item) {
    is PluginUiItem.TextField -> item.value
    is PluginUiItem.NumberField -> item.value.toString()
    else -> ""
}
