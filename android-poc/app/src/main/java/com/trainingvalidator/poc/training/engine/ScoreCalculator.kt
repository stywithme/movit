package com.trainingvalidator.poc.training.engine

import com.movit.core.training.engine.ScoreCalculator as KmpScoreCalculator
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.JointStateInfo

/**
 * ScoreCalculator - Centralized scoring logic for all exercise types.
 * Delegates to KMP [com.movit.core.training.engine.ScoreCalculator].
 */
object ScoreCalculator {
    const val PRIMARY_JOINT_WEIGHT = KmpScoreCalculator.PRIMARY_JOINT_WEIGHT
    const val SECONDARY_JOINT_WEIGHT = KmpScoreCalculator.SECONDARY_JOINT_WEIGHT
    const val DANGER_PENALTY_PER_JOINT = KmpScoreCalculator.DANGER_PENALTY_PER_JOINT

    fun getScoreRate(state: JointState): Float =
        KmpScoreCalculator.getScoreRate(state.toKmp())

    fun calculateRepScore(
        jointStates: Map<String, JointStateInfo>,
        primaryJoints: Set<String>,
    ): RepScoreResult = KmpScoreCalculator.calculateRepScore(
        jointStates.mapValues { it.value.toKmpScoringInfo() },
        primaryJoints,
    ).toApp()

    fun calculateScoreFromWorstState(worstState: JointState): Float =
        KmpScoreCalculator.calculateScoreFromWorstState(worstState.toKmp())

    fun calculateHoldScore(stateTimeMs: Map<JointState, Long>): HoldScoreResult =
        KmpScoreCalculator.calculateHoldScore(
            stateTimeMs.mapKeys { it.key.toKmp() },
        ).toApp()
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
    fun getTimeInPerfectPercentage(): Float {
        return if (totalTime > 0) (timeInPerfect.toFloat() / totalTime) * 100f else 0f
    }
}
