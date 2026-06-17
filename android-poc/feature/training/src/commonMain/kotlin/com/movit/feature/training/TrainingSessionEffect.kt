package com.movit.feature.training

sealed interface TrainingSessionEffect {
    data class ViewReport(val reportId: String) : TrainingSessionEffect
    data class Finish(val isWorkoutFlowComplete: Boolean) : TrainingSessionEffect
    data object NavigateBack : TrainingSessionEffect
}
