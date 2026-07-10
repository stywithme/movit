package com.movit.feature.library

sealed interface ExercisePrepareEffect {
    data class StartTraining(val action: TrainingStartAction) : ExercisePrepareEffect
    /** Preview Start / Back-to-session — does not launch training. */
    data object ReturnToWorkoutSession : ExercisePrepareEffect
}
