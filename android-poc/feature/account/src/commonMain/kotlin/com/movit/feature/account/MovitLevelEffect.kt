package com.movit.feature.account

sealed interface MovitLevelEffect {
    data class OpenAssessment(val mode: String = "initial") : MovitLevelEffect
    data object OpenExplore : MovitLevelEffect
    data class ShowMessage(val message: String) : MovitLevelEffect
}
