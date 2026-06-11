package com.movit.feature.training

import androidx.compose.runtime.Composable
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer

data class TrainingFeedbackPorts(
    val speech: SpeechSynthesizer,
    val haptics: HapticsPort,
)

@Composable
expect fun rememberTrainingFeedbackPorts(): TrainingFeedbackPorts
