package com.movit.core.training.session

import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.policy.TimingPolicy
import com.movit.core.training.visibility.VisibilityCheckResult

/**
 * I-14 / B-1: unified NoPose + Visibility policy in the engine layer.
 * [SessionSupervisor] thresholds (1s / 2s / 4s) align with [TimingPolicy] visibility defaults.
 */
data class PresenceThresholds(
    val graceMs: Long = 1_000L,
    val warnMs: Long = 2_000L,
    val pauseMs: Long = 4_000L,
) {
    companion object {
        fun fromTiming(timing: TimingPolicy): PresenceThresholds = PresenceThresholds(
            graceMs = timing.visibilityGraceDurationMs,
            warnMs = timing.visibilityGraceDurationMs + timing.visibilityWarningDurationMs,
            pauseMs = timing.visibilityPauseAfterMs,
        )

        /** Legacy [SessionSupervisor] NoPose constants — kept for parity. */
        val SUPERVISOR_DEFAULT = PresenceThresholds(
            graceMs = 1_000L,
            warnMs = 2_000L,
            pauseMs = 4_000L,
        )
    }
}

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

    data class NoPoseWarning(val elapsedMs: Long) : PresenceSupervisorEvent()

    data class NoPosePaused(val elapsedMs: Long) : PresenceSupervisorEvent()

    object PoseRestored : PresenceSupervisorEvent()
}

fun PresenceSupervisorEvent.toSupervisorSignal(): SupervisorSignal? = when (this) {
    is PresenceSupervisorEvent.VisibilityPaused -> SupervisorSignal.VisibilityPaused
    is PresenceSupervisorEvent.VisibilityResumed -> SupervisorSignal.VisibilityRestored
    is PresenceSupervisorEvent.NoPosePaused -> SupervisorSignal.NoPoseFrame(timestampMs = elapsedMs)
    else -> null
}

/**
 * Bridges [VisibilityMonitor] results and NoPose gaps into supervisor-ready events.
 */
class PresenceSupervisorBridge(
    private val thresholds: PresenceThresholds = PresenceThresholds.SUPERVISOR_DEFAULT,
    private val nowMs: () -> Long,
) {
    private var noPoseStartMs: Long = UNSET_TIME
    private var noPoseWarnEmitted: Boolean = false
    private var noPosePauseEmitted: Boolean = false

    fun reset() {
        noPoseStartMs = UNSET_TIME
        noPoseWarnEmitted = false
        noPosePauseEmitted = false
    }

    fun onPoseRestored(): PresenceSupervisorEvent? {
        if (noPoseStartMs < 0L) return null
        noPoseStartMs = UNSET_TIME
        noPoseWarnEmitted = false
        noPosePauseEmitted = false
        return PresenceSupervisorEvent.PoseRestored
    }

    fun onNoPoseFrame(timestampMs: Long = nowMs()): PresenceSupervisorEvent? {
        val now = if (timestampMs > 0L) timestampMs else nowMs()
        if (noPoseStartMs < 0L) {
            noPoseStartMs = now
            return null
        }
        val elapsed = now - noPoseStartMs
        return when {
            elapsed >= thresholds.pauseMs && !noPosePauseEmitted -> {
                noPosePauseEmitted = true
                PresenceSupervisorEvent.NoPosePaused(elapsed)
            }
            elapsed >= thresholds.warnMs && !noPoseWarnEmitted -> {
                noPoseWarnEmitted = true
                PresenceSupervisorEvent.NoPoseWarning(elapsed)
            }
            else -> null
        }
    }

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

    private companion object {
        const val UNSET_TIME = -1L
    }
}
