package com.movit.feature.library

data class ExerciseDetailUiState(
    val isLoading: Boolean = false,
    val exercise: ExercisePrepareUi? = null,
    val errorMessage: String? = null,
)
