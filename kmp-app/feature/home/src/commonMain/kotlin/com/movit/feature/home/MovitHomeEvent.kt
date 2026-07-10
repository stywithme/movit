package com.movit.feature.home

sealed interface MovitHomeEvent {
    data object RefreshRequested : MovitHomeEvent
    data object RetryClicked : MovitHomeEvent
    data object StartTodayPlanClicked : MovitHomeEvent
    data object BodyScanClicked : MovitHomeEvent
    data object BrowseProgramsClicked : MovitHomeEvent
    data class ViewProgramClicked(val programId: String) : MovitHomeEvent
    data object ViewPlanClicked : MovitHomeEvent
    data object ExploreClicked : MovitHomeEvent
    data object ReportsClicked : MovitHomeEvent
    data object ProfileClicked : MovitHomeEvent
    data object NotificationClicked : MovitHomeEvent
    data object LevelCardClicked : MovitHomeEvent
    data class AlertClicked(val type: String) : MovitHomeEvent
    data class JourneyRowClicked(val rowId: String) : MovitHomeEvent
    data class RecentActivityClicked(val reportId: String) : MovitHomeEvent
    data class QuickActionClicked(val actionId: String) : MovitHomeEvent
    data object CatchUpOpenClicked : MovitHomeEvent
    /** UX.6 — retry permanent outbox failures from Home alert. */
    data object RetryFailedUploadsClicked : MovitHomeEvent
}
