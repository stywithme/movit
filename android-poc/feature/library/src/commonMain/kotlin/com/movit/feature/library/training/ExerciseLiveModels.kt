package com.movit.feature.library.training

import com.movit.core.training.engine.Phase

data class ExerciseLiveUiState(
    val exerciseName: String = "",
    val exerciseSlug: String = "",
    val targetReps: Int = 12,
    val repCount: Int = 0,
    val liveFormPercent: Int = 0,
    val averageFormPercent: Int = 0,
    val phaseLabel: String = "",
    val isCameraReady: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val bridgeUnavailable: Boolean = false,
)

fun Phase.toDisplayLabel(): String = when (this) {
    Phase.IDLE -> "Ready"
    Phase.START -> "Start"
    Phase.DOWN -> "Down"
    Phase.BOTTOM -> "Bottom"
    Phase.UP -> "Up"
    Phase.COUNT -> "Rep"
}
