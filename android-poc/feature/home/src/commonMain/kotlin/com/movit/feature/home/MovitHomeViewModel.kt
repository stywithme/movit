package com.movit.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.resources.strings.HomeStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitHomeViewModel(
    private val repository: HomeRepository = defaultHomeRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitHomeUiState(isLoading = true))
    val state: StateFlow<MovitHomeUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitHomeEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitHomeEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.getHomeDashboard()) {
            is AppResult.Success -> {
                val dashboard = result.value
                _state.update {
                    it.copy(
                        isLoading = false,
                        userName = dashboard.userName,
                        greetingEyebrow = dashboard.greetingEyebrow,
                        greetingTitle = dashboard.greetingTitle,
                        greetingSubtitle = dashboard.greetingSubtitle,
                        metricTiles = dashboard.metricTiles,
                        levelCard = dashboard.levelCard,
                        alert = dashboard.alert,
                        activeProgram = dashboard.activeProgram,
                        todayPlan = dashboard.todayPlan,
                        showBodyScanCta = dashboard.showBodyScanCta,
                        showNoProgramEmpty = dashboard.showNoProgramEmpty,
                        journeyRows = dashboard.journeyRows,
                        recentActivities = dashboard.recentActivities,
                        progress = dashboard.progress.copy(
                            weeklyCompletionPercent = HomeSummaryCalculator.clampPercent(
                                dashboard.progress.weeklyCompletionPercent,
                            ),
                        ),
                        reportPreview = dashboard.reportPreview,
                        quickActions = dashboard.quickActions,
                        insightMessage = dashboard.insightMessage,
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

    fun onEvent(event: MovitHomeEvent) {
        when (event) {
            MovitHomeEvent.RetryClicked -> Unit
            MovitHomeEvent.StartTodayPlanClicked,
            MovitHomeEvent.BodyScanClicked,
            -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
            MovitHomeEvent.ExploreClicked,
            MovitHomeEvent.BrowseProgramsClicked,
            -> _effects.tryEmit(MovitHomeEffect.OpenExplore)
            MovitHomeEvent.ReportsClicked -> _effects.tryEmit(MovitHomeEffect.OpenReports)
            MovitHomeEvent.ProfileClicked -> _effects.tryEmit(MovitHomeEffect.OpenProfile)
            MovitHomeEvent.ViewProgramClicked -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
            is MovitHomeEvent.QuickActionClicked -> {
                when (event.actionId) {
                    "train" -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
                    "explore" -> _effects.tryEmit(MovitHomeEffect.OpenExplore)
                    "reports" -> _effects.tryEmit(MovitHomeEffect.OpenReports)
                    "profile" -> _effects.tryEmit(MovitHomeEffect.OpenProfile)
                    else -> viewModelScope.launch {
                        val language = if (MovitData.isInstalled) {
                            MovitData.requirePlatform().preferredLanguage()
                        } else {
                            "en"
                        }
                        val message = HomeStrings.load(language).actionUnavailable
                        _effects.emit(MovitHomeEffect.ShowMessage(message))
                    }
                }
            }
        }
    }
}
