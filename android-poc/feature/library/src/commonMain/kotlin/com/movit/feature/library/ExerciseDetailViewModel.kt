package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ExerciseDetailUiState(isLoading = true))
    val state: StateFlow<ExerciseDetailUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        if (MovitData.isInstalled) {
            MovitData.bootstrapLocalCaches()
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val exercise = ExerciseContentMapper.loadExercise(exerciseId, repository)
        if (exercise == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "prepare_not_found")
            }
        } else {
            _state.update {
                it.copy(isLoading = false, exercise = exercise)
            }
        }
    }
}
