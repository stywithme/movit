package com.movit.feature.library

sealed interface ExercisePrepareEvent {
    data class StartClicked(val workoutId: String? = null) : ExercisePrepareEvent
    data object DownloadConfigClicked : ExercisePrepareEvent
    data object SkipRest : ExercisePrepareEvent
    data object ToggleRestPause : ExercisePrepareEvent
    data object AddRestTime : ExercisePrepareEvent
    data class PoseVariantSelected(val index: Int) : ExercisePrepareEvent
    data object RetryClicked : ExercisePrepareEvent
}
