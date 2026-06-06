package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.ConfidenceLevel
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.training.report.RepTimelineEntry
import kotlin.math.sqrt

/**
 * ConfidenceCalculator - Determines measurement reliability.
 * 
 * Based on:
 * 1. Capture quality (visibility pauses, camera warnings)
 * 2. Rep score consistency (standard deviation across reps)
 * 3. Number of valid reps (more reps = more confidence)
 * 
 * Output: HIGH / MEDIUM / LOW for each measurement.
 * LOW confidence measurements are marked as "Inconclusive" and
 * excluded from scoring/decision-making.
 */
object ConfidenceCalculator {
    
    private const val HIGH_VISIBILITY_THRESHOLD = 1   // Max visibility pauses for HIGH
    private const val HIGH_SCORE_SD_THRESHOLD = 12f   // Max score SD for HIGH
    private const val MEDIUM_SCORE_SD_THRESHOLD = 20f // Max score SD for MEDIUM
    private const val MIN_REPS_FOR_HIGH = 3
    private const val MIN_REPS_FOR_MEDIUM = 2
    
    /**
     * Calculate confidence for a completed exercise report.
     */
    fun calculate(report: PostTrainingReport): ConfidenceLevel {
        val quality = report.executionQuality
        val timeline = report.repTimeline
        
        if (timeline.isEmpty()) return ConfidenceLevel.LOW
        
        // Factor 1: Capture quality (visibility)
        val visibilityScore = when {
            quality.visibilityPauseCount <= HIGH_VISIBILITY_THRESHOLD 
                && quality.cameraWarningCount <= 1 -> 3
            quality.visibilityPauseCount <= 3 
                && quality.cameraWarningCount <= 3 -> 2
            else -> 1
        }
        
        // Factor 2: Rep score consistency
        val scores = timeline.map { it.score }
        val scoreSD = calculateSD(scores)
        val consistencyScore = when {
            scoreSD <= HIGH_SCORE_SD_THRESHOLD -> 3
            scoreSD <= MEDIUM_SCORE_SD_THRESHOLD -> 2
            else -> 1
        }
        
        // Factor 3: Number of valid (counted) reps
        val validReps = timeline.count { it.isCounted && !it.isInvalidated }
        val repCountScore = when {
            validReps >= MIN_REPS_FOR_HIGH -> 3
            validReps >= MIN_REPS_FOR_MEDIUM -> 2
            else -> 1
        }
        
        // Aggregate (any factor at 1 = LOW, all at 3 = HIGH)
        val total = visibilityScore + consistencyScore + repCountScore
        return when {
            total >= 8 -> ConfidenceLevel.HIGH
            total >= 5 && visibilityScore >= 2 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }
    
    private fun calculateSD(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }
}
