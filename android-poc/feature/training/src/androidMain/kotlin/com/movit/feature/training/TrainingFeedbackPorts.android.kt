package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.movit.core.training.boundary.AndroidHapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer

@Composable
actual fun rememberTrainingFeedbackPorts(): TrainingFeedbackPorts {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        TrainingFeedbackPorts(
            speech = SpeechSynthesizer(context),
            haptics = AndroidHapticsPort(context),
        )
    }
}
