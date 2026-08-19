package com.lunasdev.lunasdpi.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.lunasdev.lunasdpi.LunaApplication
import com.lunasdev.lunasdpi.R
import com.lunasdev.lunasdpi.ui.apps.AppSelectorScreen
import com.lunasdev.lunasdpi.ui.components.LunaBackground
import com.lunasdev.lunasdpi.ui.diagnostics.DiagnosticsScreen
import com.lunasdev.lunasdpi.ui.home.HomeScreen
import com.lunasdev.lunasdpi.ui.navigation.Routes
import com.lunasdev.lunasdpi.ui.onboarding.OnboardingScreen
import com.lunasdev.lunasdpi.ui.privacy.PrivacyScreen
import com.lunasdev.lunasdpi.ui.plugins.PluginDetailScreen
import com.lunasdev.lunasdpi.ui.plugins.PluginHubScreen
import com.lunasdev.lunasdpi.ui.plugins.PluginImportScreen
import com.lunasdev.lunasdpi.ui.plugins.PluginSettingsScreen
import com.lunasdev.lunasdpi.ui.rules.EditRuleScreen
import com.lunasdev.lunasdpi.ui.rules.QuickAddScreen
import com.lunasdev.lunasdpi.ui.rules.RulesScreen
import com.lunasdev.lunasdpi.ui.settings.AdvancedSettingsScreen
import com.lunasdev.lunasdpi.ui.settings.DiscordAutoStartScreen
import com.lunasdev.lunasdpi.ui.settings.DnsSettingsScreen
import com.lunasdev.lunasdpi.ui.settings.DpiSettingsScreen
import com.lunasdev.lunasdpi.ui.settings.SettingsScreen
import com.lunasdev.lunasdpi.ui.settings.VpnSettingsScreen
import com.lunasdev.lunasdpi.ui.theme.LunaTheme

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val MainTabs = listOf(
    TabItem(Routes.Home, R.string.nav_home, Icons.Outlined.Home),
    TabItem(Routes.Rules, R.string.nav_rules, Icons.Outlined.Tune),
    TabItem(Routes.Diagnostics, R.string.nav_activity, Icons.AutoMirrored.Outlined.ShowChart),
    TabItem(Routes.Settings, R.string.nav_settings, Icons.Outlined.Settings),
)

private val MainTabRoutes = setOf(Routes.Home, Routes.Rules, Routes.Diagnostics, Routes.Settings)

