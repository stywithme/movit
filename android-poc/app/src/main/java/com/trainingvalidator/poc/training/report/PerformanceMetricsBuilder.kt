package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.engine.ScoreCalculator
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.MetricCode

/**
 * PerformanceMetricsBuilder - Builds EnhancedPerformanceMetrics from report data
 * 
 * Aggregates metrics into the 3 main cards:
 * - Form (الشكل)
 * - Safety (الأمان)
 * - Control (التحكم)
 * 
 * SINGLE SOURCE OF TRUTH PRINCIPLE:
 * - Uses pre-calculated metrics from PerformanceSummary (formConsistency, fatigueIndex, avgROM)
 * - These values are calculated ONCE in ReportGenerator using MetricsCalculator
 * - This class only FORMATS metrics for display, does NOT recalculate them
 * 
 * Uses ScoreCalculator for score rate constants.
 */
object PerformanceMetricsBuilder {
    
    /**
     * Build enhanced performance metrics from a report
     * Only includes metrics that should be shown based on exercise config
     */
    fun build(report: PostTrainingReport): EnhancedPerformanceMetrics {
        val config = report.exerciseConfig
        
        return EnhancedPerformanceMetrics(
            formCard = buildFormMetrics(report, config),
            safetyCard = buildSafetyMetrics(report, config),
            controlCard = buildControlMetrics(report, config),
            loadMetrics = buildLoadMetrics(report, config)
        )
    }
    
