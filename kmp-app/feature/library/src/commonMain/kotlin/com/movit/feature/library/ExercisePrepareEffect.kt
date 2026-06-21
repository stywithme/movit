package com.movit.feature.library

sealed interface ExercisePrepareEffect {
    data class StartTraining(val action: TrainingStartAction) : ExercisePrepareEffect
}
