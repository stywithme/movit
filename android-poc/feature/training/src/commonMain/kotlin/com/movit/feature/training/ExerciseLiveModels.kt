package com.movit.feature.training

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
    val configUnavailable: Boolean = false,
)