    /**
     * Build Form metrics card
     * Combines: FormScore, ROM, Symmetry, FormConsistency
     * 
     * Uses pre-calculated values from PerformanceSummary where available.
     */
    private fun buildFormMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): FormMetrics {
        val summary = report.summary
        val stateBreakdown = summary.stateBreakdown
        
        // Calculate form score from state distribution (always shown)
        val formScore = calculateFormScore(report, stateBreakdown)
        
        // ROM - use pre-calculated value from summary
        val romMetric = if (shouldShow(config, MetricCode.ROM)) {
            formatROMMetric(summary.avgROM)
        } else null
        
        // Symmetry - calculate only if enabled (will be null for non-bilateral)
        val symmetryMetric = if (shouldShow(config, MetricCode.SYMMETRY)) {
            calculateSymmetryMetric(report)
        } else null
        
        // Form Consistency - use pre-calculated value from summary
        val consistencyMetric = if (shouldShow(config, MetricCode.FORM_CONSISTENCY)) {
            formatFormConsistencyMetric(summary.formConsistency)
        } else null
        
        return FormMetrics(
            overallScore = formScore,
            rom = romMetric,
            symmetry = symmetryMetric,
            formConsistency = consistencyMetric
        )
    }
    
    /**
     * Build Safety metrics card
     * Combines: AlignmentAccuracy, TempoConsistency (stability field), DangerCount
     * 
     * NOTE: The "stability" field actually measures TEMPO CONSISTENCY (timing variation),
     * not hip/core stability. True hip stability requires frame data not available here.
     */
    private fun buildSafetyMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): SafetyMetrics {
        val dangerCount = report.dangerAlerts.size
        
        // Calculate overall safety score
        val totalReps = report.summary.totalReps
        val safeReps = totalReps - dangerCount
        val safetyPercentage = if (totalReps > 0) (safeReps.toFloat() / totalReps) * 100 else 100f
        
        val safetyScore = MetricWithStatus.fromPercentage(
            safetyPercentage,
            advice = if (dangerCount > 0) {
                LocalizedText(
                    ar = "راجع تنبيهات الأمان",
                    en = "Review safety alerts"
                )
            } else null
        )
        
        // Alignment accuracy - calculate only if enabled
        val alignmentMetric = if (shouldShow(config, MetricCode.ALIGNMENT)) {
            calculateAlignmentMetric(report)
        } else null
        
        // Tempo Consistency (stored in stability field for backward compatibility)
        // Measures timing variation between reps, NOT hip stability
        val tempoConsistencyMetric = if (shouldShow(config, MetricCode.STABILITY)) {
            calculateTempoConsistencyMetric(report)
        } else null
        
        return SafetyMetrics(
            overallScore = safetyScore,
            alignmentAccuracy = alignmentMetric,
            stability = tempoConsistencyMetric,
            dangerCount = dangerCount
        )
    }
    
    /**
     * Build Control metrics card
     * Combines: Tempo, TUT, FatigueIndex
     * 
     * Uses pre-calculated fatigueIndex from PerformanceSummary.
     */
    private fun buildControlMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): ControlMetrics {
        val isHold = config?.isHoldExercise() == true
        
        // Calculate tempo - for rep-based exercises
        val tempoDisplay = if (!isHold && shouldShow(config, MetricCode.TEMPO)) {
            calculateTempoDisplay(report)
        } else null
        
        // Total Time Under Tension - for rep-based exercises
        val totalTUT = if (!isHold && shouldShow(config, MetricCode.TUT)) {
            (report.summary.durationMs / 1000).toInt()
        } else null
        
        // Fatigue index - use pre-calculated value from summary (Single Source of Truth)
        val fatigueIndex = if (shouldShow(config, MetricCode.FATIGUE_INDEX)) {
            report.summary.fatigueIndex
        } else null
        
        // Control score based on consistency and fatigue
        val controlScore = calculateControlScore(report, fatigueIndex)
        
        return ControlMetrics(
            overallScore = controlScore,
            tempo = tempoDisplay,
            totalTUT = totalTUT,
            fatigueIndex = fatigueIndex
        )
    }
    
    /**
     * Build Load metrics (for weighted exercises)
     * Always calculates if weight data is present
     */
    private fun buildLoadMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): LoadMetrics? {
        val weight = report.summary.weightKg ?: return null
        if (weight <= 0) return null
        
        return LoadMetrics(
            weightKg = weight,
            weightUnit = report.summary.weightUnit,
            totalVolume = report.summary.totalVolume,
            est1RM = report.summary.est1RM
        )
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Helper calculation methods
    // ═══════════════════════════════════════════════════════════════
    
    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        if (config == null) return true
        return config.shouldShowMetric(metric)
    }
    
    private fun calculateFormScore(report: PostTrainingReport, breakdown: StateBreakdown): MetricWithStatus {
        // Prefer timeline scores when available to match overall quality logic.
        val timelineScores = report.repTimeline.map { it.score }
        val scoreValue = if (timelineScores.isNotEmpty()) {
            timelineScores.average().toFloat()
        } else {
            // Fallback: Use state breakdown with ScoreCalculator rates
            // PERFECT = 100, NORMAL = 80, PAD = 60, WARNING = 40, DANGER = 0
            val total = breakdown.total.toFloat()
            if (total == 0f) {
                0f
            } else {
                (
                    breakdown.perfectCount * ScoreCalculator.getScoreRate(JointState.PERFECT) +
                    breakdown.normalCount * ScoreCalculator.getScoreRate(JointState.NORMAL) +
                    breakdown.padCount * ScoreCalculator.getScoreRate(JointState.PAD) +
                    breakdown.warningCount * ScoreCalculator.getScoreRate(JointState.WARNING) +
                    breakdown.dangerCount * ScoreCalculator.getScoreRate(JointState.DANGER)
                ) / total
            }
        }
        
        return MetricWithStatus.fromPercentage(
            scoreValue,
            advice = when {
                scoreValue >= 90 -> LocalizedText(ar = "شكل ممتاز!", en = "Excellent form!")
                scoreValue >= 70 -> LocalizedText(ar = "شكل جيد", en = "Good form")
                else -> LocalizedText(ar = "ركز على الشكل", en = "Focus on form")
            }
        )
    }
    
    /**
     * Format ROM metric from pre-calculated value.
     *
     * NOTE: avgROM uses real MotionRecorder metrics when available,
     * otherwise falls back to a score-based proxy.
     */
    private fun formatROMMetric(avgROM: Float?): MetricWithStatus? {
        if (avgROM == null) return null
        
        return MetricWithStatus.fromPercentage(
            avgROM,
            advice = if (avgROM >= 80) {
                LocalizedText(ar = "مدى حركي جيد", en = "Good range of motion")
            } else {
                LocalizedText(ar = "حاول النزول أعمق", en = "Try going deeper")
            }
        )
    }
    
    private fun calculateSymmetryMetric(report: PostTrainingReport): MetricWithStatus? {
        // Check if exercise is bilateral
        if (report.exerciseConfig?.isBilateral != true) {
            return null  // Symmetry not applicable for unilateral exercises
        }
        
        // Check for symmetry-related errors (left/right differences)
        val symmetryErrors = report.errorAnalysis.filter { 
            it.jointCode.contains("left") || it.jointCode.contains("right")
        }
        
        // Use timeline size as total (includes all reps, not just counted)
        val totalReps = report.repTimeline.size.coerceAtLeast(report.summary.totalReps)
        
        // No symmetry data available when no reps completed
        if (totalReps == 0) {
            return null
        }
        
        if (symmetryErrors.isEmpty()) {
            return MetricWithStatus.fromPercentage(
                100f,
                advice = LocalizedText(ar = "متوازن تماماً", en = "Perfectly balanced")
            )
        }
        
        // Calculate symmetry: penalize based on unique affected reps, not total error count
        val affectedReps = symmetryErrors.flatMap { it.affectedReps }.toSet().size
        val symmetryScore = ((totalReps - affectedReps).toFloat() / totalReps) * 100
        
        return MetricWithStatus.fromPercentage(
            symmetryScore.coerceIn(0f, 100f),
            advice = if (symmetryScore >= 80) {
                LocalizedText(ar = "توازن جيد", en = "Good balance")
            } else {
                LocalizedText(ar = "عدم توازن طفيف", en = "Slight imbalance detected")
            }
        )
    }
    
    /**
     * Format form consistency from pre-calculated value
     * 
     * Uses value calculated in ReportGenerator (Single Source of Truth).
     */
    private fun formatFormConsistencyMetric(formConsistency: Float?): MetricWithStatus? {
        if (formConsistency == null) return null
        
        return MetricWithStatus.fromPercentage(
            formConsistency,
            advice = if (formConsistency >= 80) {
                LocalizedText(ar = "ثبات ممتاز", en = "Excellent consistency")
            } else {
                LocalizedText(ar = "حاول الحفاظ على نفس الشكل", en = "Try to maintain the same form")
            }
        )
    }
    
    private fun calculateAlignmentMetric(report: PostTrainingReport): MetricWithStatus? {
        // Use timeline size as total (includes all reps)
        val totalReps = report.repTimeline.size.coerceAtLeast(report.summary.totalReps)
        
        // No alignment data available when no reps completed
        if (totalReps == 0) {
            return null
        }
        
        // Calculate alignment from state breakdown
        // Count reps without WARNING or DANGER states
        val breakdown = report.summary.stateBreakdown
        val goodReps = breakdown.perfectCount + breakdown.normalCount + breakdown.padCount
        val totalFromBreakdown = breakdown.total
        
        val alignmentScore = if (totalFromBreakdown > 0) {
            (goodReps.toFloat() / totalFromBreakdown) * 100
        } else {
            // Fallback: calculate from timeline
            val goodRepsFromTimeline = report.repTimeline.count { 
                it.worstState in listOf(JointState.PERFECT, JointState.NORMAL, JointState.PAD)
            }
            if (report.repTimeline.isNotEmpty()) {
                (goodRepsFromTimeline.toFloat() / report.repTimeline.size) * 100
            } else 100f
        }
        
        return MetricWithStatus.fromPercentage(
            alignmentScore.coerceIn(0f, 100f),
            advice = if (alignmentScore >= 90) {
                LocalizedText(ar = "محاذاة ممتازة", en = "Excellent alignment")
            } else if (alignmentScore >= 70) {
                LocalizedText(ar = "محاذاة جيدة", en = "Good alignment")
            } else {
                LocalizedText(ar = "تحقق من وضعية المفاصل", en = "Check joint positions")
            }
        )
    }
    
    /**
     * Calculate Tempo Consistency metric (stored in stability field)
     * 
     * Measures timing consistency between reps (NOT hip/core stability).
     * Lower timing variation = higher consistency score.
     * 
     * NOTE: True hip stability requires frame data from MetricsCalculator.calculateStability()
     * which needs hip joint angle variance. This metric is a tempo-based proxy.
     */
    private fun calculateTempoConsistencyMetric(report: PostTrainingReport): MetricWithStatus? {
        val consistency = report.consistency ?: return null
        
        // Less timing variation = more consistent tempo
        val variationMs = consistency.variationMs
        val tempoConsistencyScore = when {
            variationMs < 500 -> 100f
            variationMs < 1000 -> 90f
            variationMs < 1500 -> 80f
            variationMs < 2000 -> 70f
            else -> 60f
        }
        
        return MetricWithStatus.fromPercentage(
            tempoConsistencyScore,
            advice = if (tempoConsistencyScore >= 80) {
                LocalizedText(ar = "إيقاع ثابت", en = "Consistent tempo")
            } else {
                LocalizedText(ar = "تفاوت في السرعة", en = "Speed variation detected")
            }
        )
    }
    
    private fun calculateTempoDisplay(report: PostTrainingReport): TempoDisplay? {
        val consistency = report.consistency ?: return null
        
        // Estimate tempo from average duration
        // Assume: 40% eccentric, 20% isometric, 40% concentric
        val avgMs = consistency.averageDurationMs
        
        return TempoDisplay(
            eccentricMs = (avgMs * 0.4).toInt(),
            isometricMs = (avgMs * 0.2).toInt(),
            concentricMs = (avgMs * 0.4).toInt()
        )
    }
    
    private fun calculateControlScore(report: PostTrainingReport, fatigueIndex: Int?): MetricWithStatus {
        val scoreValue = calculateControlScoreValue(
            totalReps = report.summary.totalReps,
            consistency = report.consistency,
            fatigueIndex = fatigueIndex
        )
        
        return MetricWithStatus.fromPercentage(
            scoreValue,
            advice = when {
                fatigueIndex != null -> LocalizedText(
                    ar = "بدأ التعب من العدة #$fatigueIndex",
                    en = "Fatigue started at rep #$fatigueIndex"
                )
                scoreValue >= 90 -> LocalizedText(ar = "تحكم ممتاز", en = "Excellent control")
                else -> LocalizedText(ar = "حافظ على الإيقاع", en = "Maintain the tempo")
            }
        )
    }
    
    /**
     * Shared Control score calculation for report and overall quality.
     */
    internal fun calculateControlScoreValue(
        totalReps: Int,
        consistency: ConsistencyMetrics?,
        fatigueIndex: Int?
    ): Float {
        if (totalReps <= 0) return 50f  // Neutral when no reps are available
        
        var score = 80f
        
        // Bonus for no fatigue
        if (fatigueIndex == null) {
            score += 10f
        } else {
            // Penalize based on how early fatigue started
            val fatigueRatio = fatigueIndex.toFloat() / totalReps
            if (fatigueRatio < 0.5) {
                score -= 20f
            } else if (fatigueRatio < 0.7) {
                score -= 10f
            }
        }
        
        // Bonus for consistent timing
        consistency?.let {
            if (it.variationMs < 1000) {
                score += 10f
            }
        }
        
        return score.coerceIn(0f, 100f)
    }
}
