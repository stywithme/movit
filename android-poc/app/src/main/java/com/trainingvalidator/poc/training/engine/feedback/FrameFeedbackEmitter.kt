package com.trainingvalidator.poc.training.engine.feedback

import com.trainingvalidator.poc.training.engine.policy.FeedbackPolicy
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.JointQualityContent
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.PositionCheck

/**
 * Throttled [FeedbackEvent]s for per-joint state messages and per-position-check cooldowns.
 * Extracted from [com.trainingvalidator.poc.training.TrainingEngine].
 */
class FrameFeedbackEmitter(
    private val feedbackPolicy: FeedbackPolicy,
    private val positionChecksById: Map<String, PositionCheck>,
    private val timeProvider: () -> Long,
    private val jointErrorCooldownMs: Long = 1000L
) {
    private val lastPositionEventTimes = mutableMapOf<String, Long>()
    private val lastJointErrorTimes = mutableMapOf<String, Long>()

    fun clearPositionCooldowns() {
        lastPositionEventTimes.clear()
        lastJointErrorTimes.clear()
    }

    /**
     * Throttled MEDIUM messages for non-error joint states.
     */
    fun emitThrottledStateMessages(
        stateInfos: Map<String, JointStateInfo>,
        emit: (FeedbackEvent) -> Unit
    ) {
        val now = timeProvider()
        for ((jointCode, info) in stateInfos) {
            val state = info.state
            if (state == JointState.TRANSITION || state == JointState.DANGER || state == JointState.WARNING) continue
            val message = info.messages.firstOrNull() ?: continue
            if (!feedbackPolicy.shouldEmitStateMessage(jointCode, state, now)) continue
            emit(
                FeedbackEvent.JointQuality(
                    content = JointQualityContent.StateMessage(
                        jointCode = jointCode,
                        state = state,
                        zone = info.currentZone,
                        message = message
                    )
                )
            )
            feedbackPolicy.recordStateMessage(jointCode, state, now)
        }
    }

    /**
     * @return true if a new feedback event for this [PositionError] / [PositionError.checkId] may be emitted.
     */
    fun shouldEmitPositionEvent(checkId: String): Boolean {
        val now = timeProvider()
        val cooldown = positionChecksById[checkId]?.cooldownMs ?: 1500L
        val lastTime = lastPositionEventTimes[checkId] ?: 0L
        if (now - lastTime < cooldown) return false
        lastPositionEventTimes[checkId] = now
        return true
    }

    fun shouldEmitJointError(error: JointError): Boolean {
        val now = timeProvider()
        val key = "${error.jointCode}:${error.errorType}:${error.state}"
        val lastTime = lastJointErrorTimes[key] ?: 0L
        if (now - lastTime < jointErrorCooldownMs) return false
        lastJointErrorTimes[key] = now
        return true
    }
}
