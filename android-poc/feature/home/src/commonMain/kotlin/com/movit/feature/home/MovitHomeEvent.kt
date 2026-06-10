package com.movit.feature.home

sealed interface MovitHomeEvent {
    data object RetryClicked : MovitHomeEvent
    data object StartTodayPlanClicked : MovitHomeEvent
    data object BodyScanClicked : MovitHomeEvent
    data object BrowseProgramsClicked : MovitHomeEvent
    data object ViewProgramClicked : MovitHomeEvent
    data object ViewPlanClicked : MovitHomeEvent
    data object ExploreClicked : MovitHomeEvent
    data object ReportsClicked : MovitHomeEvent
    data object ProfileClicked : MovitHomeEvent
    data object LevelCardClicked : MovitHomeEvent
    data class AlertClicked(val type: String) : MovitHomeEvent
    data class JourneyRowClicked(val rowId: String) : MovitHomeEvent
    data class RecentActivityClicked(val reportId: String) : MovitHomeEvent
    data class QuickActionClicked(val actionId: String) : MovitHomeEvent
}
