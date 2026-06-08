package com.movit.feature.explore

sealed interface MovitExploreEffect {
    data class NavigateToExercise(val id: String) : MovitExploreEffect
    data class ShowMessage(val message: String) : MovitExploreEffect
}
