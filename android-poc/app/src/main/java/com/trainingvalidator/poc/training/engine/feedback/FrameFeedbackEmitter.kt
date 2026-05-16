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
 * Emits [FeedbackEvent] candidates for per-joint state messages and position checks.
 * Final user-facing cooldowns live in FeedbackScheduler; this class only applies
 * a small candidate rate limit to avoid sending the same frame-level issue every frame.
 * Extracted from [com.trainingvalidator.poc.training.TrainingEngine].
 */
class FrameFeedbackEmitter(
    private val feedbackPolicy: FeedbackPolicy,
    private val positionChecksById: Map<String, PositionCheck>,
    private val timeProvider: () -> Long,
    private val jointErrorCooldownMs: Long = 1000L,
    private val maxCandidateIntervalMs: Long = 250L
) {
    private val lastPositionCandidateTimes = mutableMapOf<String, Long>()
    private val lastJointErrorCandidateTimes = mutableMapOf<String, Long>()

    fun clearPositionCooldowns() {
        lastPositionCandidateTimes.clear()
        lastJointErrorCandidateTimes.clear()
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
     * @return true if a new feedback candidate for this [PositionError] / [PositionError.checkId] may be emitted.
     */
    fun shouldEmitPositionEvent(checkId: String): Boolean {
        val now = timeProvider()
        val candidateInterval = (positionChecksById[checkId]?.cooldownMs ?: maxCandidateIntervalMs)
            .coerceAtMost(maxCandidateIntervalMs)
        val lastTime = lastPositionCandidateTimes[checkId] ?: 0L
        if (now - lastTime < candidateInterval) return false
        lastPositionCandidateTimes[checkId] = now
        return true
    }

    fun shouldEmitJointError(error: JointError): Boolean {
        val now = timeProvider()
        val key = "${error.jointCode}:${error.errorType}:${error.state}"
        val candidateInterval = jointErrorCooldownMs.coerceAtMost(maxCandidateIntervalMs)
        val lastTime = lastJointErrorCandidateTimes[key] ?: 0L
        if (now - lastTime < candidateInterval) return false
        lastJointErrorCandidateTimes[key] = now
        return true
    }
}
