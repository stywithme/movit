package com.movit.feature.training

/**
 * Foreground/background resume semantics (legacy [PhaseResumeAction] subset).
 *
 * Full item-level `phaseCanContinue` / `phaseMaxContinueTimeMs` require planned-workout
 * item metadata not yet mapped into [com.movit.core.training.session.TrainingFlowItem].
 */
enum class PhaseResumeAction {
    NONE,
    RESUMED,
    PHASE_RESTARTED_NO_CONTINUE,
    PHASE_RESTARTED_TIMEOUT,
}

data class PhasePauseSnapshot(
    val pausedAtMs: Long,
    val wasTraining: Boolean,
    val phaseCanContinue: Boolean = true,
    val phaseMaxContinueTimeMs: Long? = null,
)

object TrainingSessionLifecyclePolicy {
    fun onHostPaused(
        wasTraining: Boolean,
        nowMs: Long,
        phaseCanContinue: Boolean = true,
        phaseMaxContinueTimeMs: Long? = null,
    ): PhasePauseSnapshot? {
        if (!wasTraining) return null
        return PhasePauseSnapshot(
            pausedAtMs = nowMs,
            wasTraining = true,
            phaseCanContinue = phaseCanContinue,
            phaseMaxContinueTimeMs = phaseMaxContinueTimeMs,
        )
    }

    fun onHostResumed(snapshot: PhasePauseSnapshot?, nowMs: Long): PhaseResumeAction {
        val paused = snapshot ?: return PhaseResumeAction.NONE
        if (!paused.wasTraining) return PhaseResumeAction.NONE

        if (!paused.phaseCanContinue) {
            return PhaseResumeAction.PHASE_RESTARTED_NO_CONTINUE
        }

        val maxMs = paused.phaseMaxContinueTimeMs
        if (maxMs != null && maxMs > 0L) {
            val elapsed = nowMs - paused.pausedAtMs
            if (elapsed > maxMs) {
                return PhaseResumeAction.PHASE_RESTARTED_TIMEOUT
            }
        }

        return PhaseResumeAction.RESUMED
    }
}
