package com.movit.feature.explore

import com.movit.core.model.ExploreItemType

sealed interface MovitExploreEffect {
    data object OpenExercisesLibrary : MovitExploreEffect
    data object OpenWorkoutsLibrary : MovitExploreEffect
    data object OpenProgramList : MovitExploreEffect
    data class OpenProgramDetail(val programId: String) : MovitExploreEffect
    data class OpenWorkoutSession(val workoutId: String) : MovitExploreEffect
    data class OpenExerciseDetail(val exerciseId: String) : MovitExploreEffect
    data class NavigateToItem(val id: String, val type: ExploreItemType) : MovitExploreEffect
    /** @deprecated Use [NavigateToItem] */
    data class NavigateToExercise(val id: String) : MovitExploreEffect
    data class ShowMessage(val message: String) : MovitExploreEffect
}
