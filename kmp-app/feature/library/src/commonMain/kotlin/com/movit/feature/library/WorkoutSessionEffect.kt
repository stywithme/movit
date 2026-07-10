package com.movit.feature.library

sealed interface WorkoutSessionEffect {
    data class ShowSnackbar(val messageKey: String) : WorkoutSessionEffect
    data class StartWorkout(val firstExerciseId: String) : WorkoutSessionEffect
    /** Resume an existing open run — route must not create a new runId. */
    data class ResumeWorkout(val firstExerciseId: String, val runId: String) : WorkoutSessionEffect
    data class OpenExercise(
        val exerciseId: String,
        /** Draft id for preview mode — workoutId until Phase A run drafts land. */
        val runDraftId: String,
    ) : WorkoutSessionEffect
    data class SwitchWorkout(val sessionKey: String) : WorkoutSessionEffect
    data class OpenCatchUpDay(val sessionKey: String) : WorkoutSessionEffect
}
