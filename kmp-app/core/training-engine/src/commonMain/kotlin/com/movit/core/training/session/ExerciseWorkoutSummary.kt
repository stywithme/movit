package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.RepCounter
import com.movit.core.training.engine.RepResult

/**
 * Final summary of one exercise run inside a workout.
 *
 * Per-rep material ([stateBreakdown], [commonErrors], [repDetails]) mirrors legacy MO
 * so post-session analysis can be built without re-reading the motion journal.
 */
data class ExerciseWorkoutSummary(
    val exerciseName: String,
    val totalReps: Int,
    val countedReps: Int,
    val invalidatedReps: Int,
    val averageScore: Float,
    val countedRatio: Float,
    val durationMs: Long,
    val stateBreakdown: Map<JointState, Int> = emptyMap(),
    val commonErrors: Map<String, Int> = emptyMap(),
    val repDetails: List<RepResult> = emptyList(),
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val poseVariantIndex: Int = 0,
)

object ExerciseWorkoutSummaryBuilder {
    fun build(
        config: ExerciseConfig,
        repCounter: RepCounter,
        durationMs: Long,
        weightKg: Float? = null,
        weightUnit: String = "kg",
        poseVariantIndex: Int = 0,
    ): ExerciseWorkoutSummary = ExerciseWorkoutSummary(
        exerciseName = config.name.en,
        totalReps = repCounter.count,
        countedReps = repCounter.countedCount,
        invalidatedReps = repCounter.invalidatedCount,
        averageScore = repCounter.getAverageScore(),
        countedRatio = if (repCounter.count > 0) {
            repCounter.countedCount.toFloat() / repCounter.count
        } else {
            0f
        },
        durationMs = durationMs,
        stateBreakdown = repCounter.getStateBreakdown(),
        commonErrors = repCounter.getMostCommonErrors(),
        repDetails = repCounter.repResults,
        weightKg = weightKg?.takeIf { it > 0f },
        weightUnit = weightUnit,
        poseVariantIndex = poseVariantIndex,
    )
}
