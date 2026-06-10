package com.movit.feature.account

import androidx.lifecycle.ViewModel
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitAssessmentViewModel(
    private val repository: AssessmentRepository = defaultAssessmentRepository(),
    private val language: String = "en",
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitAssessmentUiState())
    val state: StateFlow<MovitAssessmentUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitAssessmentEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitAssessmentEffect> = _effects.asSharedFlow()

    fun onEvent(event: MovitAssessmentEvent) {
        when (event) {
            is MovitAssessmentEvent.ParqAnswered -> {
                _state.update { current ->
                    current.copy(
                        parqAnswers = current.parqAnswers + (event.questionIndex to event.yes),
                    )
                }
            }
            MovitAssessmentEvent.ContinueToBodyScan -> {
                val hasYes = _state.value.parqAnswers.values.any { it }
                if (hasYes) {
                    _effects.tryEmit(
                        MovitAssessmentEffect.ShowLocalizedMessage("assessment_parq_physician_warning"),
                    )
                }
                _state.update { it.copy(phase = AssessmentPhase.BodyScan) }
            }
            MovitAssessmentEvent.CompleteBodyScan -> {
                loadResults()
            }
            MovitAssessmentEvent.BrowseProgramsClicked -> {
                _effects.tryEmit(MovitAssessmentEffect.OpenExplore)
            }
            MovitAssessmentEvent.GoHomeClicked -> {
                _effects.tryEmit(MovitAssessmentEffect.OpenHome)
            }
            MovitAssessmentEvent.BackClicked -> {
                when (_state.value.phase) {
                    AssessmentPhase.PreScreening -> _effects.tryEmit(MovitAssessmentEffect.NavigateBack)
                    AssessmentPhase.BodyScan -> _state.update { it.copy(phase = AssessmentPhase.PreScreening) }
                    AssessmentPhase.Results -> _state.update { it.copy(phase = AssessmentPhase.BodyScan) }
                }
            }
        }
    }

    private fun loadResults() {
        workScope.launch {
            _state.update { it.copy(isLoadingResults = true) }
            val results = when (val result = repository.fetchLastResults(language)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> FakeAssessmentPreviewData.results
            }
            _state.update {
                it.copy(
                    phase = AssessmentPhase.Results,
                    isLoadingResults = false,
                    results = results,
                )
            }
        }
    }

    override fun onCleared() {
        workScope.cancel()
        super.onCleared()
    }
}
