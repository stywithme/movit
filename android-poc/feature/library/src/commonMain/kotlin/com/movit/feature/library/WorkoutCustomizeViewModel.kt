package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkoutCustomizeViewModel(
    private val workoutId: String,
    private val sessionRepository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutCustomizeUiState(isLoading = true))
    val state: StateFlow<WorkoutCustomizeUiState> = _state.asStateFlow()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        updateExercise(exerciseId) { it.copy(sets = sets.coerceIn(1, 10)) }
    }

    fun onRepsChanged(exerciseId: String, reps: Int) {
        updateExercise(exerciseId) { it.copy(reps = reps.coerceIn(1, 100)) }
    }

    fun deleteExercise(exerciseId: String) {
        _state.update { current ->
            val config = current.config ?: return@update current
            current.copy(config = config.copy(exercises = config.exercises.filter { it.id != exerciseId }))
        }
    }

    fun moveExercise(exerciseId: String, delta: Int) {
        _state.update { current ->
            val config = current.config ?: return@update current
            val exercises = config.exercises.toMutableList()
            val index = exercises.indexOfFirst { it.id == exerciseId }
            if (index < 0) return@update current
            val target = (index + delta).coerceIn(0, exercises.lastIndex)
            if (target == index) return@update current
            val item = exercises.removeAt(index)
            exercises.add(target, item)
            current.copy(config = config.copy(exercises = exercises))
        }
    }

    fun onRestOptionSelected(seconds: Int) {
        _state.update { current ->
            val config = current.config ?: return@update current
            current.copy(config = config.copy(restBetweenSetsSeconds = seconds))
        }
    }

    fun commitForRun(onReady: (WorkoutFlowConfigUi) -> Unit = {}) {
        val config = _state.value.config ?: return
        if (config.exercises.isEmpty()) return
        WorkoutFlowCache.put(config)
        onReady(config)
        persistScope.launch { persistCustomization(config) }
    }

    private suspend fun persistCustomization(config: WorkoutFlowConfigUi) {
        _state.update { it.copy(isSaving = true) }
        when (sessionRepository.saveFlowCustomization(workoutId, config)) {
            is AppResult.Success -> _state.update { it.copy(isSaving = false) }
            is AppResult.Failure -> _state.update { it.copy(isSaving = false) }
        }
    }

    private inline fun updateExercise(
        exerciseId: String,
        transform: (WorkoutFlowExerciseUi) -> WorkoutFlowExerciseUi,
    ) {
        _state.update { current ->
            val config = current.config ?: return@update current
            val updated = config.copy(
                exercises = config.exercises.map { exercise ->
                    if (exercise.id == exerciseId) transform(exercise) else exercise
                },
            )
            current.copy(config = updated)
        }
    }
}
