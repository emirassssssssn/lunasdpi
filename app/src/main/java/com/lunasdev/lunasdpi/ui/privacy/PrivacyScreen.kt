package com.lunasdev.lunasdpi.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.ui.components.AppCard
import com.lunasdev.lunasdpi.ui.components.LunaScaffold
import com.lunasdev.lunasdpi.ui.theme.LunaSpacing
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.vpn.VpnController

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LunaScaffold(title = stringResource(R.string.privacy), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LunaSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineSmall)
            AppCard {
                Text(
                    VpnController.LOCAL_DISCLAIMER,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunaTheme.colors.textSecondary,
                )
            }
            Text(
                stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodyLarge,
                color = LunaTheme.colors.textSecondary,
            )
            Text(
                stringResource(R.string.not_a_vpn_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = LunaTheme.colors.textMuted,
            )
        }
    }
}
