package com.movit.feature.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MovitAssessmentViewModel : ViewModel() {
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
                        MovitAssessmentEffect.ShowMessage(
                            "Consult a physician before training if you answered Yes to any question.",
                        ),
                    )
                }
                _state.update { it.copy(phase = AssessmentPhase.BodyScan) }
            }
            MovitAssessmentEvent.CompleteBodyScan -> {
                _state.update {
                    it.copy(
                        phase = AssessmentPhase.Results,
                        results = FakeAssessmentPreviewData.results,
                    )
                }
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
}
