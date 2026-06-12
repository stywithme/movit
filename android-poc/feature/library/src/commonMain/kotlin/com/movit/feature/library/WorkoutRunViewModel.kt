package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.movit.shared.AppResult
import kotlinx.coroutines.launch

class WorkoutRunViewModel(
    private val workoutId: String,
    private val sessionRepository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutRunUiState(isLoading = true))
    val state: StateFlow<WorkoutRunUiState> = _state.asStateFlow()
    private var sessionContext: WorkoutSessionContextUi? = null

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
        WorkoutFlowCache.put(config)
        val progress = WorkoutRunProgressStore.read(workoutId)
        val currentSlug = config.exercises.getOrNull(progress.exerciseIndex)?.exerciseSlug
            ?: config.exercises.firstOrNull()?.exerciseSlug
        val insight = WorkoutFormInsightLoader.load(
            programId = sessionContext?.programSlug ?: sessionContext?.programId,
            exerciseSlug = currentSlug,
        )
        _state.update {
            it.copy(
                isLoading = false,
                config = config,
                currentExerciseIndex = progress.exerciseIndex,
                currentSet = progress.currentSet,
                previousFormPercent = insight?.formPercent,
                previousFormTip = insight?.tip,
            )
        }
    }

    private suspend fun loadFromSession(): WorkoutFlowConfigUi? =
        when (val result = sessionRepository.loadSession(workoutId)) {
            is AppResult.Success -> {
                sessionContext = result.value.context
                WorkoutFlowMapper.fromSession(result.value)
            }
            is AppResult.Failure -> null
        }

    fun legacyFileNameForStart(): String? = _state.value.currentExercise?.exerciseSlug
}
