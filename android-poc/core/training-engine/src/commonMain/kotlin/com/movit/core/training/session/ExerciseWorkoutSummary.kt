package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.engine.RepCounter

data class ExerciseWorkoutSummary(
    val exerciseName: String,
    val totalReps: Int,
    val countedReps: Int,
    val invalidatedReps: Int,
    val averageScore: Float,
    val countedRatio: Float,
    val durationMs: Long,
)

object ExerciseWorkoutSummaryBuilder {
    fun build(
        config: ExerciseConfig,
        repCounter: RepCounter,
        durationMs: Long,
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
    )
}
