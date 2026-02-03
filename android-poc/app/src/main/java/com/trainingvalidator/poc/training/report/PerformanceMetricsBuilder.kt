package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.engine.ScoreCalculator
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.MetricCode

/**
 * PerformanceMetricsBuilder - Builds EnhancedPerformanceMetrics from report data
 * 
 * Aggregates raw metrics into the 3 main cards:
 * - Form (الشكل)
 * - Safety (الأمان)
 * - Control (التحكم)
 * 
 * Uses ScoreCalculator for consistent score calculation across the app.
 * 
 * IMPORTANT: Calculates all metrics - display layer decides what to show
 * based on ExerciseConfigSnapshot in the report.
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
     * Check if a metric should be calculated/shown
     */
    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        // If no config, show all (backward compatibility)
        if (config == null) return true
        return config.shouldShowMetric(metric)
    }
    
    /**
     * Build Form metrics card
     * Combines: FormScore, ROM, Symmetry, FormConsistency
     * Always calculates all metrics - display layer decides what to show
     */
    private fun buildFormMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): FormMetrics {
        val summary = report.summary
        val stateBreakdown = summary.stateBreakdown
        
        // Calculate form score from state distribution (always shown)
        val formScore = calculateFormScore(stateBreakdown)
        
        // ROM - always calculate
        val romMetric = calculateROMMetric(report)
        
        // Symmetry - always calculate (will be null for non-bilateral)
        val symmetryMetric = calculateSymmetryMetric(report)
        
        // Form Consistency - need 4+ reps
        val consistencyMetric = if (report.repTimeline.size >= 4) {
            calculateFormConsistencyMetric(report)
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
     * Combines: AlignmentAccuracy, Stability, DangerCount
     * Always calculates all metrics - display layer decides what to show
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
        
        // Alignment accuracy - always calculate
        val alignmentMetric = calculateAlignmentMetric(report)
        
        // Stability - always calculate
        val stabilityMetric = calculateStabilityMetric(report)
        
        return SafetyMetrics(
            overallScore = safetyScore,
            alignmentAccuracy = alignmentMetric,
            stability = stabilityMetric,
            dangerCount = dangerCount
        )
    }
    
    /**
     * Build Control metrics card
     * Combines: Tempo, TUT, FatigueIndex
     * Always calculates all metrics - display layer decides what to show
     */
    private fun buildControlMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): ControlMetrics {
        val isHold = config?.isHoldExercise() == true
        
        // Calculate tempo - for rep-based exercises
        val tempoDisplay = if (!isHold) {
            calculateTempoDisplay(report)
        } else null
        
        // Total Time Under Tension - for rep-based exercises
        val totalTUT = if (!isHold) {
            (report.summary.durationMs / 1000).toInt()
        } else null
        
        // Fatigue index - need 4+ reps
        val fatigueIndex = if (report.repTimeline.size >= 4) {
            detectFatigueIndex(report)
        } else null
        
        // Control score based on consistency and tempo
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
    
    private fun calculateFormScore(breakdown: StateBreakdown): MetricWithStatus {
        // Use ScoreCalculator rates for consistency
        // PERFECT = 100, NORMAL = 80, PAD = 60, WARNING = 40, DANGER = 0
        val total = breakdown.total.toFloat()
        if (total == 0f) {
            return MetricWithStatus.fromPercentage(0f)
        }
        
        val weightedScore = (
            breakdown.perfectCount * ScoreCalculator.getScoreRate(JointState.PERFECT) +
            breakdown.normalCount * ScoreCalculator.getScoreRate(JointState.NORMAL) +
            breakdown.padCount * ScoreCalculator.getScoreRate(JointState.PAD) +
            breakdown.warningCount * ScoreCalculator.getScoreRate(JointState.WARNING) +
            breakdown.dangerCount * ScoreCalculator.getScoreRate(JointState.DANGER)
        ) / total
        
        return MetricWithStatus.fromPercentage(
            weightedScore,
            advice = when {
                weightedScore >= 90 -> LocalizedText(ar = "شكل ممتاز!", en = "Excellent form!")
                weightedScore >= 70 -> LocalizedText(ar = "شكل جيد", en = "Good form")
                else -> LocalizedText(ar = "ركز على الشكل", en = "Focus on form")
            }
        )
    }
    
    private fun calculateROMMetric(report: PostTrainingReport): MetricWithStatus? {
        // Calculate ROM from rep timeline scores
        // Higher scores indicate better ROM (reaching target angles)
        val timeline = report.repTimeline
        
        if (timeline.isEmpty()) {
            // Fallback to state breakdown
            val breakdown = report.summary.stateBreakdown
            val total = breakdown.total
            if (total == 0) return null
            
            // Use weighted score from states
            val romScore = (
                breakdown.perfectCount * 100f +
                breakdown.normalCount * 80f +
                breakdown.padCount * 60f +
                breakdown.warningCount * 40f
            ) / total
            
            return MetricWithStatus.fromPercentage(
                romScore,
                advice = if (romScore >= 80) {
                    LocalizedText(ar = "مدى حركي جيد", en = "Good range of motion")
                } else {
                    LocalizedText(ar = "حاول النزول أعمق", en = "Try going deeper")
                }
            )
        }
        
        // Use average score from timeline as ROM indicator
        val avgScore = timeline.map { it.score }.average().toFloat()
        
        return MetricWithStatus.fromPercentage(
            avgScore,
            advice = if (avgScore >= 80) {
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
        
        if (totalReps == 0) {
            return MetricWithStatus.fromPercentage(
                100f,
                advice = LocalizedText(ar = "متوازن تماماً", en = "Perfectly balanced")
            )
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
    
    private fun calculateFormConsistencyMetric(report: PostTrainingReport): MetricWithStatus? {
        val timeline = report.repTimeline
        if (timeline.size < 4) return null
        
        // Calculate variance in scores
        val scores = timeline.map { it.score }
        val mean = scores.average()
        val variance = scores.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // Lower variance = higher consistency
        // stdDev of 0 = 100%, stdDev of 30+ = 0%
        val consistencyScore = (100 - (stdDev * 3.33)).coerceIn(0.0, 100.0).toFloat()
        
        return MetricWithStatus.fromPercentage(
            consistencyScore,
            advice = if (consistencyScore >= 80) {
                LocalizedText(ar = "ثبات ممتاز", en = "Excellent consistency")
            } else {
                LocalizedText(ar = "حاول الحفاظ على نفس الشكل", en = "Try to maintain the same form")
            }
        )
    }
    
    private fun calculateAlignmentMetric(report: PostTrainingReport): MetricWithStatus? {
        // Use timeline size as total (includes all reps)
        val totalReps = report.repTimeline.size.coerceAtLeast(report.summary.totalReps)
        
        if (totalReps == 0) {
            return MetricWithStatus.fromPercentage(
                100f,
                advice = LocalizedText(ar = "محاذاة ممتازة", en = "Excellent alignment")
            )
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
    
    private fun calculateStabilityMetric(report: PostTrainingReport): MetricWithStatus? {
        // Use consistency metrics for stability
        val consistency = report.consistency ?: return null
        
        // Less variation = more stable
        val variationMs = consistency.variationMs
        val stabilityScore = when {
            variationMs < 500 -> 100f
            variationMs < 1000 -> 90f
            variationMs < 1500 -> 80f
            variationMs < 2000 -> 70f
            else -> 60f
        }
        
        return MetricWithStatus.fromPercentage(
            stabilityScore,
            advice = if (stabilityScore >= 80) {
                LocalizedText(ar = "ثبات جيد", en = "Good stability")
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
    
    private fun detectFatigueIndex(report: PostTrainingReport): Int? {
        val timeline = report.repTimeline
        if (timeline.size < 4) return null
        
        // Find where score drops by more than 15% from first 3 reps average
        val firstThreeAvg = timeline.take(3).map { it.score }.average()
        
        for (i in 3 until timeline.size) {
            if (timeline[i].score < firstThreeAvg - 15) {
                return timeline[i].repNumber
            }
        }
        
        return null
    }
    
    private fun calculateControlScore(report: PostTrainingReport, fatigueIndex: Int?): MetricWithStatus {
        // Base score from consistency
        var score = 80f
        
        // Bonus for no fatigue
        if (fatigueIndex == null) {
            score += 10f
        } else {
            // Penalize based on how early fatigue started
            val totalReps = report.summary.totalReps
            val fatigueRatio = fatigueIndex.toFloat() / totalReps
            if (fatigueRatio < 0.5) {
                score -= 20f
            } else if (fatigueRatio < 0.7) {
                score -= 10f
            }
        }
        
        // Bonus for consistent timing
        report.consistency?.let {
            if (it.variationMs < 1000) {
                score += 10f
            }
        }
        
        return MetricWithStatus.fromPercentage(
            score.coerceIn(0f, 100f),
            advice = when {
                fatigueIndex != null -> LocalizedText(
                    ar = "بدأ التعب من العدة #$fatigueIndex",
                    en = "Fatigue started at rep #$fatigueIndex"
                )
                score >= 90 -> LocalizedText(ar = "تحكم ممتاز", en = "Excellent control")
                else -> LocalizedText(ar = "حافظ على الإيقاع", en = "Maintain the tempo")
            }
        )
    }
}
