package com.movit.feature.reports

sealed interface MovitReportsEvent {
    data object RetryClicked : MovitReportsEvent
    data object RefreshRequested : MovitReportsEvent
    data class TabSelected(val tab: ReportsTab) : MovitReportsEvent
    data class ExerciseReportClicked(val reportId: String) : MovitReportsEvent
    data object StartTrainingClicked : MovitReportsEvent
    data object UpgradeClicked : MovitReportsEvent
}
