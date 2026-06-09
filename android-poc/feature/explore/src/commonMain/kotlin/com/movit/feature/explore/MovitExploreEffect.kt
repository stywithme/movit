package com.movit.feature.explore

sealed interface MovitExploreEffect {
    data object OpenExercisesLibrary : MovitExploreEffect
    data object OpenWorkoutsLibrary : MovitExploreEffect
    data object OpenProgramList : MovitExploreEffect
    data class OpenProgramDetail(val programId: String) : MovitExploreEffect
    data class OpenWorkoutSession(val workoutId: String) : MovitExploreEffect
    data class OpenExercisePrepare(val exerciseId: String) : MovitExploreEffect
    data class NavigateToItem(val id: String, val type: ExploreItemType) : MovitExploreEffect
    /** @deprecated Use [NavigateToItem] */
    data class NavigateToExercise(val id: String) : MovitExploreEffect
    data class ShowMessage(val message: String) : MovitExploreEffect
}
