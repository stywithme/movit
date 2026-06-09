package com.movit.feature.account

sealed interface MovitAssessmentEvent {
    data class ParqAnswered(val questionIndex: Int, val yes: Boolean) : MovitAssessmentEvent
    data object ContinueToBodyScan : MovitAssessmentEvent
    data object CompleteBodyScan : MovitAssessmentEvent
    data object BrowseProgramsClicked : MovitAssessmentEvent
    data object GoHomeClicked : MovitAssessmentEvent
    data object BackClicked : MovitAssessmentEvent
}
