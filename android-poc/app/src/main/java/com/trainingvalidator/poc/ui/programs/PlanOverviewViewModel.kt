package com.trainingvalidator.poc.ui.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.network.ActivePlanData
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ProgressionEntryData
import com.trainingvalidator.poc.network.ReassessmentData
import com.trainingvalidator.poc.storage.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlanOverviewData(
    val plan: ActivePlanData?,
    val progression: List<ProgressionEntryData>,
    val reassessments: List<ReassessmentData>
)

sealed class PlanOverviewUiState {
    object Loading : PlanOverviewUiState()
    object NoAuth : PlanOverviewUiState()
    data class Success(val data: PlanOverviewData) : PlanOverviewUiState()
    data class Error(val message: String) : PlanOverviewUiState()
}

class PlanOverviewViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<PlanOverviewUiState>(PlanOverviewUiState.Loading)
    val uiState: StateFlow<PlanOverviewUiState> = _uiState.asStateFlow()

    fun loadPlanData() {
        _uiState.value = PlanOverviewUiState.Loading
        viewModelScope.launch {
            try {
                val authHeader = AuthManager.getAuthHeader(getApplication())
                if (authHeader == null) {
                    _uiState.value = PlanOverviewUiState.NoAuth
                    return@launch
                }

                // Fetch all three endpoints concurrently
                val planDeferred = async(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getActivePlan(authHeader)
                }
                val progressionDeferred = async(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getProgressionHistory(authHeader)
                }
                val reassessmentDeferred = async(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getUpcomingReassessments(authHeader)
                }

                val planResponse = planDeferred.await()
                val progressionResponse = progressionDeferred.await()
                val reassessmentResponse = reassessmentDeferred.await()

                _uiState.value = PlanOverviewUiState.Success(
                    PlanOverviewData(
                        plan = planResponse.body()?.data,
                        progression = progressionResponse.body()?.data ?: emptyList(),
                        reassessments = reassessmentResponse.body()?.data ?: emptyList()
                    )
                )
            } catch (e: Exception) {
                _uiState.value = PlanOverviewUiState.Error(e.message ?: "unknown")
            }
        }
    }
}
