package com.movit.core.training.session

import com.movit.core.training.engine.policy.TimingPolicy
import com.movit.core.training.visibility.VisibilityCheckResult
import com.movit.core.training.visibility.VisibilityMonitor

/**
 * Visibility-driven training interruption. User manual pause stays in [SessionOrchestrator].
 * Ported from legacy [com.movit.training.engine.PauseController].
 */
class PauseController(
    private val visibilityResumeCountdownMs: Long,
    private val nowMs: () -> Long,
) {
    var isCountingSuspended: Boolean = false
        private set

    var isVisibilityPaused: Boolean = false
        private set

    var visibilityResumeCountdown: Int? = null
        private set

    private var visibilityResumeStartMs: Long = 0L

    fun resetExecution() {
        isCountingSuspended = false
        isVisibilityPaused = false
        visibilityResumeStartMs = 0L
        visibilityResumeCountdown = null
    }

    fun onUserOrSupervisorResume(visibilityMonitor: VisibilityMonitor): Boolean {
        if (!isVisibilityPaused && !isCountingSuspended) return false
        isCountingSuspended = false
        isVisibilityPaused = false
        visibilityResumeStartMs = 0L
        visibilityResumeCountdown = null
        visibilityMonitor.onResumeCountdownComplete()
        return true
    }

    fun clearAfterAutoResume(visibilityMonitor: VisibilityMonitor) {
        isCountingSuspended = false
        isVisibilityPaused = false
        visibilityResumeStartMs = 0L
        visibilityResumeCountdown = null
        visibilityMonitor.onResumeCountdownComplete()
    }

    /**
     * @return `true` when the frame should skip rep/phase/validation.
     */
    fun processVisibilityResult(
        result: VisibilityCheckResult,
        emit: (PauseControllerEvent) -> Unit,
        onAutoResumeComplete: () -> Unit,
    ): Boolean = when (result) {
        is VisibilityCheckResult.ShowWarning -> {
            isCountingSuspended = true
            visibilityResumeStartMs = 0L
            visibilityResumeCountdown = null
            emit(
                PauseControllerEvent.VisibilityWarning(
                    invisibleJoints = result.invisibleJoints,
                    remainingBeforePauseMs = result.remainingBeforePause,
                ),
            )
            true
        }

        is VisibilityCheckResult.PauseTraining -> {
            isCountingSuspended = true
            if (!isVisibilityPaused) {
                isVisibilityPaused = true
                emit(
                    PauseControllerEvent.VisibilityPaused(
                        savedRepCount = result.savedRepCount,
                        savedPhase = result.savedPhase,
                        invisibleJoints = result.invisibleJoints,
                    ),
                )
            }
            true
        }

        is VisibilityCheckResult.StartResumeCountdown -> {
            visibilityResumeStartMs = nowMs()
            visibilityResumeCountdown = 3
            true
        }

        is VisibilityCheckResult.ContinueCountdown -> {
            if (visibilityResumeStartMs > 0L) {
                val elapsed = nowMs() - visibilityResumeStartMs
                val remaining =
                    ((visibilityResumeCountdownMs - elapsed) / 1000).toInt().coerceAtLeast(0)
                visibilityResumeCountdown = remaining
                if (elapsed >= visibilityResumeCountdownMs) {
                    onAutoResumeComplete()
                    return false
                }
            }
            true
        }

        is VisibilityCheckResult.ContinueTraining -> {
            val wasSuspended = isCountingSuspended
            val wasPaused = isVisibilityPaused
            if (wasSuspended && !wasPaused) {
                isCountingSuspended = false
                visibilityResumeStartMs = 0L
                visibilityResumeCountdown = null
            }
            false
        }
    }

    companion object {
        fun fromTiming(timing: TimingPolicy, nowMs: () -> Long): PauseController =
            PauseController(timing.visibilityResumeCountdownMs, nowMs)
    }
}
