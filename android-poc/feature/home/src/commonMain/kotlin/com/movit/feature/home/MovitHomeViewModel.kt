package com.movit.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
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
        viewModelScope.launch { load(isRefresh = false) }
    }

    suspend fun load(isRefresh: Boolean = false) {
        if (isRefresh) {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            when (val result = repository.getHomeDashboard()) {
                is AppResult.Success -> {
                    applyDashboard(result.value)
                    _state.update { it.copy(isRefreshing = false) }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isRefreshing = false, errorMessage = result.message)
                }
            }
            return
        }

        if (_state.value.userName == null) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        }
        repository.observeDashboard().collect { cacheState ->
            when (cacheState) {
                is CacheState.Cached -> applyDashboard(cacheState.value)
                is CacheState.Fresh -> applyDashboard(cacheState.value)
                is CacheState.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = cacheState.message)
                }
                is CacheState.Loading -> Unit
            }
        }
    }

    private fun applyDashboard(dashboard: HomeDashboardUi) {
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
                catchUp = dashboard.catchUp,
            )
        }
    }

    fun onEvent(event: MovitHomeEvent) {
        when (event) {
            MovitHomeEvent.RefreshRequested -> Unit
            MovitHomeEvent.RetryClicked -> Unit
            MovitHomeEvent.StartTodayPlanClicked -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
            MovitHomeEvent.BodyScanClicked -> _effects.tryEmit(MovitHomeEffect.OpenAssessment)
            MovitHomeEvent.LevelCardClicked -> _effects.tryEmit(MovitHomeEffect.OpenLevel)
            MovitHomeEvent.ExploreClicked,
            MovitHomeEvent.BrowseProgramsClicked,
            -> _effects.tryEmit(MovitHomeEffect.OpenExplore)
            MovitHomeEvent.ReportsClicked -> _effects.tryEmit(MovitHomeEffect.OpenReports)
            MovitHomeEvent.ProfileClicked -> _effects.tryEmit(MovitHomeEffect.OpenProfile)
            is MovitHomeEvent.ViewProgramClicked -> {
                if (event.programId.isNotBlank()) {
                    _effects.tryEmit(MovitHomeEffect.OpenProgramDetail(event.programId))
                }
            }
            MovitHomeEvent.ViewPlanClicked -> _effects.tryEmit(MovitHomeEffect.OpenLevel)
            is MovitHomeEvent.AlertClicked -> handleAlert(event.type)
            is MovitHomeEvent.JourneyRowClicked -> handleJourneyRow(event.rowId)
            is MovitHomeEvent.RecentActivityClicked -> {
                if (event.reportId.isNotBlank()) {
                    _effects.tryEmit(MovitHomeEffect.OpenReportDetail(event.reportId))
                } else {
                    _effects.tryEmit(MovitHomeEffect.OpenReports)
                }
            }
            is MovitHomeEvent.QuickActionClicked -> {
                when (event.actionId) {
                    "train" -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
                    "explore" -> _effects.tryEmit(MovitHomeEffect.OpenExplore)
                    "reports" -> _effects.tryEmit(MovitHomeEffect.OpenReports)
                    "profile" -> _effects.tryEmit(MovitHomeEffect.OpenProfile)
                    "level" -> _effects.tryEmit(MovitHomeEffect.OpenLevel)
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
            MovitHomeEvent.CatchUpOpenClicked -> {
                val catchUp = _state.value.catchUp ?: return
                _effects.tryEmit(
                    MovitHomeEffect.OpenCatchUpDay(
                        programId = catchUp.programId,
                        weekNumber = catchUp.weekNumber,
                        dayNumber = catchUp.dayNumber,
                    ),
                )
            }
        }
    }

    private fun handleAlert(type: String) {
        when (type) {
            "reassessment_due" -> _effects.tryEmit(MovitHomeEffect.OpenAssessment)
            "progression_applied" -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
            else -> Unit
        }
    }

    private fun handleJourneyRow(rowId: String) {
        when (rowId) {
            "reassessment" -> _effects.tryEmit(MovitHomeEffect.OpenAssessment)
            "timeline" -> _effects.tryEmit(MovitHomeEffect.OpenLevel)
            else -> _effects.tryEmit(MovitHomeEffect.OpenTrain)
        }
    }
}
