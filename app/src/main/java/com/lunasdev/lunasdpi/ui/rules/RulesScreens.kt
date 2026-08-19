package com.lunasdev.lunasdpi.ui.rules

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.DomainValidator
import com.lunasdev.lunasdpi.data.ServicePresets
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.DpiMode
import com.lunasdev.lunasdpi.plugin.PluginRuleIds
import com.lunasdev.lunasdpi.ui.components.AddDomainButton
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.DomainChip
import com.lunasdev.lunasdpi.ui.components.EmptyState
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.components.LunaSwitch
import com.lunasdev.lunasdpi.ui.components.LunaTextField
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.components.SectionHeader
import com.lunasdev.lunasdpi.ui.components.SettingRow
import com.lunasdev.lunasdpi.ui.components.StatusBadge
import com.lunasdev.lunasdpi.ui.components.StatusPulse
import com.lunasdev.lunasdpi.ui.components.StatusTone
import com.lunasdev.lunasdpi.ui.components.StrategyBadge
import com.lunasdev.lunasdpi.ui.format.labelRes
import com.lunasdev.lunasdpi.ui.settings.ChoiceChip as SettingsChoiceChip
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.RulesViewModel
import kotlinx.coroutines.launch

private enum class RuleFilter { All, Enabled, Disabled }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onEdit: (String?) -> Unit,
    onQuickAdd: () -> Unit,
    vm: RulesViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LunaTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RuleFilter.All) }
    var menuOpen by remember { mutableStateOf(false) }
    val presets = remember {
        listOf(
            ServicePresets.discord(),
            ServicePresets.gaming(),
            ServicePresets.socialMedia(),
            ServicePresets.messaging(),
        )
    }
    val presentIds = remember(rules) { rules.map { it.id }.toSet() }
    val presentNames = remember(rules) { rules.map { it.name.lowercase() }.toSet() }
    val visible = remember(rules, query, filter) {
        val needle = query.trim()
        rules.filter { rule ->
            val matchesFilter = when (filter) {
                RuleFilter.All -> true
                RuleFilter.Enabled -> rule.enabled
                RuleFilter.Disabled -> !rule.enabled
            }
            val matchesQuery = needle.isEmpty() ||
                rule.name.contains(needle, ignoreCase = true) ||
                rule.domains.any { it.contains(needle, ignoreCase = true) }
            matchesFilter && matchesQuery
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(vm.exportJson().toByteArray())
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: return@rememberLauncherForActivityResult
        vm.importJson(text)
    }
    fun removeRule(rule: DomainRule) {
        vm.delete(rule.id)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.rule_deleted, rule.name),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.upsert(rule)
            }
        }
    }
    LunaScaffold(
        title = stringResource(R.string.custom_bypass_rules),
        subtitle = stringResource(R.string.rules_enabled_count, rules.count { it.enabled }, rules.size),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.elevated,
                    contentColor = colors.textPrimary,
                    actionColor = colors.accent,
                )
            }
        },
        actions = {
            TextButton(
                onClick = onQuickAdd,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) { Text(stringResource(R.string.quick_add)) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.elevated,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.enable_all_rules)) },
                        onClick = {
                            menuOpen = false
                            vm.setAllEnabled(true)
                        },
                        colors = lunaMenuItemColors(),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.disable_all_rules)) },
                        onClick = {
                            menuOpen = false
                            vm.setAllEnabled(false)
                        },
                        colors = lunaMenuItemColors(),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_rules)) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("luna-dpi-rules.json")
                        },
                        colors = lunaMenuItemColors(),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_rules)) },
                        onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        colors = lunaMenuItemColors(),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null) },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_rule))
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
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.rules_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
                LunaTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.search_rules),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChoiceChip(
                        selected = filter == RuleFilter.All,
                        label = stringResource(R.string.filter_all),
                        onClick = { filter = RuleFilter.All },
                    )
                    SettingsChoiceChip(
                        selected = filter == RuleFilter.Enabled,
                        label = stringResource(R.string.filter_enabled),
                        onClick = { filter = RuleFilter.Enabled },
                    )
                    SettingsChoiceChip(
                        selected = filter == RuleFilter.Disabled,
                        label = stringResource(R.string.filter_disabled),
                        onClick = { filter = RuleFilter.Disabled },
                    )
                }
                SectionHeader(stringResource(R.string.presets))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    presets.forEach { preset ->
                        val selected = preset.id in presentIds || preset.name.lowercase() in presentNames
                        SettingsChoiceChip(
                            selected = selected,
                            label = preset.name,
                            onClick = { vm.addPreset(preset) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(
                            if (rules.isEmpty()) R.string.empty_rules_title else R.string.empty_rules_filter_title,
                        ),
                        body = stringResource(
                            if (rules.isEmpty()) R.string.empty_rules_body else R.string.empty_rules_filter_body,
                        ),
                        icon = Icons.Outlined.Add,
                        actionLabel = if (rules.isEmpty()) stringResource(R.string.add_rule) else null,
                        onAction = if (rules.isEmpty()) {{ onEdit(null) }} else null,
                    )
                }
            } else {
                items(visible, key = { it.id }) { rule ->
                    DismissibleRuleRow(
                        rule = rule,
                        onOpen = { onEdit(rule.id) },
                        onToggle = { vm.setEnabled(rule.id, it) },
                        onDuplicate = {
                            vm.duplicate(rule, context.getString(R.string.rule_copy_name, rule.name))
                        },
                        onDelete = { removeRule(rule) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleRuleRow(
    rule: DomainRule,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LunaTheme.colors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val settled = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(
                targetValue = if (settled) colors.error.copy(alpha = 0.22f) else colors.elevated,
                label = "dismiss-bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg, MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_rule), tint = colors.error)
            }
        },
    ) {
        RuleRow(
            rule = rule,
            onOpen = onOpen,
            onToggle = onToggle,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun RuleRow(
    rule: DomainRule,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val preview = remember(rule.domains) {
        rule.domains.take(2).joinToString(", ").ifBlank { "—" }
    }
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPulse(
                active = rule.enabled,
                color = if (rule.enabled) LunaTheme.colors.success else LunaTheme.colors.textMuted,
            )
            Spacer(Modifier.padding(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = LunaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StrategyBadge(rule.strategy)
                    if (PluginRuleIds.isPluginOwned(rule.id)) {
                        StatusBadge(text = stringResource(R.string.plugin_rule_badge), tone = StatusTone.Info)
                    }
                }
            }
            LunaSwitch(checked = rule.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = LunaTheme.colors.elevated,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_rule)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                        colors = lunaMenuItemColors(),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_rule)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        colors = lunaMenuItemColors(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleScreen(
    ruleId: String?,
    onBack: () -> Unit,
    vm: RulesViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val existing = rules.find { it.id == ruleId }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var domains by remember(existing?.id) { mutableStateOf(existing?.domains ?: emptyList()) }
    var draftDomain by remember { mutableStateOf("") }
    var strategy by remember(existing?.id) { mutableStateOf(existing?.strategy ?: DpiMode.AUTOMATIC) }
    var enabled by remember(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var showAdvanced by remember { mutableStateOf(false) }
    var frag by remember(existing?.id) { mutableStateOf(existing?.tcpFragmentation ?: true) }
    var fragSize by remember(existing?.id) { mutableStateOf((existing?.fragmentSize ?: 2).toString()) }
    var hostCase by remember(existing?.id) { mutableStateOf(existing?.httpHostCase ?: true) }
    var spacing by remember(existing?.id) { mutableStateOf(existing?.httpSpacing ?: false) }
    var method by remember(existing?.id) { mutableStateOf(existing?.httpMethodSpacing ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameRequired = stringResource(R.string.rule_name_required)
    val domainsRequired = stringResource(R.string.rule_domains_required)

    fun addDraft() {
        val raw = draftDomain.trim()
        if (raw.isEmpty()) return
        val reason = DomainValidator.rejectReason(raw)
        if (reason != null) {
            error = reason
            return
        }
        val normalized = DomainValidator.normalize(raw)
        if (normalized !in domains) domains = domains + normalized
        draftDomain = ""
        error = null
    }

    LunaScaffold(
        title = if (existing == null) stringResource(R.string.add_rule) else stringResource(R.string.edit_rule),
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_rule))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(stringResource(R.string.rule_name))
            LunaTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.rule_name))
            SectionHeader(stringResource(R.string.domains))
            domains.forEach { domain ->
                DomainChip(text = domain, onRemove = { domains = domains.filterNot { it == domain } }, modifier = Modifier.fillMaxWidth())
            }
            LunaTextField(
                value = draftDomain,
                onValueChange = { draftDomain = it },
                label = stringResource(R.string.domain),
                supportingText = stringResource(R.string.domain_hint),
            )
            AddDomainButton(onClick = { addDraft() }, modifier = Modifier.align(Alignment.End))
            SectionHeader(stringResource(R.string.strategy))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                DpiMode.entries.forEach { mode ->
                    SettingsChoiceChip(
                        selected = strategy == mode,
                        label = stringResource(mode.labelRes()),
                        onClick = { strategy = mode },
                    )
                }
            }
            AppCard {
                SettingRow(title = stringResource(R.string.enabled)) {
                    LunaSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) stringResource(R.string.hide_advanced) else stringResource(R.string.advanced))
            }
            if (showAdvanced) {
                AppCard {
                    SettingRow(title = stringResource(R.string.tcp_fragmentation)) {
                        LunaSwitch(checked = frag, onCheckedChange = { frag = it })
                    }
                    LunaTextField(value = fragSize, onValueChange = { fragSize = it }, label = stringResource(R.string.fragment_size))
                    SettingRow(title = stringResource(R.string.http_host_case)) {
                        LunaSwitch(checked = hostCase, onCheckedChange = { hostCase = it })
                    }
                    SettingRow(title = stringResource(R.string.http_spacing)) {
                        LunaSwitch(checked = spacing, onCheckedChange = { spacing = it })
                    }
                    SettingRow(title = stringResource(R.string.http_method_spacing)) {
                        LunaSwitch(checked = method, onCheckedChange = { method = it })
                    }
                }
            }
            error?.let { Text(it, color = LunaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(
                text = stringResource(R.string.save_rule),
                onClick = {
                    addDraft()
                    if (name.isBlank()) {
                        error = nameRequired
                        return@PrimaryButton
                    }
                    if (domains.isEmpty()) {
                        error = domainsRequired
                        return@PrimaryButton
                    }
                    vm.upsert(
                        DomainRule(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            enabled = enabled,
                            domains = domains,
                            strategy = strategy,
                            tcpFragmentation = frag,
                            fragmentSize = fragSize.toIntOrNull()?.coerceIn(1, 256) ?: 2,
                            httpHostCase = hostCase,
                            httpSpacing = spacing,
                            httpMethodSpacing = method,
                        ),
                    )
                    onBack()
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = LunaTheme.colors.card,
            titleContentColor = LunaTheme.colors.textPrimary,
            textContentColor = LunaTheme.colors.textSecondary,
            title = { Text(stringResource(R.string.delete_rule)) },
            text = { Text(stringResource(R.string.delete_rule_confirm, existing.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(existing.id)
                        confirmDelete = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = LunaTheme.colors.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LunaTheme.colors.textSecondary),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(onBack: () -> Unit, vm: RulesViewModel = viewModel()) {
    var domain by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(DpiMode.AUTOMATIC) }
    var error by remember { mutableStateOf<String?>(null) }
    val domainsRequired = stringResource(R.string.rule_domains_required)
    LunaScaffold(title = stringResource(R.string.quick_add), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LunaTextField(
                value = domain,
                onValueChange = { domain = it },
                label = stringResource(R.string.domain),
                supportingText = stringResource(R.string.domain_hint),
            )
            SectionHeader(stringResource(R.string.strategy))
            SettingsChoiceChip(
                selected = strategy == DpiMode.AUTOMATIC,
                label = stringResource(R.string.use_global_strategy),
                onClick = { strategy = DpiMode.AUTOMATIC },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DpiMode.BASIC, DpiMode.BALANCED, DpiMode.AGGRESSIVE).forEach { mode ->
                    SettingsChoiceChip(
                        selected = strategy == mode,
                        label = stringResource(mode.labelRes()),
                        onClick = { strategy = mode },
                    )
                }
            }
            error?.let { Text(it, color = LunaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(
                text = stringResource(R.string.save_rule),
                onClick = {
                    val reason = DomainValidator.rejectReason(domain)
                    if (reason != null) {
                        error = reason
                        return@PrimaryButton
                    }
                    val normalized = DomainValidator.normalize(domain)
                    if (normalized.isBlank()) {
                        error = domainsRequired
                        return@PrimaryButton
                    }
                    vm.upsert(
                        DomainRule(
                            name = normalized,
                            domains = listOf(normalized),
                            strategy = strategy,
                        ),
                    )
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun lunaMenuItemColors() = MenuDefaults.itemColors(
    textColor = LunaTheme.colors.textPrimary,
    leadingIconColor = LunaTheme.colors.textSecondary,
    trailingIconColor = LunaTheme.colors.textSecondary,
)
