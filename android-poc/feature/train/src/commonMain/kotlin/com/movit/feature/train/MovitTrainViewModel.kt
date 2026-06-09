package com.movit.feature.train

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

class MovitTrainViewModel(
    private val repository: TrainRepository = defaultTrainRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitTrainUiState(isLoading = true))
    val state: StateFlow<MovitTrainUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitTrainEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitTrainEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.getTrainDashboard()) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        dashboard = result.value,
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

    fun onEvent(event: MovitTrainEvent) {
        when (event) {
            MovitTrainEvent.RetryClicked -> Unit
            MovitTrainEvent.StartWorkoutClicked -> {
                val launchTarget = _state.value.dashboard
                    ?.today
                    ?.sessions
                    .orEmpty()
                    .firstOrNull { !it.isCompleted && it.launchTarget != null }
                    ?.launchTarget
                    ?: _state.value.dashboard
                        ?.today
                        ?.sessions
                        .orEmpty()
                        .firstOrNull { it.launchTarget != null }
                        ?.launchTarget

                if (launchTarget != null) {
                    _effects.tryEmit(MovitTrainEffect.OpenProgramWorkout(launchTarget))
                } else {
                    _effects.tryEmit(MovitTrainEffect.OpenSessionPreview)
                }
            }
            MovitTrainEvent.ExploreProgramsClicked -> {
                _effects.tryEmit(MovitTrainEffect.OpenExplore)
            }
            MovitTrainEvent.ViewReportClicked -> {
                _effects.tryEmit(MovitTrainEffect.OpenReports)
            }
            is MovitTrainEvent.QuickActionClicked -> {
                when (event.actionId) {
                    "explore" -> _effects.tryEmit(MovitTrainEffect.OpenExplore)
                    "reports" -> _effects.tryEmit(MovitTrainEffect.OpenReports)
                    else -> _effects.tryEmit(
                        MovitTrainEffect.ShowMessage("Training preferences arrive in a later phase."),
                    )
                }
            }
        }
    }
}
