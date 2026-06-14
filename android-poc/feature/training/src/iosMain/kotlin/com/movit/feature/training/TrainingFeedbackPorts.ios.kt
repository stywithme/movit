package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.NoOpHapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer

@Composable
actual fun rememberTrainingFeedbackPorts(language: String): TrainingFeedbackPorts {
    return remember(language) {
        TrainingFeedbackPorts(
            speech = SpeechSynthesizer(),
            haptics = NoOpHapticsPort(),
            audioPlayer = null,
        )
    }
}