@Composable
fun LunaApp() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as LunaApplication
    var ready by remember { mutableStateOf(false) }
    var onboardingDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        onboardingDone = app.settings.isOnboardingDone()
        ready = true
    }
    if (!ready) {
        LunaBackground { }
        return
    }
    val start = if (onboardingDone) Routes.Home else Routes.Onboarding
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = route in MainTabRoutes
    val colors = LunaTheme.colors
    LunaBackground {
        Scaffold(
            containerColor = colors.background,
            contentColor = colors.textPrimary,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showTabs) {
                    NavigationBar(
                        containerColor = colors.backgroundSecondary,
                        contentColor = colors.textSecondary,
                        tonalElevation = 0.dp,
                    ) {
                        MainTabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.accent,
                                    selectedTextColor = colors.textPrimary,
                                    indicatorColor = colors.accent.copy(alpha = 0.16f),
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .clipToBounds(),
            ) {
                NavHost(
                    navController = nav,
                    startDestination = start,
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                    sizeTransform = { null },
                ) {
                    composable(Routes.Onboarding) { }
                    composable(Routes.Home) { }
                    composable(Routes.Settings) { }
                    composable(Routes.Dpi) { }
                    composable(Routes.Dns) { }
                    composable(Routes.Vpn) { }
                    composable(Routes.Advanced) { }
                    composable(Routes.Rules) { }
                    composable(
                        route = Routes.EditRule,
                        arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "new" }),
                    ) { }
                    composable(Routes.QuickAdd) { }
                    composable(Routes.Diagnostics) { }
                    composable(Routes.Privacy) { }
                    composable(Routes.Apps) { }
                    composable(Routes.DiscordAutoStart) { }
                    composable(Routes.Plugins) { }
                    composable(Routes.PluginImport) { }
                    composable(
                        route = Routes.PluginDetail,
                        arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
                    ) { }
                    composable(
                        route = Routes.PluginSettings,
                        arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
                    ) { }
                }
                entry?.let { owner ->
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides owner,
                        LocalLifecycleOwner provides owner,
                        LocalSavedStateRegistryOwner provides owner,
                    ) {
                        key(owner.id) {
                            LunaScreen(nav = nav, entry = owner)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LunaScreen(nav: NavHostController, entry: NavBackStackEntry) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LunaTheme.colors.background),
    ) {
        when (entry.destination.route) {
        Routes.Onboarding -> OnboardingScreen(onFinished = {
            nav.navigate(Routes.Home) {
                popUpTo(Routes.Onboarding) { inclusive = true }
            }
        })
        Routes.Home -> HomeScreen(
            onMode = { nav.navigate(Routes.Dpi) },
            onDns = { nav.navigate(Routes.Dns) },
            onDiscordAutoStart = { nav.navigate(Routes.DiscordAutoStart) },
        )
        Routes.Settings -> SettingsScreen(
            onDpi = { nav.navigate(Routes.Dpi) },
            onDns = { nav.navigate(Routes.Dns) },
            onVpn = { nav.navigate(Routes.Vpn) },
            onAdvanced = { nav.navigate(Routes.Advanced) },
            onRules = {
                nav.navigate(Routes.Rules) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onDiagnostics = {
                nav.navigate(Routes.Diagnostics) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onPrivacy = { nav.navigate(Routes.Privacy) },
            onApps = { nav.navigate(Routes.Apps) },
            onDiscordAutoStart = { nav.navigate(Routes.DiscordAutoStart) },
            onPlugins = { nav.navigate(Routes.Plugins) },
        )
        Routes.Dpi -> DpiSettingsScreen(onBack = { nav.popBackStack() })
        Routes.Dns -> DnsSettingsScreen(onBack = { nav.popBackStack() })
        Routes.Vpn -> VpnSettingsScreen(onBack = { nav.popBackStack() })
        Routes.Advanced -> AdvancedSettingsScreen(onBack = { nav.popBackStack() })
        Routes.Rules -> RulesScreen(
            onEdit = { id -> nav.navigate(Routes.editRule(id)) },
            onQuickAdd = { nav.navigate(Routes.QuickAdd) },
        )
        Routes.EditRule -> EditRuleScreen(
            ruleId = entry.arguments?.getString("id")?.takeIf { it != "new" },
            onBack = { nav.popBackStack() },
        )
        Routes.QuickAdd -> QuickAddScreen(onBack = { nav.popBackStack() })
        Routes.Diagnostics -> DiagnosticsScreen()
        Routes.Privacy -> PrivacyScreen(onBack = { nav.popBackStack() })
        Routes.Apps -> AppSelectorScreen(onBack = { nav.popBackStack() })
        Routes.DiscordAutoStart -> DiscordAutoStartScreen(onBack = { nav.popBackStack() })
        Routes.Plugins -> PluginHubScreen(
            onBack = { nav.popBackStack() },
            onOpen = { id -> nav.navigate(Routes.pluginDetail(id)) },
            onImportReady = { nav.navigate(Routes.PluginImport) },
        )
        Routes.PluginImport -> PluginImportScreen(
            onBack = { nav.popBackStack() },
            onInstalled = {
                nav.popBackStack()
            },
        )
        Routes.PluginDetail -> PluginDetailScreen(
            pluginId = entry.arguments?.getString("id").orEmpty(),
            onBack = { nav.popBackStack() },
            onSettings = {
                val id = entry.arguments?.getString("id").orEmpty()
                nav.navigate(Routes.pluginSettings(id))
            },
            onUninstalled = { nav.popBackStack() },
        )
        Routes.PluginSettings -> PluginSettingsScreen(
            pluginId = entry.arguments?.getString("id").orEmpty(),
            onBack = { nav.popBackStack() },
        )
        else -> Unit
        }
    }
}
