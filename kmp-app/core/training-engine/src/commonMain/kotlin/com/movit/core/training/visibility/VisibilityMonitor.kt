package com.movit.core.training.visibility

import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.policy.VisibilityDefaults

/**
 * Tracks visibility of required joints and manages pause/resume timing.
 * Landmark → visibility mapping stays in the Android wrapper.
 *
 * [minVisibility] must be supplied from [com.movit.core.training.engine.policy.TimingPolicy]
 * (typically [VisibilityDefaults.PAUSE_GATE]); there is no constructor default.
 */
class VisibilityMonitor(
    visibilityTrackedJoints: List<VisibilityJointConfig>,
    private val minVisibility: Float,
    private val graceDurationMs: Long = 500,
    private val warningDurationMs: Long = 1500,
    private val pauseAfterMs: Long = 3000,
    private val timeProvider: () -> Long = { com.movit.core.training.engine.currentTimeMillis() },
) {
    private val jointRules = VisibilityJointRules(visibilityTrackedJoints)

    var state: VisibilityState = VisibilityState.VISIBLE
        private set

    private var invisibleStartTime: Long? = null
    private var lastVisibleRepCount: Int = 0
    private var lastPhase: Phase = Phase.IDLE

    private var totalPauseCount: Int = 0
    private var totalWarningCount: Int = 0

    fun evaluateJointVisibility(jointVisibilities: Map<String, Float>): List<JointVisibility> =
        jointRules.evaluate(jointVisibilities, minVisibility)

    fun checkVisibility(
        jointVisibilities: Map<String, Float>,
        currentRepCount: Int,
        currentPhase: Phase,
    ): VisibilityCheckResult = checkVisibility(
        details = evaluateJointVisibility(jointVisibilities),
        currentRepCount = currentRepCount,
        currentPhase = currentPhase,
    )

    /** D-02/I-07: evaluate joints once per frame, then pass details here. */
    fun checkVisibility(
        details: List<JointVisibility>,
        currentRepCount: Int,
        currentPhase: Phase,
    ): VisibilityCheckResult {
        if (details.isEmpty()) return VisibilityCheckResult.ContinueTraining
        val allVisible = details.all { it.isVisible }
        val now = timeProvider()
        return if (allVisible) {
            handleVisible(currentRepCount, currentPhase)
        } else {
            handleInvisible(
                now = now,
                repCount = currentRepCount,
                phase = currentPhase,
                visibilityDetails = details,
            )
        }
    }

    private fun handleVisible(repCount: Int, phase: Phase): VisibilityCheckResult =
        when (state) {
            VisibilityState.WARNING -> {
                state = VisibilityState.VISIBLE
                invisibleStartTime = null
                VisibilityCheckResult.ContinueTraining
            }

            VisibilityState.PAUSED -> {
                state = VisibilityState.RESUMING
                VisibilityCheckResult.StartResumeCountdown(
                    resumeFromRep = lastVisibleRepCount,
                    resumeFromPhase = lastPhase,
                )
            }

            VisibilityState.RESUMING -> VisibilityCheckResult.ContinueCountdown

            VisibilityState.VISIBLE -> {
                lastVisibleRepCount = repCount
                lastPhase = phase
                VisibilityCheckResult.ContinueTraining
            }
        }

    private fun handleInvisible(
        now: Long,
        repCount: Int,
        phase: Phase,
        visibilityDetails: List<JointVisibility>,
    ): VisibilityCheckResult {
        if (invisibleStartTime == null) {
            invisibleStartTime = now
            lastVisibleRepCount = repCount
            lastPhase = phase
        }

        val invisibleDuration = now - invisibleStartTime!!
        val invisibleJoints = visibilityDetails
            .filter { !it.isVisible }
            .map { it.jointName }

        return when {
            invisibleDuration >= pauseAfterMs -> {
                if (state != VisibilityState.PAUSED) {
                    state = VisibilityState.PAUSED
                    totalPauseCount++
                }
                VisibilityCheckResult.PauseTraining(
                    savedRepCount = lastVisibleRepCount,
                    savedPhase = lastPhase,
                    invisibleJoints = invisibleJoints,
                )
            }

            invisibleDuration >= warningDurationMs -> {
                if (state != VisibilityState.WARNING) {
                    state = VisibilityState.WARNING
                    totalWarningCount++
                }
                VisibilityCheckResult.ShowWarning(
                    invisibleJoints = invisibleJoints,
                    remainingBeforePause = pauseAfterMs - invisibleDuration,
                )
            }

            invisibleDuration < graceDurationMs -> VisibilityCheckResult.ContinueTraining

            else -> VisibilityCheckResult.ContinueTraining
        }
    }

    fun onResumeCountdownComplete() {
        state = VisibilityState.VISIBLE
        invisibleStartTime = null
    }

    fun isPausedOrResuming(): Boolean =
        state == VisibilityState.PAUSED || state == VisibilityState.RESUMING

    fun reset() {
        state = VisibilityState.VISIBLE
        invisibleStartTime = null
        lastVisibleRepCount = 0
        lastPhase = Phase.IDLE
    }

    fun getStats(): VisibilityStats = VisibilityStats(
        totalPauseCount = totalPauseCount,
        totalWarningCount = totalWarningCount,
    )

    fun resetStats() {
        totalPauseCount = 0
        totalWarningCount = 0
    }
}
