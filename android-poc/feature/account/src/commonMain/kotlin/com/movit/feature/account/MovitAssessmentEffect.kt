package com.movit.feature.account

sealed interface MovitAssessmentEffect {
    data object OpenExplore : MovitAssessmentEffect
    data object OpenHome : MovitAssessmentEffect
    data object NavigateBack : MovitAssessmentEffect
    /** Resolved in shell via [com.movit.resources.localizedString]. */
    data class ShowLocalizedMessage(val key: String) : MovitAssessmentEffect
}
