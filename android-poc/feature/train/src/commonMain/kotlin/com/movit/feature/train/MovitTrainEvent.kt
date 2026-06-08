package com.movit.feature.train

sealed interface MovitTrainEvent {
    data object StartWorkoutClicked : MovitTrainEvent
    data object ExploreProgramsClicked : MovitTrainEvent
    data object ViewReportClicked : MovitTrainEvent
    data object RetryClicked : MovitTrainEvent
    data class QuickActionClicked(val actionId: String) : MovitTrainEvent
}
