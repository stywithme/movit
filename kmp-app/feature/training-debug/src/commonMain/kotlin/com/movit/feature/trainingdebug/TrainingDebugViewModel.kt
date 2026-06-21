package com.movit.feature.trainingdebug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import com.movit.core.training.boundary.DeviceTiltPort
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrainingDebugViewModel(
    private val exerciseSlug: String? = null,
    private val deviceTiltPort: DeviceTiltPort? = null,
) : ViewModel() {
    private companion object {
        const val TILT_OWNER = "training_debug_lab"
    }

    private val analyzer = TrainingDebugAnalyzer()
    private val _uiState = MutableStateFlow(TrainingDebugUiState())
    val uiState: StateFlow<TrainingDebugUiState> = _uiState.asStateFlow()
    /** Alias for common screen collectors. */
    val state: StateFlow<TrainingDebugUiState> = uiState
    private val _effects = MutableSharedFlow<TrainingDebugEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var sourceFrames = 0
    private var sourceWindowStart = 0L
    private var analysisFrames = 0
    private var analysisWindowStart = 0L

    init {
        syncTiltAcquisition(_uiState.value.config.tiltCorrectionEnabled)
    }

    fun dispatch(action: TrainingDebugAction) {
        if (TrainingDebugReducers.shouldResetAnalysis(action)) {
            resetAnalysisState("settings change")
        }
        _uiState.update { it.copy(config = TrainingDebugReducers.reduceConfig(it.config, action)) }
        if (action is TrainingDebugAction.SetTiltCorrection) {
            syncTiltAcquisition(action.enabled)
        }
        if (action is TrainingDebugAction.SetInputMode && !isInputModeSupportedOnPlatform(action.mode)) {
            _uiState.update { it.copy(config = it.config.copy(inputMode = TrainingDebugInputMode.CAMERA)) }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
    }

    fun setVideoState(playing: Boolean, currentMs: Long, durationMs: Long, loaded: Boolean) {
        _uiState.update {
            it.copy(
                videoPlaying = playing,
                videoCurrentMs = currentMs,
                videoDurationMs = durationMs,
                hasMediaLoaded = loaded,
            )
        }
    }

    fun setModelLabel(label: String) {
        _uiState.update { it.copy(modelLabel = label) }
    }

    fun onSourceFrame() {
        sourceFrames += 1
        val now = trainingDebugWallClockMs()
        if (sourceWindowStart == 0L) sourceWindowStart = now
        if (now - sourceWindowStart >= 1_000L) {
            val fps = sourceFrames
            sourceFrames = 0
            sourceWindowStart = now
            _uiState.update { it.copy(fps = it.fps.copy(sourceFps = fps)) }
        }
    }

    fun onSkippedBusyFrames(count: Int) {
        _uiState.update { it.copy(fps = it.fps.copy(skippedBusyFrames = count)) }
    }

    fun onFrame(frame: TrainingDebugFrameInput?) {
        if (frame == null) {
            clearPoseAnalysis()
            return
        }
        val config = _uiState.value.config
        val analysis = analyzer.analyze(
            frame = frame,
            config = config,
            tiltSource = deviceTiltPort,
            exerciseSlug = exerciseSlug,
        )
        analysisFrames += 1
        val now = frame.timestampMs
        if (analysisWindowStart == 0L) analysisWindowStart = now
        val analysisFps = if (now - analysisWindowStart >= 1_000L) {
            val fps = analysisFrames
            analysisFrames = 0
            analysisWindowStart = now
            fps
        } else {
            _uiState.value.fps.analysisFps
        }
        _uiState.update {
            it.copy(
                analysis = analysis,
                isFrontCamera = frame.isFrontCamera,
                errorMessage = null,
                fps = it.fps.copy(
                    inferenceFps = if (frame.inferenceTimeMs > 0) {
                        (1000 / frame.inferenceTimeMs.coerceAtLeast(1)).toInt()
                    } else {
                        it.fps.inferenceFps
                    },
                    analysisFps = analysisFps,
                ),
            )
        }
    }

    fun resetAnalysisState(reason: String) {
        analyzer.resetAnalysisState(reason)
        sourceFrames = 0
        sourceWindowStart = 0L
        analysisFrames = 0
        analysisWindowStart = 0L
    }

    private fun clearPoseAnalysis() {
        _uiState.update {
            it.copy(
                analysis = TrainingDebugAnalysisResult(
                    hasPose = false,
                    liveValueText = "—",
                    statusText = "No pose",
                    infoPanelText = "No pose detected",
                ),
            )
        }
    }

    fun exportText(): String = TrainingDebugExportFormatter.formatText(
        config = _uiState.value.config,
        analysis = _uiState.value.analysis,
        fps = _uiState.value.fps,
        modelLabel = _uiState.value.modelLabel,
    )

    fun onEvent(event: TrainingDebugEvent) {
        when (event) {
            TrainingDebugEvent.BackClicked -> viewModelScope.launch {
                _effects.emit(TrainingDebugEffect.NavigateBack)
            }
            TrainingDebugEvent.FlipCameraClicked -> {
                resetAnalysisState("camera switch")
                _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
            }
            TrainingDebugEvent.CopyDebugInfoClicked -> viewModelScope.launch {
                _effects.emit(TrainingDebugEffect.CopyToClipboard(exportText()))
            }
            TrainingDebugEvent.ToggleInfoPanel -> dispatch(
                TrainingDebugAction.SetInfoPanelVisible(!_uiState.value.config.infoPanelVisible),
            )
            is TrainingDebugEvent.TabSelected -> dispatch(TrainingDebugAction.SetTab(event.tab))
            is TrainingDebugEvent.InputModeSelected -> dispatch(TrainingDebugAction.SetInputMode(event.mode))
            is TrainingDebugEvent.JointToggled -> dispatch(TrainingDebugAction.ToggleJoint(event.jointCode))
            is TrainingDebugEvent.FrameReceived -> onFrame(event.frame)
            is TrainingDebugEvent.SourceFpsUpdated -> {
                _uiState.update { it.copy(fps = it.fps.copy(sourceFps = event.fps)) }
            }
            is TrainingDebugEvent.ErrorReceived -> {
                _uiState.update { it.copy(errorMessage = event.message) }
            }
        }
    }

    override fun onCleared() {
        (deviceTiltPort as? AcquirableDeviceTiltPort)?.release(TILT_OWNER)
        super.onCleared()
    }

    private fun syncTiltAcquisition(enabled: Boolean) {
        val port = deviceTiltPort as? AcquirableDeviceTiltPort ?: return
        if (enabled) {
            port.acquire(TILT_OWNER)
        } else {
            port.release(TILT_OWNER)
        }
    }
}

sealed interface TrainingDebugEffect {
    data object NavigateBack : TrainingDebugEffect
    data class CopyToClipboard(val text: String) : TrainingDebugEffect
}

sealed interface TrainingDebugEvent {
    data object BackClicked : TrainingDebugEvent
    data object FlipCameraClicked : TrainingDebugEvent
    data object CopyDebugInfoClicked : TrainingDebugEvent
    data object ToggleInfoPanel : TrainingDebugEvent
    data class TabSelected(val tab: TrainingDebugTab) : TrainingDebugEvent
    data class InputModeSelected(val mode: TrainingDebugInputMode) : TrainingDebugEvent
    data class JointToggled(val jointCode: String) : TrainingDebugEvent
    data class FrameReceived(val frame: TrainingDebugFrameInput?) : TrainingDebugEvent
    data class SourceFpsUpdated(val fps: Int) : TrainingDebugEvent
    data class ErrorReceived(val message: String) : TrainingDebugEvent
}
