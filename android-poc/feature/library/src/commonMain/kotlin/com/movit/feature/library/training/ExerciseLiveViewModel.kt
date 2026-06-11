package com.movit.feature.library.training

import androidx.lifecycle.ViewModel
import com.movit.core.training.blueprint.ExerciseBlueprintRegistry
import com.movit.core.training.session.LiveExerciseRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ExerciseLiveViewModel(
    private val exerciseSlug: String,
    private val exerciseName: String,
    private val targetReps: Int,
) : ViewModel() {
    private val blueprint = ExerciseBlueprintRegistry.resolve(exerciseSlug)
    val runner: LiveExerciseRunner? = blueprint?.let { LiveExerciseRunner(it, targetReps) }

    private val _state = MutableStateFlow(
        ExerciseLiveUiState(
            exerciseSlug = exerciseSlug,
            exerciseName = exerciseName.ifBlank { blueprint?.displayName.orEmpty() },
            targetReps = targetReps,
            bridgeUnavailable = KmpTrainingSessionBridge.factory == null,
        ),
    )
    val state: StateFlow<ExerciseLiveUiState> = _state.asStateFlow()

    init {
        runner?.onMetrics = { metrics ->
            _state.update {
                it.copy(
                    repCount = metrics.repCount,
                    liveFormPercent = metrics.liveFormScore.toInt().coerceIn(0, 100),
                    averageFormPercent = metrics.averageFormScore.toInt().coerceIn(0, 100),
                    phaseLabel = metrics.phase.toDisplayLabel(),
                    isComplete = metrics.isTargetReached,
                )
            }
        }
        runner?.onTargetReached = {
            _state.update { it.copy(isComplete = true) }
        }
    }

    fun onCameraReady() {
        _state.update { it.copy(isCameraReady = true, errorMessage = null) }
        runner?.start()
    }

    fun onCameraError(message: String) {
        _state.update { it.copy(errorMessage = message, isCameraReady = false) }
    }

    fun stopSession(): Long = runner?.stop() ?: 0L
}
