package com.lunasdev.lunasdpi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lunasdev.lunasdpi.R

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(
    size: Int,
    weight: FontWeight,
    line: Int,
    tracking: Float = 0f,
    color: Color = Color.Unspecified,
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    color = color,
)

fun lunaTypography(ink: Color): Typography = Typography(
    displaySmall = style(28, FontWeight.SemiBold, 34, -0.4f, ink),
    headlineMedium = style(26, FontWeight.SemiBold, 32, -0.3f, ink),
    headlineSmall = style(22, FontWeight.SemiBold, 28, -0.2f, ink),
    titleLarge = style(18, FontWeight.SemiBold, 24, -0.1f, ink),
    titleMedium = style(16, FontWeight.SemiBold, 22, 0.1f, ink),
    titleSmall = style(14, FontWeight.SemiBold, 20, 0.1f, ink),
    bodyLarge = style(15, FontWeight.Normal, 22, 0.1f, ink),
    bodyMedium = style(14, FontWeight.Normal, 20, 0.15f, ink),
    bodySmall = style(13, FontWeight.Normal, 18, 0.15f, ink),
    labelLarge = style(14, FontWeight.SemiBold, 20, 0.15f, ink),
    labelMedium = style(12, FontWeight.Medium, 16, 0.2f, ink),
    labelSmall = style(11, FontWeight.Medium, 14, 0.8f, ink),
)
