package com.movit.feature.train

sealed interface MovitTrainEvent {
    data object RefreshRequested : MovitTrainEvent
    data object StartWorkoutClicked : MovitTrainEvent
    data object ExploreProgramsClicked : MovitTrainEvent
    data object AssessmentClicked : MovitTrainEvent
    data object ViewReportClicked : MovitTrainEvent
    data object ViewJourneyClicked : MovitTrainEvent
    data object WhatsNextClicked : MovitTrainEvent
    data object RetryClicked : MovitTrainEvent
    data object PreviousWeekClicked : MovitTrainEvent
    data object NextWeekClicked : MovitTrainEvent
    data class StartProgramClicked(val programId: String) : MovitTrainEvent
    data class QuickActionClicked(val actionId: String) : MovitTrainEvent
}
