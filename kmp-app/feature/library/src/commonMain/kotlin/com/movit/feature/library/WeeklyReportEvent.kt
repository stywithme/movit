package com.movit.feature.library

sealed interface WeeklyReportEvent {
    data class WeekSelected(val weekNumber: Int) : WeeklyReportEvent
    data object ShareClicked : WeeklyReportEvent
    data object RetryClicked : WeeklyReportEvent
}
