package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun TrainingKeepScreenOnEffect() {
    DisposableEffect(Unit) {
        val app = UIApplication.sharedApplication
        val previous = app.isIdleTimerDisabled()
        app.setIdleTimerDisabled(true)
        onDispose {
            app.setIdleTimerDisabled(previous)
        }
    }
}
