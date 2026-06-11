package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.movit.feature.library.training.KmpTrainingSlugs
import com.movit.shared.AppResult
import kotlinx.coroutines.launch

class WorkoutRunViewModel(
    private val workoutId: String,
    private val sessionRepository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutRunUiState(isLoading = true))
    val state: StateFlow<WorkoutRunUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val config = WorkoutFlowCache.get(workoutId)
            ?: loadFromSession()
        if (config == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Workout not found.")
            }
            return
        }
        val insight = WorkoutRunPreviewInsights.forExercise(
            config.exercises.getOrNull(1)?.exerciseSlug ?: config.exercises.firstOrNull()?.exerciseSlug,
        )
        _state.update {
            it.copy(
                isLoading = false,
                config = config,
                currentExerciseIndex = 0,
                currentSet = 1,
                previousFormPercent = insight?.formPercent,
                previousFormTip = insight?.tip,
            )
        }
    }

    private suspend fun loadFromSession(): WorkoutFlowConfigUi? =
        when (val result = sessionRepository.loadSession(workoutId)) {
            is AppResult.Success -> WorkoutFlowMapper.fromSession(result.value)
            is AppResult.Failure -> null
        }

    fun trainingStartAction(): TrainingStartAction? {
        val exercise = _state.value.currentExercise ?: return null
        val slug = exercise.exerciseSlug
        return if (KmpTrainingSlugs.supports(slug)) {
            TrainingStartAction.KmpLive(
                slug = slug,
                exerciseName = exercise.name,
                targetReps = exercise.reps ?: 12,
                workoutId = workoutId,
            )
        } else {
            TrainingStartAction.Legacy(slug)
        }
    }

    /** @deprecated Use [trainingStartAction]; kept for tests. */
    fun legacyFileNameForStart(): String? = _state.value.currentExercise?.exerciseSlug
}

private object WorkoutRunPreviewInsights {
    data class Insight(val formPercent: Int, val tip: String)

    fun forExercise(slug: String?): Insight? = when (slug) {
        "barbell-squat", "bodyweight-squat" -> Insight(92, "Great depth — keep the same tempo.")
        "romanian-deadlift", "rdl" -> Insight(94, "Great hip hinge — keep the same tempo.")
        else -> Insight(90, "Solid form — maintain controlled reps.")
    }
}
