package com.movit.feature.reports

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

class MovitReportsViewModel(
    private val repository: ReportsRepository = defaultReportsRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitReportsUiState(isLoading = true))
    val state: StateFlow<MovitReportsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitReportsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitReportsEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load(isRefresh = false) }
    }

    suspend fun load(isRefresh: Boolean = false) {
        _state.update {
            it.copy(
                isLoading = !isRefresh && it.dashboard == null,
                isRefreshing = isRefresh,
                errorMessage = null,
            )
        }
        when (val result = repository.getReportsDashboard()) {
            is AppResult.Success -> {
                val dashboard = result.value
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        dashboard = dashboard,
                        errorMessage = if (dashboard.hubState == ReportsHubState.Error) {
                            dashboard.errorMessage
                        } else {
                            null
                        },
                    )
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        dashboard = ReportsDashboardUi(
                            hubState = ReportsHubState.Error,
                            errorMessage = result.message,
                        ),
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun onEvent(event: MovitReportsEvent) {
        when (event) {
            MovitReportsEvent.RetryClicked -> Unit
            is MovitReportsEvent.TabSelected -> {
                _state.update { it.copy(selectedTab = event.tab) }
            }
            MovitReportsEvent.StartTrainingClicked -> {
                _effects.tryEmit(MovitReportsEffect.OpenTrain)
            }
            MovitReportsEvent.UpgradeClicked -> {
                _effects.tryEmit(MovitReportsEffect.OpenUpgrade)
            }
            is MovitReportsEvent.ExerciseReportClicked -> {
                _effects.tryEmit(MovitReportsEffect.OpenReportDetail(event.reportId))
            }
            MovitReportsEvent.RefreshRequested -> Unit
        }
    }
}
