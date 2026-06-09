package com.movit.feature.library

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

data class WeeklyReportUiState(
    val isLoading: Boolean = false,
    val report: WeeklyReportUi? = null,
    val errorMessage: String? = null,
)

sealed interface WeeklyReportEffect {
    data object ShareRequested : WeeklyReportEffect
}

class WeeklyReportViewModel(
    private val programId: String,
    private val weekNumber: Int,
    private val repository: ProgramFlowRepository = defaultProgramFlowRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WeeklyReportUiState(isLoading = true))
    val state: StateFlow<WeeklyReportUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<WeeklyReportEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<WeeklyReportEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadWeeklyReport(programId, weekNumber)) {
            is AppResult.Success -> {
                _state.update { it.copy(isLoading = false, report = result.value) }
            }
            is AppResult.Failure -> {
                _state.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun onShareClicked() {
        _effects.tryEmit(WeeklyReportEffect.ShareRequested)
    }
}
