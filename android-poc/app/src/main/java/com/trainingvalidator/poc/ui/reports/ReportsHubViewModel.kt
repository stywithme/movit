package com.trainingvalidator.poc.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ReportsHubUiState {
    object Idle : ReportsHubUiState()
    object Loading : ReportsHubUiState()
    object NoActiveProgram : ReportsHubUiState()
    data class Success(val metrics: MetricsResponse) : ReportsHubUiState()
    data class Error(val message: String) : ReportsHubUiState()
}

/**
 * Shared ViewModel for the Reports Hub (HistoryFragment + its 4 child tab fragments).
 * Child fragments use activityViewModels() to observe the same instance.
 */
class ReportsHubViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<ReportsHubUiState>(ReportsHubUiState.Idle)
    val uiState: StateFlow<ReportsHubUiState> = _uiState.asStateFlow()

    fun loadMetrics() {
        if (_uiState.value is ReportsHubUiState.Loading) return
        _uiState.value = ReportsHubUiState.Loading
        viewModelScope.launch {
            try {
                val programRepo = ProgramRepository.getInstance(getApplication())
                withContext(Dispatchers.IO) { programRepo.initialize() }

                val activeProgram = programRepo.getActiveProgram()
                if (activeProgram == null) {
                    _uiState.value = ReportsHubUiState.NoActiveProgram
                    return@launch
                }

                val reportRepo = ReportRepository.getInstance(getApplication())
                val result = withContext(Dispatchers.IO) {
                    reportRepo.getProgramMetrics(activeProgram.id, includeChildren = true)
                }

                if (result != null && result.success) {
                    _uiState.value = ReportsHubUiState.Success(result)
                } else {
                    _uiState.value = ReportsHubUiState.NoActiveProgram
                }
            } catch (e: Exception) {
                _uiState.value = ReportsHubUiState.Error(e.message ?: "unknown")
            }
        }
    }
}
