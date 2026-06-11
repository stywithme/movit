package com.movit.core.training.engine.feedback

import com.movit.core.training.config.PositionCheck
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.policy.FeedbackPolicy
import com.movit.core.training.position.PositionError

/**
 * Rate-limits per-frame feedback candidates (extracted from legacy TrainingEngine).
 */
class FrameFeedbackEmitter(
    private val feedbackPolicy: FeedbackPolicy,
    private val positionChecksById: Map<String, PositionCheck>,
    private val timeProvider: () -> Long,
    private val jointErrorCooldownMs: Long = 1_000L,
    private val maxCandidateIntervalMs: Long = 250L,
) {
    private val lastPositionCandidateTimes = mutableMapOf<String, Long>()
    private val lastJointErrorCandidateTimes = mutableMapOf<String, Long>()

    fun clearPositionCooldowns() {
        lastPositionCandidateTimes.clear()
        lastJointErrorCandidateTimes.clear()
    }

    fun emitThrottledStateMessages(
        stateInfos: Map<String, JointStateInfo>,
        emit: (jointCode: String, state: JointState) -> Unit,
    ) {
        val now = timeProvider()
        for ((jointCode, info) in stateInfos) {
            val state = info.state
            if (state == JointState.TRANSITION || state == JointState.DANGER || state == JointState.WARNING) {
                continue
            }
            if (!feedbackPolicy.shouldEmitStateMessage(jointCode, state, now)) continue
            emit(jointCode, state)
            feedbackPolicy.recordStateMessage(jointCode, state, now)
        }
    }

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

    fun filterPositionErrorsForEmit(errors: List<PositionError>): List<PositionError> =
        errors.filter { shouldEmitPositionEvent(it.checkId) }
}
