package com.movit.feature.training

/**
 * Foreground/background resume semantics (legacy [PhaseResumeAction] subset).
 *
 * **Product policy (G-05 / OQ-G1): always resume** after background — no phase timeout
 * or forced set restart. Long-background restart would require mapping
 * `phaseMaxContinueTimeMs` from planned-workout item metadata (not wired today).
 */
enum class PhaseResumeAction {
    NONE,
    RESUMED,
}

data class PhasePauseSnapshot(
    val pausedAtMs: Long,
    val wasTraining: Boolean,
)

object TrainingSessionLifecyclePolicy {
    fun onHostPaused(
        wasTraining: Boolean,
        nowMs: Long,
    ): PhasePauseSnapshot? {
        if (!wasTraining) return null
        return PhasePauseSnapshot(
            pausedAtMs = nowMs,
            wasTraining = true,
        )
    }

    fun onHostResumed(snapshot: PhasePauseSnapshot?, @Suppress("UNUSED_PARAMETER") nowMs: Long): PhaseResumeAction {
        val paused = snapshot ?: return PhaseResumeAction.NONE
        if (!paused.wasTraining) return PhaseResumeAction.NONE
        return PhaseResumeAction.RESUMED
    }
}
