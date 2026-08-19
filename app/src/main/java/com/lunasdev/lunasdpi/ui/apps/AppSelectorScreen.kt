package com.lunasdev.lunasdpi.ui.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.data.model.PerAppMode
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.format.labelRes
import com.lunasdev.lunasdpi.ui.settings.ChoiceChip
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.SettingsViewModel

data class InstalledApp(
    val packageName: String,
    val label: String,
)

@Composable
fun AppSelectorScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LunaTheme.colors
    val apps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }
    LunaScaffold(title = stringResource(R.string.per_app_vpn), onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = LunaSpacing.screen)) {
            Text(
                stringResource(R.string.per_app_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PerAppMode.entries.forEach { mode ->
                    ChoiceChip(
                        selected = config.perAppMode == mode,
                        label = stringResource(mode.labelRes()),
                        onClick = { vm.update { it.copy(perAppMode = mode) } },
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val selected = app.packageName in config.perAppPackages
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                vm.update { current ->
                                    val next = current.perAppPackages.toMutableSet()
                                    if (checked) next.add(app.packageName) else next.remove(app.packageName)
                                    current.copy(perAppPackages = next.toList())
                                }
                            },
                            enabled = config.perAppMode != PerAppMode.ALL,
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accent,
                                uncheckedColor = colors.border,
                            ),
                        )
                        Column {
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}
