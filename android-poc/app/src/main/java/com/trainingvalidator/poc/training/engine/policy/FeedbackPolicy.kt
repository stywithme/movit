package com.trainingvalidator.poc.training.engine.policy

import com.trainingvalidator.poc.training.models.JointState

/**
 * Throttling for training feedback emitted from [com.trainingvalidator.poc.training.TrainingEngine].
 * Next step: merge with [com.trainingvalidator.poc.training.feedback.FeedbackManager] throttles
 * and voice [com.trainingvalidator.poc.training.feedback.MessageOrchestrator] behind one policy.
 */
class FeedbackPolicy(
    private val stateMessageCooldownMs: Long
) {
    private val lastStateMessageTimes = mutableMapOf<String, Long>()
    private val lastEmittedStates = mutableMapOf<String, JointState>()

    fun resetSession() {
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
        return (lastState != state) || (nowMs - lastTime >= stateMessageCooldownMs)
    }

    fun recordStateMessage(jointCode: String, state: JointState, nowMs: Long) {
        lastEmittedStates[jointCode] = state
        lastStateMessageTimes[jointCode] = nowMs
    }

    companion object {
        fun from(timing: TimingPolicy) = FeedbackPolicy(timing.stateMessageCooldownMs)
    }
}
