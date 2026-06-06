package com.trainingvalidator.poc.training.engine.policy

import com.trainingvalidator.poc.training.models.JointState

/**
 * Candidate-rate limiting for training feedback emitted from [com.trainingvalidator.poc.training.TrainingEngine].
 * User-facing cooldowns and delivery decisions live in [com.trainingvalidator.poc.training.feedback.FeedbackScheduler].
 */
class FeedbackPolicy(
    private val stateMessageCooldownMs: Long,
    private val maxCandidateIntervalMs: Long = 250L
) {
    private val lastStateMessageTimes = mutableMapOf<String, Long>()
    private val lastEmittedStates = mutableMapOf<String, JointState>()

    fun resetExecution() {
        lastStateMessageTimes.clear()
        lastEmittedStates.clear()
    }

    /**
     * @return When the joint has a non-empty message, whether to emit [com.trainingvalidator.poc.training.feedback.FeedbackEvent.JointQuality] with [com.trainingvalidator.poc.training.feedback.JointQualityContent.StateMessage] this frame.
     */
    fun shouldEmitStateMessage(
        jointCode: String,
        state: JointState,
        nowMs: Long
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
    }
}
