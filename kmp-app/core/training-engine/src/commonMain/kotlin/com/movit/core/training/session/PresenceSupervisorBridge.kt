package com.movit.core.training.session

import com.movit.core.training.engine.Phase
import com.movit.core.training.visibility.VisibilityCheckResult

/**
 * Maps [VisibilityMonitor] / [PauseController] partial-visibility events for the engine layer.
 *
 * **Presence layers (WP-13):**
 * 1. **Full NoPose** — [SessionSupervisor] via [SupervisorSignal.NoPoseFrame] on the camera path
 *    (ViewModel worker); warns at 2s, auto-pauses at 4s.
 * 2. **Partial visibility** — this bridge + [VisibilityMonitor]; joint occlusion during TRAINING.
 */
sealed class PresenceSupervisorEvent {
    data class VisibilityWarning(
        val invisibleJoints: List<String>,
        val remainingBeforePauseMs: Long,
    ) : PresenceSupervisorEvent()

    data class VisibilityPaused(
        val savedRepCount: Int,
        val savedPhase: Phase,
        val invisibleJoints: List<String>,
    ) : PresenceSupervisorEvent()

    data class VisibilityResumed(val repCount: Int) : PresenceSupervisorEvent()
}

fun PresenceSupervisorEvent.toSupervisorSignal(): SupervisorSignal? = when (this) {
    is PresenceSupervisorEvent.VisibilityPaused -> SupervisorSignal.VisibilityPaused
    is PresenceSupervisorEvent.VisibilityResumed -> SupervisorSignal.VisibilityRestored
    else -> null
}

class PresenceSupervisorBridge {
    fun mapVisibilityEvent(event: PauseControllerEvent): PresenceSupervisorEvent = when (event) {
        is PauseControllerEvent.VisibilityWarning -> PresenceSupervisorEvent.VisibilityWarning(
            invisibleJoints = event.invisibleJoints,
            remainingBeforePauseMs = event.remainingBeforePauseMs,
        )
        is PauseControllerEvent.VisibilityPaused -> PresenceSupervisorEvent.VisibilityPaused(
            savedRepCount = event.savedRepCount,
            savedPhase = event.savedPhase,
            invisibleJoints = event.invisibleJoints,
        )
        is PauseControllerEvent.VisibilityResumed -> PresenceSupervisorEvent.VisibilityResumed(
            repCount = event.repCount,
        )
    }

    fun mapVisibilityCheck(result: VisibilityCheckResult): PresenceSupervisorEvent? = when (result) {
        is VisibilityCheckResult.ShowWarning -> PresenceSupervisorEvent.VisibilityWarning(
            invisibleJoints = result.invisibleJoints,
            remainingBeforePauseMs = result.remainingBeforePause,
        )
        is VisibilityCheckResult.PauseTraining -> PresenceSupervisorEvent.VisibilityPaused(
            savedRepCount = result.savedRepCount,
            savedPhase = result.savedPhase,
            invisibleJoints = result.invisibleJoints,
        )
        else -> null
    }
}
