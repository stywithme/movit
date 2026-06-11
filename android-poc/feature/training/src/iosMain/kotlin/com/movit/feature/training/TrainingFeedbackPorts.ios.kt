package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.NoOpHapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer

@Composable
actual fun rememberTrainingFeedbackPorts(): TrainingFeedbackPorts {
    return remember {
        TrainingFeedbackPorts(
            speech = SpeechSynthesizer(),
            haptics = NoOpHapticsPort(),
        )
    }
}
