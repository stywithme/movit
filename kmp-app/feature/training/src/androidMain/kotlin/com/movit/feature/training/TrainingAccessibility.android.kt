package com.movit.feature.training

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPrefersReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { prefersReducedMotion(context) }
}

private fun prefersReducedMotion(context: Context): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}
