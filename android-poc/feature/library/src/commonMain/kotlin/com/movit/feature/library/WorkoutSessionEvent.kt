package com.movit.feature.library

sealed interface WorkoutSessionEvent {
    data object ToggleEditMode : WorkoutSessionEvent
    data class ExerciseClicked(val exerciseId: String) : WorkoutSessionEvent
    data class RestClicked(val restId: String) : WorkoutSessionEvent
    data class OpenSwapSheet(val exerciseId: String) : WorkoutSessionEvent
    data object OpenAddExerciseSheet : WorkoutSessionEvent
    data class SwapQueryChanged(val query: String) : WorkoutSessionEvent
    data class AddExerciseQueryChanged(val query: String) : WorkoutSessionEvent
    data class SwapCandidateSelected(val slug: String) : WorkoutSessionEvent
    data class AddExerciseCandidateSelected(val slug: String) : WorkoutSessionEvent
    data class OpenEditSheet(val exerciseId: String) : WorkoutSessionEvent
    data class EditDraftChanged(val transform: (ExerciseEditDraft) -> ExerciseEditDraft) : WorkoutSessionEvent
    data class RestDurationChanged(val seconds: Int) : WorkoutSessionEvent
    data object SaveEditDetails : WorkoutSessionEvent
    data object SaveRestEdit : WorkoutSessionEvent
    data class DeleteExercise(val exerciseId: String) : WorkoutSessionEvent
    data class DeleteBlock(val blockId: String) : WorkoutSessionEvent
    data class MoveBlock(val sectionPhaseRole: String, val blockId: String, val delta: Int) : WorkoutSessionEvent
    data object AddRestBlock : WorkoutSessionEvent
    data object DismissSheet : WorkoutSessionEvent
    data class TogglePlannedWorkoutExpand(val workoutId: String) : WorkoutSessionEvent
    data object DismissCatchUpDialog : WorkoutSessionEvent
    data object OpenCatchUpDayClicked : WorkoutSessionEvent
    data object SkipWarmup : WorkoutSessionEvent
    data object SwitchEditToSwap : WorkoutSessionEvent
    data class SelectPlannedWorkout(val plannedWorkoutId: String) : WorkoutSessionEvent
    data object StartWorkoutClicked : WorkoutSessionEvent
    data class OpenExerciseClicked(val exerciseId: String) : WorkoutSessionEvent
    data object RetryClicked : WorkoutSessionEvent
    data object SnackbarConsumed : WorkoutSessionEvent
}
