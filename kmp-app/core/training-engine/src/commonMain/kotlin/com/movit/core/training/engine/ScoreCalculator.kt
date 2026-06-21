package com.movit.core.training.engine

object ScoreCalculator {
    const val PRIMARY_JOINT_WEIGHT = 1.0f
    const val SECONDARY_JOINT_WEIGHT = 0.3f
    const val DANGER_PENALTY_PER_JOINT = 15f

    fun getScoreRate(state: JointState): Float = when (state) {
        JointState.PERFECT -> 100f
        JointState.NORMAL -> 80f
        JointState.PAD -> 60f
        JointState.WARNING -> 40f
        JointState.DANGER -> 0f
        JointState.TRANSITION -> 80f
    }

    fun calculateRepScore(
        jointStates: Map<String, JointStateInfo>,
        primaryJoints: Set<String>,
    ): RepScoreResult {
        if (jointStates.isEmpty()) {
            return RepScoreResult(
                score = 0f,
                worstState = JointState.WARNING,
                isCounted = false,
                isInvalidated = false,
                dangerJoints = emptyList(),
                breakdown = emptyMap(),
            )
        }

        var weightedSum = 0f
        var totalWeight = 0f
        var worstState = JointState.PERFECT
        val dangerJoints = mutableListOf<String>()
        val breakdown = mutableMapOf<String, JointScoreContribution>()

        for ((jointCode, stateInfo) in jointStates) {
            val state = stateInfo.state
            if (state == JointState.TRANSITION) continue

            val isPrimary = primaryJoints.contains(jointCode)
            val weight = if (isPrimary) PRIMARY_JOINT_WEIGHT else SECONDARY_JOINT_WEIGHT
            val rate = getScoreRate(state)

            weightedSum += rate * weight
            totalWeight += weight

            if (state.isWorseThan(worstState)) {
                worstState = state
            }
            if (state == JointState.DANGER) {
                dangerJoints.add(jointCode)
            }

            breakdown[jointCode] = JointScoreContribution(
                state = state,
                rate = rate,
                weight = weight,
                contribution = rate * weight,
                isPrimary = isPrimary,
            )
        }

        if (totalWeight <= 0f) {
            return RepScoreResult(
                score = 0f,
                worstState = JointState.WARNING,
                isCounted = false,
                isInvalidated = false,
                dangerJoints = emptyList(),
                breakdown = breakdown,
            )
        }

        val baseScore = weightedSum / totalWeight
        val dangerPenalty = dangerJoints.size * DANGER_PENALTY_PER_JOINT
        val finalScore = (baseScore - dangerPenalty).coerceIn(0f, 100f)
        val isCounted = worstState in listOf(JointState.PERFECT, JointState.NORMAL, JointState.PAD)
        val isInvalidated = worstState == JointState.DANGER

        return RepScoreResult(
            score = finalScore,
            worstState = worstState,
            isCounted = isCounted,
            isInvalidated = isInvalidated,
            dangerJoints = dangerJoints,
            breakdown = breakdown,
        )
    }

    fun calculateScoreFromWorstState(worstState: JointState): Float = getScoreRate(worstState)

    fun calculateHoldScore(stateTimeMs: Map<JointState, Long>): HoldScoreResult {
        val perfectTime = stateTimeMs[JointState.PERFECT] ?: 0L
        val normalTime = stateTimeMs[JointState.NORMAL] ?: 0L
        val padTime = stateTimeMs[JointState.PAD] ?: 0L
        val warningTime = stateTimeMs[JointState.WARNING] ?: 0L
        val dangerTime = stateTimeMs[JointState.DANGER] ?: 0L
        val totalTime = perfectTime + normalTime + padTime + warningTime + dangerTime

        if (dangerTime > 0) {
            return HoldScoreResult(
                score = 0f,
                isInvalidated = true,
                timeInPerfect = perfectTime,
                timeInNormal = normalTime,
                timeInWarning = warningTime,
                timeInDanger = dangerTime,
                totalTime = totalTime,
            )
        }

        if (totalTime == 0L) {
            return HoldScoreResult(
                score = 0f,
                isInvalidated = false,
                timeInPerfect = 0L,
                timeInNormal = 0L,
                timeInWarning = 0L,
                timeInDanger = 0L,
                totalTime = 0L,
            )
        }

        val weightedSum = (
            perfectTime * getScoreRate(JointState.PERFECT) +
                normalTime * getScoreRate(JointState.NORMAL) +
                padTime * getScoreRate(JointState.PAD) +
                warningTime * getScoreRate(JointState.WARNING)
            )

        return HoldScoreResult(
            score = weightedSum / totalTime,
            isInvalidated = false,
            timeInPerfect = perfectTime,
            timeInNormal = normalTime,
            timeInWarning = warningTime,
            timeInDanger = dangerTime,
            totalTime = totalTime,
        )
    }
}

data class RepScoreResult(
    val score: Float,
    val worstState: JointState,
    val isCounted: Boolean,
    val isInvalidated: Boolean,
    val dangerJoints: List<String>,
    val breakdown: Map<String, JointScoreContribution>,
)

data class JointScoreContribution(
    val state: JointState,
    val rate: Float,
    val weight: Float,
    val contribution: Float,
    val isPrimary: Boolean,
)

data class HoldScoreResult(
    val score: Float,
    val isInvalidated: Boolean,
    val timeInPerfect: Long,
    val timeInNormal: Long,
    val timeInWarning: Long,
    val timeInDanger: Long,
    val totalTime: Long,
) {
    fun getTimeInPerfectPercentage(): Float =
        if (totalTime > 0) (timeInPerfect.toFloat() / totalTime) * 100f else 0f
}
