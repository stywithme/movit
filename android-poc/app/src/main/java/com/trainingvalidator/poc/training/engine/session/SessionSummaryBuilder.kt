package com.trainingvalidator.poc.training.engine.session

import com.trainingvalidator.poc.training.engine.RepCounter
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.SessionSummary

/**
 * Folds [RepCounter] + duration into a [SessionSummary] at session end; keeps [com.trainingvalidator.poc.training.TrainingEngine.stop] thin.
 */
object SessionSummaryBuilder {
    fun build(
        config: ExerciseConfig,
        repCounter: RepCounter,
        durationMs: Long
    ): SessionSummary = SessionSummary(
        exerciseName = config.name.en,
        totalReps = repCounter.count,
        countedReps = repCounter.countedCount,
        invalidatedReps = repCounter.invalidatedCount,
        averageScore = repCounter.getAverageScore(),
        countedRatio = if (repCounter.count > 0) {
            repCounter.countedCount.toFloat() / repCounter.count
        } else 0f,
        durationMs = durationMs,
        stateBreakdown = repCounter.getStateBreakdown(),
        commonErrors = repCounter.getMostCommonErrors(),
        repDetails = repCounter.repResults
    )
}
