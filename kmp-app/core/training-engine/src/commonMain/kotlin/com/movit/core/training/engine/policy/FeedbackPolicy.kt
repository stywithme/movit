package com.movit.core.training.engine.policy

import com.movit.core.training.engine.JointState
import com.movit.core.training.feedback.CoachIntensity

/**
 * Candidate-rate limiting for training feedback emitted from the engine.
 * User-facing cooldowns and delivery decisions live in [com.movit.core.training.feedback.FeedbackScheduler].
 */
class FeedbackPolicy(
    private val stateMessageCooldownMs: Long,
    private val maxCandidateIntervalMs: Long = 250L,
) {
    private val lastStateMessageTimes = mutableMapOf<String, Long>()
    private val lastEmittedStates = mutableMapOf<String, JointState>()

    fun resetExecution() {
        lastStateMessageTimes.clear()
        lastEmittedStates.clear()
    }

    fun shouldEmitStateMessage(
        jointCode: String,
        state: JointState,
        nowMs: Long,
    ): Boolean {
        val lastState = lastEmittedStates[jointCode]
        val lastTime = lastStateMessageTimes[jointCode] ?: 0L
        val candidateInterval = stateMessageCooldownMs.coerceAtMost(maxCandidateIntervalMs)
        return (lastState != state) || (nowMs - lastTime >= candidateInterval)
    }

    fun recordStateMessage(jointCode: String, state: JointState, nowMs: Long) {
        lastEmittedStates[jointCode] = state
        lastStateMessageTimes[jointCode] = nowMs
    }

    companion object {
        fun from(timing: TimingPolicy) = FeedbackPolicy(timing.stateMessageCooldownMs)

        fun fromCoachIntensity(intensity: CoachIntensity, base: TimingPolicy = TimingPolicy.DEFAULT): FeedbackPolicy =
            from(TimingPolicy.withCoachIntensity(intensity, base))
    }
}
