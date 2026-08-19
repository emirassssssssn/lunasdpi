package com.lunasdev.lunasdpi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = DarkLunaColors.accent,
    onPrimary = DarkLunaColors.onAccent,
    primaryContainer = Color(0xFF2B2448),
    onPrimaryContainer = Color(0xFFE4DCFF),
    secondary = DarkLunaColors.accentMuted,
    onSecondary = DarkLunaColors.onAccent,
    secondaryContainer = Color(0xFF242038),
    onSecondaryContainer = Color(0xFFD8D1FF),
    tertiary = DarkLunaColors.info,
    onTertiary = Color(0xFF041428),
    tertiaryContainer = Color(0xFF1B2E48),
    onTertiaryContainer = Color(0xFFD6E6FF),
    background = DarkLunaColors.background,
    onBackground = DarkLunaColors.textPrimary,
    surface = DarkLunaColors.backgroundSecondary,
    onSurface = DarkLunaColors.textPrimary,
    surfaceVariant = DarkLunaColors.card,
    onSurfaceVariant = DarkLunaColors.textSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = DarkLunaColors.background,
    surfaceContainerLow = DarkLunaColors.backgroundSecondary,
    surfaceContainer = DarkLunaColors.card,
    surfaceContainerHigh = DarkLunaColors.elevated,
    surfaceContainerHighest = DarkLunaColors.elevated,
    outline = DarkLunaColors.border,
    outlineVariant = DarkLunaColors.border,
    error = DarkLunaColors.error,
    onError = Color(0xFF2A0A10),
    inverseSurface = Color(0xFFE7E8ED),
    inverseOnSurface = Color(0xFF1A1C20),
    inversePrimary = DarkLunaColors.accentMuted,
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = LightLunaColors.accent,
    onPrimary = LightLunaColors.onAccent,
    primaryContainer = Color(0xFFE8E2FF),
    onPrimaryContainer = Color(0xFF2A2448),
    secondary = LightLunaColors.accentMuted,
    onSecondary = LightLunaColors.onAccent,
    secondaryContainer = Color(0xFFEDE9FF),
    onSecondaryContainer = Color(0xFF2A2448),
    tertiary = LightLunaColors.info,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E8FF),
    onTertiaryContainer = Color(0xFF0E2748),
    background = LightLunaColors.background,
    onBackground = LightLunaColors.textPrimary,
    surface = LightLunaColors.card,
    onSurface = LightLunaColors.textPrimary,
    surfaceVariant = LightLunaColors.elevated,
    onSurfaceVariant = LightLunaColors.textSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFDFB),
    surfaceContainerLow = LightLunaColors.card,
    surfaceContainer = LightLunaColors.elevated,
    surfaceContainerHigh = LightLunaColors.backgroundSecondary,
    surfaceContainerHighest = LightLunaColors.backgroundSecondary,
    outline = LightLunaColors.border,
    outlineVariant = LightLunaColors.border,
    error = LightLunaColors.error,
    onError = Color.White,
    inverseSurface = Color(0xFF2A2C30),
    inverseOnSurface = Color(0xFFF1F3F5),
    inversePrimary = LightLunaColors.accent,
    scrim = Color(0xFF000000),
)

object LunaTheme {
    val colors: LunaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLunaColors.current

    val motion: LunaMotion
        @Composable
        @ReadOnlyComposable
        get() = LunaMotionTokens
}

@Composable
fun LunaDpiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkLunaColors else LightLunaColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
            onDispose { }
        }
    }
    val typography = remember(palette.textPrimary) { lunaTypography(palette.textPrimary) }
    CompositionLocalProvider(
        LocalLunaColors provides palette,
        LocalContentColor provides palette.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = LunaShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.textPrimary) {
                content()
            }
        }
    }
}
