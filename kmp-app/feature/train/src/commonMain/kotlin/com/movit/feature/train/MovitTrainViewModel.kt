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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MovitTrainViewModel(
    private val repository: TrainRepository = defaultTrainRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovitTrainUiState(isLoading = true))
    val state: StateFlow<MovitTrainUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitTrainEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitTrainEffect> = _effects.asSharedFlow()
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        val previous = _state.value
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        val weekIndex = resolveWeekIndex(dashboard, previous.selectedWeekNumber)
        val week = weeks.getOrElse(weekIndex) { dashboard.week }
        val daySelection = resolveDaySelection(week, previous.selectedDayNumber, previous.selectedDayIndex)

        _state.update {
            it.copy(
                isLoading = false,
                dashboard = dashboard,
                errorMessage = null,
                selectedWeekIndex = weekIndex,
                selectedWeekNumber = week.weekNumber,
                selectedDayIndex = daySelection?.first,
                selectedDayNumber = daySelection?.second,
            )
        }
        prefetchLaunchTarget(resolvePrimaryLaunchTarget(dashboard))
        daySelection?.let { (dayIndex, _) ->
            week.days.getOrNull(dayIndex)?.detail?.launchTarget?.let(::prefetchLaunchTarget)
        }
    }

    fun onEvent(event: MovitTrainEvent) {
        when (event) {
            MovitTrainEvent.RefreshRequested -> Unit
            MovitTrainEvent.RetryClicked -> {
                viewModelScope.launch { load(isRefresh = false) }
            }
            MovitTrainEvent.PreviousWeekClicked -> navigateWeek(-1)
            MovitTrainEvent.NextWeekClicked -> navigateWeek(+1)
            is MovitTrainEvent.DayClicked -> toggleDaySelection(event.index)
            is MovitTrainEvent.DayActionClicked -> handleDayAction(event.detail)
            is MovitTrainEvent.StartSession -> {
                _effects.tryEmit(MovitTrainEffect.OpenProgramWorkout(event.target))
            }
            is MovitTrainEvent.ViewReport -> {
                _effects.tryEmit(MovitTrainEffect.OpenReport(event.target))
            }
            MovitTrainEvent.ExploreProgramsClicked,
            MovitTrainEvent.WhatsNextClicked,
            -> {
                _effects.tryEmit(MovitTrainEffect.OpenProgramList)
            }
            MovitTrainEvent.AssessmentClicked -> {
                _effects.tryEmit(MovitTrainEffect.OpenAssessment)
            }
            MovitTrainEvent.ViewJourneyClicked -> {
                val program = _state.value.dashboard?.program
                if (program != null && program.id.isNotBlank()) {
                    _effects.tryEmit(
                        MovitTrainEffect.OpenReport(
                            TrainReportTargetUi.ProgramWeek(
                                programId = program.id,
                                weekNumber = program.weekNumber,
                            ),
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
        dashboard?.today?.primaryLaunchTarget?.let { return it }
        val sessions = dashboard?.today?.sessions.orEmpty()
        return sessions.firstOrNull { !it.isCompleted && it.launchTarget != null }?.launchTarget
            ?: sessions.firstOrNull { it.launchTarget != null }?.launchTarget
    }

    private fun prefetchLaunchTarget(target: TrainWorkoutLaunchUi?) {
        if (target == null) return
        val sessionKey = sessionKeyFor(target)
        if (prefetchedSessionKey == sessionKey) return
        prefetchedSessionKey = sessionKey

        prefetchScope.launch {
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

    private fun sessionKeyFor(target: TrainWorkoutLaunchUi): String =
        listOf(
            target.programId,
            target.weekNumber,
            target.dayNumber,
            target.plannedWorkoutId,
        ).joinToString(":")

    private fun navigateWeek(delta: Int) {
        val dashboard = _state.value.dashboard ?: return
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        if (weeks.size <= 1) return
        val nextIndex = (_state.value.selectedWeekIndex + delta).coerceIn(0, weeks.lastIndex)
        if (nextIndex == _state.value.selectedWeekIndex) return
        val nextWeek = weeks[nextIndex]
        val preservedDay = _state.value.selectedDayNumber?.let { dayNumber ->
            nextWeek.days.indexOfFirst { it.dayNumber.toIntOrNull() == dayNumber }.takeIf { it >= 0 }
        }
        _state.update {
            it.copy(
                selectedWeekIndex = nextIndex,
                selectedWeekNumber = nextWeek.weekNumber,
                selectedDayIndex = preservedDay,
                selectedDayNumber = preservedDay?.let { idx ->
                    nextWeek.days.getOrNull(idx)?.dayNumber?.toIntOrNull()
                },
            )
        }
        preservedDay?.let { dayIndex ->
            nextWeek.days.getOrNull(dayIndex)?.detail?.launchTarget?.let(::prefetchLaunchTarget)
        }
    }

    private fun toggleDaySelection(index: Int) {
        val dashboard = _state.value.dashboard ?: return
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        val week = weeks.getOrElse(_state.value.selectedWeekIndex) { dashboard.week }
        val day = week.days.getOrNull(index) ?: return
        val dayNumber = day.dayNumber.toIntOrNull()
        val currentlySelected = _state.value.selectedDayIndex == index

        _state.update {
            val nextIndex = if (currentlySelected) null else index
            it.copy(
                selectedDayIndex = nextIndex,
                selectedDayNumber = if (nextIndex == null) null else dayNumber,
            )
        }
        if (!currentlySelected) {
            day.detail?.launchTarget?.let(::prefetchLaunchTarget)
        }
    }

    private fun handleDayAction(detail: TrainWeekDayDetailUi) {
        detail.launchTarget?.let { target ->
            _effects.tryEmit(MovitTrainEffect.OpenProgramWorkout(target))
            return
        }
        detail.reportTarget?.let { target ->
            _effects.tryEmit(MovitTrainEffect.OpenReport(target))
        }
    }

    private fun resolveWeekIndex(dashboard: TrainDashboardUi, preservedWeekNumber: Int?): Int {
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        preservedWeekNumber?.let { weekNumber ->
            weeks.indexOfFirst { it.weekNumber == weekNumber }.takeIf { it >= 0 }?.let { return it }
        }
        val currentIndex = weeks.indexOfFirst { it.title == dashboard.week.title }
        return if (currentIndex >= 0) currentIndex else 0
    }

    private fun resolveDaySelection(
        week: TrainWeekPreviewUi,
        preservedDayNumber: Int?,
        fallbackDayIndex: Int?,
    ): Pair<Int, Int>? {
        preservedDayNumber?.let { dayNumber ->
            val index = week.days.indexOfFirst { it.dayNumber.toIntOrNull() == dayNumber }
            if (index >= 0) return index to dayNumber
        }
        fallbackDayIndex?.let { index ->
            val dayNumber = week.days.getOrNull(index)?.dayNumber?.toIntOrNull()
            if (dayNumber != null) return index to dayNumber
        }
        return null
    }

    override fun onCleared() {
        prefetchScope.cancel()
        super.onCleared()
    }
}
