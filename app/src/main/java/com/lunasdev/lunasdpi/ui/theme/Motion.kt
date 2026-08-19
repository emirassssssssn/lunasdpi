package com.lunasdev.lunasdpi.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class LunaMotion(
    val fast: Int = 150,
    val medium: Int = 220,
    val slow: Int = 300,
    val easing: Easing = FastOutSlowInEasing,
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
) {
    fun <T> fastSpec() = tween<T>(fast, easing = easing)
    fun <T> mediumSpec() = tween<T>(medium, easing = easing)
}

val LunaMotionTokens = LunaMotion()

@Immutable
object LunaSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val screen: Dp = 20.dp
    val card: Dp = 16.dp
    val touch: Dp = 48.dp
}
