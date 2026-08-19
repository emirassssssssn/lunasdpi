package com.lunasdev.lunasdpi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.ui.components.PrimaryButton
import com.lunasdev.lunasdpi.ui.theme.LunaTheme
import com.lunasdev.lunasdpi.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val pages = listOf(
        Triple(Icons.Outlined.Policy, stringResource(R.string.onboarding_1_title), stringResource(R.string.onboarding_1_body)),
        Triple(Icons.Outlined.Lan, stringResource(R.string.onboarding_2_title), stringResource(R.string.onboarding_2_body)),
        Triple(Icons.Outlined.Shield, stringResource(R.string.onboarding_3_title), stringResource(R.string.onboarding_3_body)),
    )
    val colors = LunaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            OnboardingPage(pages[page].first, pages[page].second, pages[page].third)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val selected = pager.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.accent else colors.border),
                )
            }
        }
        if (pager.currentPage < 2) {
            PrimaryButton(
                text = stringResource(R.string.next),
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.enable_protection),
                onClick = { vm.complete(onFinished) },
            )
        }
        TextButton(onClick = { vm.complete(onFinished) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.skip), color = colors.textSecondary)
        }
    }
}

@Composable
private fun OnboardingPage(icon: ImageVector, title: String, body: String) {
    val colors = LunaTheme.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.large)
                .background(colors.card)
                .border(1.dp, colors.border, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = colors.accent)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.textSecondary,
        )
    }
}
