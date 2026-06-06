package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.engine.policy.TimingPolicy
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single owner of **visibility-driven** training interruption:
 * - Tier 1: counting suspended (warning) — engine keeps processing frames, skips rep/phase.
 * - Tier 2: full visibility pause + optional 3-2-1 auto-resume countdown.
 *
 * User manual pause ([TrainingEngine.isPaused]) stays in the engine; this class does not
 * own that flag. The engine combines both when deciding whether to short-circuit
 * a frame for rep logic.
 */
class PauseController(
    private val visibilityResumeCountdownMs: Long,
    private val nowMs: () -> Long
) {

    private val _isCountingSuspended = MutableStateFlow(false)
    val isCountingSuspended: StateFlow<Boolean> = _isCountingSuspended.asStateFlow()

    private val _isVisibilityPaused = MutableStateFlow(false)
    val isVisibilityPaused: StateFlow<Boolean> = _isVisibilityPaused.asStateFlow()

    private val _visibilityResumeCountdown = MutableStateFlow<Int?>(null)
    val visibilityResumeCountdown: StateFlow<Int?> = _visibilityResumeCountdown.asStateFlow()

    private var visibilityResumeStartMs: Long = 0L

    fun resetExecution() {
        _isCountingSuspended.value = false
        _isVisibilityPaused.value = false
        visibilityResumeStartMs = 0L
        _visibilityResumeCountdown.value = null
    }

    /**
     * User or WorkoutRunSupervisor resume: clear engine-side visibility-pause flags and sync
     * [VisibilityMonitor] so a stale RESUMING/Paused monitor state does not fight with UI.
     *
     * @return true if visibility-pause state was active and was cleared (caller may sync UI flows).
     */
    fun onUserOrSupervisorResume(visibilityMonitor: VisibilityMonitor): Boolean {
        if (!_isVisibilityPaused.value && !_isCountingSuspended.value) return false
        _isCountingSuspended.value = false
        _isVisibilityPaused.value = false
        visibilityResumeStartMs = 0L
        _visibilityResumeCountdown.value = null
        visibilityMonitor.onResumeCountdownComplete()
        Log.d(TAG, "Cleared visibility pause state after user/supervisor resume")
        return true
    }

    /**
     * After internal 3-2-1: clear pause flags; engine still runs phase/validator reset.
     */
    fun clearAfterAutoResume(visibilityMonitor: VisibilityMonitor) {
        _isCountingSuspended.value = false
        _isVisibilityPaused.value = false
        visibilityResumeStartMs = 0L
        _visibilityResumeCountdown.value = null
        visibilityMonitor.onResumeCountdownComplete()
    }

    /**
     * @return `true` when the frame should **skip** rep/phase/validation (only angles + visibility path).
     */
    fun processVisibilityResult(
        result: VisibilityCheckResult,
        emit: (FeedbackEvent) -> Unit,
        onAutoResumeComplete: () -> Unit
    ): Boolean = when (result) {

        is VisibilityCheckResult.ShowWarning -> {
            _isCountingSuspended.value = true
            visibilityResumeStartMs = 0L
            _visibilityResumeCountdown.value = null
            emit(
                FeedbackEvent.VisibilityWarning(
                    message = result.message,
                    remainingBeforePauseMs = result.remainingBeforePause,
                    invisibleJoints = result.invisibleJoints
                )
            )
            true
        }

        is VisibilityCheckResult.PauseTraining -> {
            _isCountingSuspended.value = true
            if (!_isVisibilityPaused.value) {
                _isVisibilityPaused.value = true
                emit(
                    FeedbackEvent.VisibilityPaused(
                        savedRepCount = result.savedRepCount,
                        savedPhase = result.savedPhase,
                        message = result.message
                    )
                )
            }
            true
        }

        is VisibilityCheckResult.StartResumeCountdown -> {
            visibilityResumeStartMs = nowMs()
            _visibilityResumeCountdown.value = 3
            true
        }

        is VisibilityCheckResult.ContinueCountdown -> {
            if (visibilityResumeStartMs > 0L) {
                val elapsed = nowMs() - visibilityResumeStartMs
                val remaining =
                    ((visibilityResumeCountdownMs - elapsed) / 1000).toInt().coerceAtLeast(0)
                _visibilityResumeCountdown.value = remaining

                if (elapsed >= visibilityResumeCountdownMs) {
                    onAutoResumeComplete()
                    return false
                }
            }
            true
        }

        is VisibilityCheckResult.ContinueTraining -> {
            val wasSuspended = _isCountingSuspended.value
            val wasPaused = _isVisibilityPaused.value

            if (wasSuspended && !wasPaused) {
                _isCountingSuspended.value = false
                visibilityResumeStartMs = 0L
                _visibilityResumeCountdown.value = null
            }
            false
        }
    }

    companion object {
        private const val TAG = "PauseController"
        fun fromTiming(timing: TimingPolicy, nowMs: () -> Long) =
            PauseController(timing.visibilityResumeCountdownMs, nowMs)
    }
}
