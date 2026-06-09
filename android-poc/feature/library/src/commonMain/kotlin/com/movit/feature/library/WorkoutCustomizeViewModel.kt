package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.movit.shared.AppResult
import kotlinx.coroutines.launch

class WorkoutCustomizeViewModel(
    private val workoutId: String,
    private val sessionRepository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutCustomizeUiState(isLoading = true))
    val state: StateFlow<WorkoutCustomizeUiState> = _state.asStateFlow()

    val restOptionsSeconds: List<Int> = listOf(45, 60, 90)

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val cached = WorkoutFlowCache.get(workoutId)
        if (cached != null) {
            _state.update { it.copy(isLoading = false, config = cached) }
            return
        }
        when (val result = sessionRepository.loadSession(workoutId)) {
            is AppResult.Success -> {
                val config = WorkoutFlowMapper.fromSession(result.value)
                _state.update { it.copy(isLoading = false, config = config) }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onSetsChanged(exerciseId: String, sets: Int) {
        _state.update { current ->
            val config = current.config ?: return@update current
            val updated = config.copy(
                exercises = config.exercises.map { exercise ->
                    if (exercise.id == exerciseId) exercise.copy(sets = sets.coerceIn(1, 10)) else exercise
                },
            )
            current.copy(config = updated)
        }
    }

    fun onRestOptionSelected(seconds: Int) {
        _state.update { current ->
            val config = current.config ?: return@update current
            current.copy(config = config.copy(restBetweenSetsSeconds = seconds))
        }
    }

    fun commitForRun(): WorkoutFlowConfigUi? {
        val config = _state.value.config ?: return null
        WorkoutFlowCache.put(config)
        return config
    }
}
