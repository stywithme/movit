package com.movit.feature.account

sealed interface MovitLevelEffect {
    data object OpenAssessment : MovitLevelEffect
    data object OpenExplore : MovitLevelEffect
    data class ShowMessage(val message: String) : MovitLevelEffect
}
