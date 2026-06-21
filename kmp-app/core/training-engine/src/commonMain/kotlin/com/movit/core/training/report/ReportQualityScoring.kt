package com.movit.core.training.report

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.ScoreCalculator

/**
 * Scoring helpers ported from legacy PerformanceMetricsBuilder / ReportGenerator.
 */
internal object ReportQualityScoring {
    private const val FORM_CARD_WEIGHT_STATE = 0.5f
    private const val FORM_CARD_WEIGHT_ROM = 0.25f
    private const val FORM_CARD_WEIGHT_CONSISTENCY = 0.25f

    fun calculateFormScore(
        summary: MovitPerformanceSummary,
        timeline: List<MovitRepTimelineEntry>,
        isHoldExercise: Boolean,
    ): Float {
        val stateScore = computeStateFormScorePercentFromTimeline(timeline, summary.stateBreakdown)
        return calculateCombinedFormScorePercent(
            stateScore = stateScore,
            romPercent = summary.avgRom,
            consistencyPercent = summary.formConsistency,
            isHoldExercise = isHoldExercise,
        )
    }

    fun calculateSafetyScore(
        errorAnalysis: List<MovitReportErrorAnalysis>,
        invalidatedReps: Int,
        totalReps: Int,
    ): Float {
        if (totalReps == 0) return 100f
        val warningEvents = errorAnalysis.filter { it.state == JointState.WARNING.name }.sumOf { it.count }
        val dangerEvents = invalidatedReps.coerceAtLeast(0)
        val warningPenalty = (warningEvents.toFloat() / totalReps) * 30f
        val dangerPenalty = (dangerEvents.toFloat() / totalReps) * 50f
        return (100f - warningPenalty - dangerPenalty).coerceIn(0f, 100f)
    }

    fun calculateControlScore(
        totalReps: Int,
        consistency: MovitConsistencyMetrics?,
        fatigueIndex: Int?,
        tempoConsistency: Float?,
        velocityLoss: Float?,
        formConsistency: Float?,
    ): Float {
        if (totalReps <= 0) return 50f

        if (tempoConsistency != null || velocityLoss != null || formConsistency != null) {
            val tcScore = tempoConsistency ?: 80f
            val vlScore = if (velocityLoss != null) (100f - velocityLoss) else 85f
            val fcScore = formConsistency ?: 80f
            val fatiguePenalty = if (fatigueIndex != null) {
                val fatigueRatio = fatigueIndex.toFloat() / totalReps
                (fatigueRatio * 100f).coerceIn(30f, 100f)
            } else {
                100f
            }
            return (
                tcScore * 0.30f +
                    vlScore * 0.25f +
                    fcScore * 0.25f +
                    fatiguePenalty * 0.20f
                ).coerceIn(0f, 100f)
        }

        var score = 80f
        if (fatigueIndex == null) {
            score += 10f
        } else {
            val fatigueRatio = fatigueIndex.toFloat() / totalReps
            score -= (fatigueRatio * 30f)
        }
        consistency?.let { metrics ->
            val variationRatio = if (metrics.averageDurationMs > 0) {
                metrics.variationMs.toFloat() / metrics.averageDurationMs
            } else {
                0f
            }
            score -= (variationRatio * 20f).coerceAtMost(20f)
        }
        return score.coerceIn(0f, 100f)
    }

    private fun computeStateFormScorePercentFromTimeline(
        timeline: List<MovitRepTimelineEntry>,
        breakdown: MovitStateBreakdown,
    ): Float {
        val timelineScores = timeline.map { it.score }
        if (timelineScores.isNotEmpty()) return timelineScores.average().toFloat()
        val total = breakdown.perfectCount + breakdown.normalCount + breakdown.padCount +
            breakdown.warningCount + breakdown.dangerCount
        if (total == 0) return 0f
        return (
            breakdown.perfectCount * ScoreCalculator.getScoreRate(JointState.PERFECT) +
                breakdown.normalCount * ScoreCalculator.getScoreRate(JointState.NORMAL) +
                breakdown.padCount * ScoreCalculator.getScoreRate(JointState.PAD) +
                breakdown.warningCount * ScoreCalculator.getScoreRate(JointState.WARNING) +
                breakdown.dangerCount * ScoreCalculator.getScoreRate(JointState.DANGER)
            ) / total.toFloat()
    }

    private fun calculateCombinedFormScorePercent(
        stateScore: Float,
        romPercent: Float?,
        consistencyPercent: Float?,
        isHoldExercise: Boolean,
    ): Float {
        if (isHoldExercise) return stateScore.coerceIn(0f, 100f)
        var wState = FORM_CARD_WEIGHT_STATE
        var wRom = FORM_CARD_WEIGHT_ROM
        var wCons = FORM_CARD_WEIGHT_CONSISTENCY
        if (romPercent == null) wRom = 0f
        if (consistencyPercent == null) wCons = 0f
        val sumW = wState + wRom + wCons
        if (sumW <= 0f) return stateScore.coerceIn(0f, 100f)
        return (
            stateScore * (wState / sumW) +
                (romPercent ?: 0f) * (wRom / sumW) +
                (consistencyPercent ?: 0f) * (wCons / sumW)
            ).coerceIn(0f, 100f)
    }
}
