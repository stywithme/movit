package com.movit.feature.account

sealed interface MovitAssessmentEffect {
    data object OpenExplore : MovitAssessmentEffect
    data object OpenHome : MovitAssessmentEffect
    data object NavigateBack : MovitAssessmentEffect
    data class ShowMessage(val message: String) : MovitAssessmentEffect
}
