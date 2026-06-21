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
    assessmentMode: String = "initial",
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitAssessmentUiState(assessmentMode = assessmentMode))
    val state: StateFlow<MovitAssessmentUiState> = _state.asStateFlow()
    private var scanEngine: AssessmentBodyScanEngine? = null
    private var pendingScanResult: AssessmentBodyScanResult? = null

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
                startBodyScan()
            }
            MovitAssessmentEvent.BodyScanCameraReady -> {
                _state.update { it.copy(scanErrorMessage = null) }
            }
            MovitAssessmentEvent.BodyScanGuidedModeStarted -> {
                _state.update { it.copy(isGuidedScan = true, scanErrorMessage = null) }
            }
            is MovitAssessmentEvent.BodyScanFrameReceived -> {
                val update = scanEngine?.ingest(event.frame) ?: return
                _state.update {
                    it.copy(
                        scanProgressPercent = update.progressPercent,
                        scanMovementIndex = update.movementIndex,
                        isPoseDetected = update.poseDetected,
                        isScanComplete = update.isComplete,
                        scanErrorMessage = if (update.poseDetected) null else it.scanErrorMessage,
                    )
                }
            }
            is MovitAssessmentEvent.BodyScanError -> {
                _state.update { it.copy(scanErrorMessage = event.message) }
            }
            MovitAssessmentEvent.CompleteBodyScan -> {
                completeBodyScan()
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

    private fun startBodyScan() {
        workScope.launch {
            _state.update {
                it.copy(
                    phase = AssessmentPhase.BodyScan,
                    isResolvingTemplate = true,
                    scanProgressPercent = 0,
                    scanMovementIndex = 0,
                    isScanComplete = false,
                    isPoseDetected = false,
                    scanErrorMessage = null,
                )
            }
            val mode = _state.value.assessmentMode.ifBlank { "initial" }
            val template = when (val result = repository.resolveTemplate(mode = mode, language = language)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    _state.update { it.copy(scanErrorMessage = result.message) }
                    AssessmentDefaults.initialTemplate
                }
            }
            scanEngine = AssessmentBodyScanEngine(template = template, language = language)
            pendingScanResult = null
            _state.update {
                it.copy(
                    bodyScanTemplate = template,
                    isResolvingTemplate = false,
                    scanMovementIndex = 0,
                    scanProgressPercent = 0,
                )
            }
        }
    }

    private fun completeBodyScan() {
        val engine = scanEngine ?: run {
            _state.update { it.copy(scanErrorMessage = "Assessment engine is not ready.") }
            return
        }
        if (!engine.isComplete) {
            _state.update { it.copy(scanErrorMessage = "Complete all scan movements before viewing results.") }
            return
        }
        val parqFlags = _state.value.parqAnswers
            .filterValues { it }
            .keys
            .mapNotNull { AssessmentDefaults.parqQuestions.getOrNull(it) }
        val localResult = engine.buildResult(
            parqPassed = parqFlags.isEmpty(),
            parqFlags = parqFlags,
        )
        pendingScanResult = localResult
        workScope.launch {
            _state.update { it.copy(isLoadingResults = true, scanErrorMessage = null) }
            val results = when (val result = repository.submitBodyScan(localResult)) {
                is AppResult.Success -> result.value.copy(resultsSavedToServer = true)
                is AppResult.Failure -> {
                    localResult.uiResults.copy(
                        resultsSavedToServer = false,
                    )
                }
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
