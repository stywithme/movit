package com.movit.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.resources.strings.TrainStrings
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
    private var prefetchedSessionKey: String? = null

    fun loadInitial() {
        viewModelScope.launch { load(isRefresh = false) }
    }

    suspend fun load(isRefresh: Boolean = false) {
        if (isRefresh) {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            when (val result = repository.getTrainDashboard()) {
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

        if (_state.value.dashboard == null) {
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

    private fun applyDashboard(dashboard: TrainDashboardUi) {
        val weekIndex = resolveWeekIndex(dashboard)
        _state.update {
            it.copy(
                isLoading = false,
                dashboard = dashboard,
                errorMessage = null,
                selectedWeekIndex = weekIndex,
                selectedDayIndex = null,
            )
        }
        prefetchPrimarySession(dashboard)
    }

    fun onEvent(event: MovitTrainEvent) {
        when (event) {
            MovitTrainEvent.RefreshRequested -> Unit
            MovitTrainEvent.RetryClicked -> Unit
            MovitTrainEvent.PreviousWeekClicked -> navigateWeek(-1)
            MovitTrainEvent.NextWeekClicked -> navigateWeek(+1)
            is MovitTrainEvent.DayClicked -> toggleDaySelection(event.index)
            MovitTrainEvent.DayActionClicked -> handleDayAction()
            MovitTrainEvent.StartWorkoutClicked -> {
                val launchTarget = resolvePrimaryLaunchTarget(_state.value.dashboard)

                if (launchTarget != null) {
                    _effects.tryEmit(MovitTrainEffect.OpenProgramWorkout(launchTarget))
                } else {
                    _effects.tryEmit(MovitTrainEffect.OpenProgramList)
                }
            }
            MovitTrainEvent.ExploreProgramsClicked,
            MovitTrainEvent.WhatsNextClicked,
            -> {
                _effects.tryEmit(MovitTrainEffect.OpenProgramList)
            }
            MovitTrainEvent.AssessmentClicked -> {
                _effects.tryEmit(MovitTrainEffect.OpenAssessment)
            }
            MovitTrainEvent.ViewReportClicked,
            MovitTrainEvent.ViewJourneyClicked,
            -> {
                val program = _state.value.dashboard?.program
                if (program != null && program.id.isNotBlank()) {
                    _effects.tryEmit(
                        MovitTrainEffect.OpenWeeklyReport(
                            programId = program.id,
                            weekNumber = program.weekNumber,
                        ),
                    )
                } else {
                    _effects.tryEmit(MovitTrainEffect.OpenReports)
                }
            }
            is MovitTrainEvent.StartProgramClicked -> {
                _effects.tryEmit(MovitTrainEffect.OpenProgramDetail(event.programId))
            }
            is MovitTrainEvent.QuickActionClicked -> {
                when (event.actionId) {
                    "explore" -> _effects.tryEmit(MovitTrainEffect.OpenExplore)
                    "reports" -> _effects.tryEmit(MovitTrainEffect.OpenReports)
                    else -> viewModelScope.launch {
                        val language = if (MovitData.isInstalled) {
                            MovitData.requirePlatform().preferredLanguage()
                        } else {
                            "en"
                        }
                        val message = TrainStrings.load(language).prefsLater
                        _effects.emit(MovitTrainEffect.ShowMessage(message))
                    }
                }
            }
        }
    }

    private fun resolvePrimaryLaunchTarget(dashboard: TrainDashboardUi?): TrainWorkoutLaunchUi? {
        val sessions = dashboard?.today?.sessions.orEmpty()
        return sessions.firstOrNull { !it.isCompleted && it.launchTarget != null }?.launchTarget
            ?: sessions.firstOrNull { it.launchTarget != null }?.launchTarget
    }

    private fun prefetchPrimarySession(dashboard: TrainDashboardUi) {
        val target = resolvePrimaryLaunchTarget(dashboard) ?: return
        val sessionKey = listOf(
            target.programId,
            target.weekNumber,
            target.dayNumber,
            target.plannedWorkoutId,
        ).joinToString(":")
        if (prefetchedSessionKey == sessionKey) return
        prefetchedSessionKey = sessionKey

        viewModelScope.launch {
            if (!MovitData.isInstalled) return@launch
            val cachedUserProgramId = MovitData.plan.readCachedActiveUserProgramId()
            val userProgramId = cachedUserProgramId ?: when (
                val refreshed = MovitData.plan.refreshActiveUserProgramId(target.programId)
            ) {
                is AppResult.Success -> refreshed.value
                is AppResult.Failure -> null
            }
            if (userProgramId.isNullOrBlank()) {
                prefetchedSessionKey = null
                return@launch
            }

            when (
                MovitData.workoutSession.syncEffectivePlan(
                    userProgramId = userProgramId,
                    weekNumber = target.weekNumber,
                    dayNumber = target.dayNumber,
                )
            ) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> prefetchedSessionKey = null
            }
        }
    }

    private fun navigateWeek(delta: Int) {
        val dashboard = _state.value.dashboard ?: return
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        if (weeks.size <= 1) return
        val nextIndex = (_state.value.selectedWeekIndex + delta).coerceIn(0, weeks.lastIndex)
        if (nextIndex != _state.value.selectedWeekIndex) {
            _state.update { it.copy(selectedWeekIndex = nextIndex, selectedDayIndex = null) }
        }
    }

    private fun toggleDaySelection(index: Int) {
        _state.update {
            val next = if (it.selectedDayIndex == index) null else index
            it.copy(selectedDayIndex = next)
        }
    }

    private fun selectedDayDetail(): TrainWeekDayDetailUi? {
        val state = _state.value
        val dashboard = state.dashboard ?: return null
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        val week = weeks.getOrElse(state.selectedWeekIndex) { dashboard.week }
        val dayIndex = state.selectedDayIndex ?: return null
        return week.days.getOrNull(dayIndex)?.detail
    }

    private fun handleDayAction() {
        val detail = selectedDayDetail() ?: return
        val launchTarget = detail.launchTarget
        when {
            launchTarget != null -> _effects.tryEmit(MovitTrainEffect.OpenProgramWorkout(launchTarget))
            detail.isCompleted -> {
                val program = _state.value.dashboard?.program
                if (program != null && program.id.isNotBlank()) {
                    _effects.tryEmit(
                        MovitTrainEffect.OpenWeeklyReport(
                            programId = program.id,
                            weekNumber = program.weekNumber,
                        ),
                    )
                } else {
                    _effects.tryEmit(MovitTrainEffect.OpenReports)
                }
            }
        }
    }

    private fun resolveWeekIndex(dashboard: TrainDashboardUi): Int {
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        val currentIndex = weeks.indexOfFirst { it.title == dashboard.week.title }
        return if (currentIndex >= 0) currentIndex else 0
    }
}
