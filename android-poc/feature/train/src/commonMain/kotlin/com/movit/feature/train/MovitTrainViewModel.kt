package com.movit.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
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

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.getTrainDashboard()) {
            is AppResult.Success -> {
                val dashboard = result.value
                val weekIndex = resolveWeekIndex(dashboard)
                _state.update {
                    it.copy(
                        isLoading = false,
                        dashboard = dashboard,
                        errorMessage = null,
                        selectedWeekIndex = weekIndex,
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

    fun onEvent(event: MovitTrainEvent) {
        when (event) {
            MovitTrainEvent.RetryClicked -> Unit
            MovitTrainEvent.PreviousWeekClicked -> navigateWeek(-1)
            MovitTrainEvent.NextWeekClicked -> navigateWeek(+1)
            MovitTrainEvent.StartWorkoutClicked -> {
                val launchTarget = _state.value.dashboard
                    ?.today
                    ?.sessions
                    .orEmpty()
                    .firstOrNull { !it.isCompleted && it.launchTarget != null }
                    ?.launchTarget
                    ?: _state.value.dashboard
                        ?.today
                        ?.sessions
                        .orEmpty()
                        .firstOrNull { it.launchTarget != null }
                        ?.launchTarget

                if (launchTarget != null) {
                    _effects.tryEmit(MovitTrainEffect.OpenProgramWorkout(launchTarget))
                } else {
                    _effects.tryEmit(MovitTrainEffect.OpenSessionPreview)
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
                _effects.tryEmit(
                    MovitTrainEffect.OpenProgramWeekPlan(
                        programId = event.programId,
                        weekNumber = 1,
                    ),
                )
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

    private fun navigateWeek(delta: Int) {
        val dashboard = _state.value.dashboard ?: return
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        if (weeks.size <= 1) return
        val nextIndex = (_state.value.selectedWeekIndex + delta).coerceIn(0, weeks.lastIndex)
        if (nextIndex != _state.value.selectedWeekIndex) {
            _state.update { it.copy(selectedWeekIndex = nextIndex) }
        }
    }

    private fun resolveWeekIndex(dashboard: TrainDashboardUi): Int {
        val weeks = dashboard.weekOptions.ifEmpty { listOf(dashboard.week) }
        val currentIndex = weeks.indexOfFirst { it.title == dashboard.week.title }
        return if (currentIndex >= 0) currentIndex else 0
    }
}
