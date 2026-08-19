package com.lunasdev.lunasdpi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF7C5CFF)
val AccentMuted = Color(0xFF6352D9)
val Connected = Color(0xFF35C98B)
val Caution = Color(0xFFE5B85C)
val Danger = Color(0xFFEF6675)
val Info = Color(0xFF6EA8FF)

@Immutable
data class LunaColors(
    val background: Color,
    val backgroundSecondary: Color,
    val card: Color,
    val elevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentMuted: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val overlay: Color,
)

val DarkLunaColors = LunaColors(
    background = Color(0xFF0B0D10),
    backgroundSecondary = Color(0xFF101318),
    card = Color(0xFF15191F),
    elevated = Color(0xFF1A1F26),
    border = Color(0xFF252B33),
    textPrimary = Color(0xFFF1F3F5),
    textSecondary = Color(0xFF9AA3AE),
    textMuted = Color(0xFF68717D),
    accent = Accent,
    accentMuted = AccentMuted,
    onAccent = Color(0xFFFFFFFF),
    success = Connected,
    warning = Caution,
    error = Danger,
    info = Info,
    overlay = Color(0x99000000),
)

val LightLunaColors = LunaColors(
    background = Color(0xFFF4F3F0),
    backgroundSecondary = Color(0xFFEBE9E4),
    card = Color(0xFFFFFDFB),
    elevated = Color(0xFFF7F6F3),
    border = Color(0xFFDDDAD3),
    textPrimary = Color(0xFF16181C),
    textSecondary = Color(0xFF5E666F),
    textMuted = Color(0xFF8B929A),
    accent = Accent,
    accentMuted = AccentMuted,
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF1F9A68),
    warning = Color(0xFFB5811F),
    error = Color(0xFFD14355),
    info = Color(0xFF3B7BD6),
    overlay = Color(0x66000000),
)

val LocalLunaColors = staticCompositionLocalOf { DarkLunaColors }
