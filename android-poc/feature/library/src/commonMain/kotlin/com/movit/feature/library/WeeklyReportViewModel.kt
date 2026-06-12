package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.resources.strings.ProgramFlowStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyReportUiState(
    val isLoading: Boolean = false,
    val report: WeeklyReportUi? = null,
    val weekSummaries: List<WeeklyReportWeekSummaryUi> = emptyList(),
    val selectedWeekNumber: Int = 1,
    val errorMessage: String? = null,
)

sealed interface WeeklyReportEffect {
    data class ShareRequested(
        val subject: String,
        val text: String,
    ) : WeeklyReportEffect
}

class WeeklyReportViewModel(
    private val programId: String,
    initialWeekNumber: Int,
    private val repository: ProgramFlowRepository = defaultProgramFlowRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        WeeklyReportUiState(isLoading = true, selectedWeekNumber = initialWeekNumber),
    )
    val state: StateFlow<WeeklyReportUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<WeeklyReportEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<WeeklyReportEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        val weekNumber = _state.value.selectedWeekNumber
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadWeeklyReport(programId, weekNumber)) {
            is AppResult.Success -> {
                val summaries = result.value.weekSummaries.ifEmpty {
                    when (val summariesResult = repository.loadWeekReportSummaries(programId)) {
                        is AppResult.Success -> summariesResult.value
                        is AppResult.Failure -> emptyList()
                    }
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        report = result.value,
                        weekSummaries = summaries,
                        selectedWeekNumber = result.value.weekNumber,
                    )
                }
            }
            is AppResult.Failure -> {
                _state.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun onWeekSelected(weekNumber: Int) {
        if (weekNumber == _state.value.selectedWeekNumber) return
        _state.update { it.copy(selectedWeekNumber = weekNumber) }
    }

    fun onShareClicked() {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            val language = if (MovitData.isInstalled) {
                MovitData.requirePlatform().preferredLanguage()
            } else {
                "en"
            }
            val strings = ProgramFlowStrings.load(language)
            val reps = formatReps(report.totalReps)
            val subject = strings.weekTitle(report.weekNumber) + " — " + report.programName
            val text = strings.shareBody(
                programName = report.programName,
                weekNumber = report.weekNumber,
                subtitle = report.heroSubtitle,
                completed = report.sessionsCompleted,
                planned = report.sessionsPlanned,
                formPercent = report.avgFormPercent,
                reps = reps,
            )
            _effects.tryEmit(WeeklyReportEffect.ShareRequested(subject = subject, text = text))
        }
    }

    private fun formatReps(reps: Int): String = when {
        reps >= 1000 -> "${reps / 1000}.${(reps % 1000) / 100}k"
        else -> reps.toString()
    }
}
