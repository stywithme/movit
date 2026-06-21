package com.movit.feature.training

import androidx.compose.runtime.Composable
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer
import com.movit.core.training.boundary.AudioFeedbackPlayer

data class TrainingFeedbackPorts(
    val speech: SpeechSynthesizer,
    val haptics: HapticsPort,
    val audioPlayer: AudioFeedbackPlayer? = null,
)

@Composable
expect fun rememberTrainingFeedbackPorts(language: String = "en"): TrainingFeedbackPorts
