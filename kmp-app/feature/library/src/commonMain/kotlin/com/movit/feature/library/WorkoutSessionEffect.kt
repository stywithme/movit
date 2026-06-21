package com.movit.feature.library

sealed interface WorkoutSessionEffect {
    data class ShowSnackbar(val messageKey: String) : WorkoutSessionEffect
    data class StartWorkout(val firstExerciseId: String) : WorkoutSessionEffect
    data class OpenExercise(val exerciseId: String) : WorkoutSessionEffect
    data class SwitchWorkout(val sessionKey: String) : WorkoutSessionEffect
    data class OpenCatchUpDay(val sessionKey: String) : WorkoutSessionEffect
}
