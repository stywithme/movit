package com.trainingvalidator.poc.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.network.ExerciseMetrics
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.network.MetricsSummary
import com.trainingvalidator.poc.network.ReportDashboardResponse
import com.trainingvalidator.poc.storage.AuthManager
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
    object Locked : ReportsHubUiState()
    object Empty : ReportsHubUiState()
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
                if (!AuthManager.isProUser(getApplication())) {
                    _uiState.value = ReportsHubUiState.Locked
                    return@launch
                }

                val reportRepo = ReportRepository.getInstance(getApplication())
                val dashboard = withContext(Dispatchers.IO) {
                    reportRepo.getReportsDashboard(
                        programId = activeProgram?.id,
                        period = "all",
                        source = "all"
                    )
                }
                val dashboardMetrics = dashboard.toMetricsResponse()

                when {
                    dashboard == null -> {
                        _uiState.value = ReportsHubUiState.Error("Unable to load reports right now")
                    }
                    !dashboard.success -> {
                        _uiState.value = ReportsHubUiState.Error(dashboard.error ?: "Unable to load reports right now")
                    }
                    dashboardMetrics?.summary.hasTrainingData() -> {
                        _uiState.value = ReportsHubUiState.Success(dashboardMetrics)
                    }
                    else -> {
                        _uiState.value = ReportsHubUiState.Empty
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ReportsHubUiState.Error(e.message ?: "unknown")
            }
        }
    }

    private fun MetricsSummary?.hasTrainingData(): Boolean {
        if (this == null) return false
        return (daysTrained ?: 0) > 0 ||
            (totalReps ?: 0) > 0 ||
            (totalTrainingTime ?: 0L) > 0L ||
            !weeks.isNullOrEmpty() ||
            !exercises.isNullOrEmpty()
    }

    private fun ReportDashboardResponse?.toMetricsResponse(): MetricsResponse? {
        val dashboard = this ?: return null
        val summary = dashboard.summary ?: return null
        return MetricsResponse(
            success = dashboard.success,
            scope = "dashboard",
            summary = MetricsSummary(
                programId = summary.programId,
                programProgress = summary.programProgress,
                daysTrained = summary.daysTrained,
                totalDays = null,
                totalTrainingTime = summary.totalTrainingTime,
                totalVolume = summary.totalVolume,
                totalReps = summary.totalReps,
                overallFormScore = summary.overallFormScore,
                currentStreak = summary.currentStreak,
                programGrade = summary.programGrade,
                improvementRate = null,
                bestWeekNumber = dashboard.records?.bestWeekNumber,
                weeklyFormScores = dashboard.trends?.formScoreByWeek,
                exercises = dashboard.exerciseBreakdown?.map { exercise ->
                    ExerciseMetrics(
                        exerciseSlug = exercise.exerciseSlug,
                        exerciseName = exercise.exerciseName,
                        averageFormScore = exercise.averageFormScore,
                        averageCompletionRate = 0f,
                        totalVolume = exercise.totalVolume,
                        sessionsCount = exercise.sessionsCount,
                        setsCompleted = 0,
                        setsPlanned = 0,
                        totalReps = exercise.totalReps,
                        bestSetNumber = null,
                        dropOffRate = 0f,
                        formRating = exercise.focusArea,
                        sets = null
                    )
                }
            ),
            comparison = null,
            insights = dashboard.insights,
            error = dashboard.error
        )
    }
}
