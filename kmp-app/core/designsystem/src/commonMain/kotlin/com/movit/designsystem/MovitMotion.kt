package com.movit.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object MovitMotion {
    const val DurationShort = 150
    const val DurationMedium = 250
    const val DurationLong = 350

    fun <T> tweenShort() = tween<T>(
        durationMillis = DurationShort,
        easing = FastOutSlowInEasing,
    )

    fun <T> tweenMedium() = tween<T>(
        durationMillis = DurationMedium,
        easing = FastOutSlowInEasing,
    )

    fun offsetTweenMedium() = tween<IntOffset>(
        durationMillis = DurationMedium,
        easing = FastOutSlowInEasing,
    )
}
