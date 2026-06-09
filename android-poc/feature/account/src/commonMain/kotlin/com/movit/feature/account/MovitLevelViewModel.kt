package com.movit.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitLevelViewModel(
    private val repository: LevelRepository = defaultLevelRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitLevelUiState(isLoading = true))
    val state: StateFlow<MovitLevelUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitLevelEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitLevelEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.fetchLevelProfile()) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        profile = result.value,
                        errorMessage = null,
                    )
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun onEvent(event: MovitLevelEvent) {
        when (event) {
            MovitLevelEvent.RetryClicked -> Unit
            is MovitLevelEvent.TabSelected -> {
                _state.update { it.copy(selectedTab = event.tab) }
            }
            MovitLevelEvent.StartScanClicked -> {
                _effects.tryEmit(MovitLevelEffect.OpenAssessment)
            }
            MovitLevelEvent.BrowseProgramsClicked -> {
                _effects.tryEmit(MovitLevelEffect.OpenExplore)
            }
        }
    }
}
