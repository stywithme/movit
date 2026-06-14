package com.movit.feature.training

import androidx.compose.runtime.Composable

/** Keeps the device screen awake while a training session is visible (legacy TrainingActivity parity). */
@Composable
expect fun TrainingKeepScreenOnEffect()
