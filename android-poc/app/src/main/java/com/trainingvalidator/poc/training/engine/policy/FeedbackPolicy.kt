package com.trainingvalidator.poc.training.engine.policy

import com.movit.core.training.engine.policy.FeedbackPolicy as KmpFeedbackPolicy
import com.trainingvalidator.poc.training.engine.toKmp
import com.trainingvalidator.poc.training.models.JointState

/**
 * Candidate-rate limiting for training feedback emitted from [com.trainingvalidator.poc.training.TrainingEngine].
 * Delegates to KMP [com.movit.core.training.engine.policy.FeedbackPolicy].
 */
class FeedbackPolicy(
    stateMessageCooldownMs: Long,
    maxCandidateIntervalMs: Long = 250L,
) {
    private val core = KmpFeedbackPolicy(stateMessageCooldownMs, maxCandidateIntervalMs)

    fun resetExecution() = core.resetExecution()

    fun shouldEmitStateMessage(
        jointCode: String,
        state: JointState,
        nowMs: Long,
    ): Boolean = core.shouldEmitStateMessage(jointCode, state.toKmp(), nowMs)

    fun recordStateMessage(jointCode: String, state: JointState, nowMs: Long) {
        core.recordStateMessage(jointCode, state.toKmp(), nowMs)
    }

    companion object {
        fun from(timing: TimingPolicy) = FeedbackPolicy(timing.stateMessageCooldownMs)
    }
}
