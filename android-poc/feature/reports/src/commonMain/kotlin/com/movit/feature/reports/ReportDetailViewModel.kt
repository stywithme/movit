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

data class ReportDetailUiState(
    val isLoading: Boolean = false,
    val report: ReportDetailUi? = null,
    val selectedPage: ReportDetailPage = ReportDetailPage.Overview,
    val errorMessage: String? = null,
)

class ReportDetailViewModel(
    private val reportId: String,
    private val repository: ReportDetailRepository = defaultReportDetailRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ReportDetailUiState(isLoading = true))
    val state: StateFlow<ReportDetailUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ReportDetailEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ReportDetailEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.getReportDetail(reportId)) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(isLoading = false, report = result.value)
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onPageSelected(page: ReportDetailPage) {
        _state.update { it.copy(selectedPage = page) }
    }

    fun onShareClicked() {
        _effects.tryEmit(ReportDetailEffect.ShareRequested)
    }

    fun onExportClicked() {
        _effects.tryEmit(ReportDetailEffect.ExportRequested)
    }
}

sealed interface ReportDetailEffect {
    data object ShareRequested : ReportDetailEffect
    data object ExportRequested : ReportDetailEffect
}
