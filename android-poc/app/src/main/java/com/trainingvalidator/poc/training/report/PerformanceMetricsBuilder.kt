package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.engine.ScoreCalculator
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.MetricCode

/**
 * PerformanceMetricsBuilder - Builds EnhancedPerformanceMetrics from report data
 * 
 * Aggregates metrics into the 3 main cards:
 * - Form (الشكل): FormScore, ROM, Symmetry (LSI for bilateral), FormConsistency
 * - Safety (الأمان): PositionCheck-based Alignment, Trunk Stability, DangerCount
 * - Control (التحكم): Tempo, TUT, VelocityLoss, TempoConsistency, FatigueIndex
 * 
 * SINGLE SOURCE OF TRUTH PRINCIPLE:
 * - Uses pre-calculated metrics from PerformanceSummary
 * - These values are calculated ONCE in ReportGenerator using MetricsCalculator
 * - This class only FORMATS metrics for display, does NOT recalculate them
 * 
 * V2 IMPROVEMENTS:
 * - Alignment uses PositionCheck data (ERROR/WARNING/TIP severity levels)
 * - Symmetry uses LSI (Limb Symmetry Index) for bilateral alternating exercises
 * - Stability uses trunk stability (spine angle variance from MetricsCalculator)
 * - Safety Score combines Position alignment + DANGER events + Trunk stability
 * - Control Score incorporates VelocityLoss%, TempoConsistency, FormConsistency
 * - Tempo uses real phase data from SessionMetrics (not estimated from duration)
 * - TUT uses sum of rep durations (not session duration)
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
     * Combines: FormScore, ROM, Symmetry (LSI for bilateral), FormConsistency
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
        
        // Symmetry — uses LSI approach for bilateral alternating exercises
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
     * 
     * V2: Combines PositionCheck-based Alignment, Trunk Stability, DangerCount.
     * 
     * Safety Score formula (weighted):
     * - 40% Position Check Alignment (reps without ERROR/WARNING position violations)
     * - 30% DANGER-free ratio (reps without DANGER joint state)
     * - 30% Trunk Stability (from SessionMetrics — spine angle variance)
     */
    private fun buildSafetyMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): SafetyMetrics {
        val summary = report.summary
        val dangerCount = report.dangerAlerts.size
        val totalReps = summary.totalReps
        
        // Calculate overall safety score with multi-factor formula
        val safetyScore = calculateSafetyScore(report)
        
        // Alignment — uses PositionCheck data (ERROR + WARNING + TIP)
        val alignmentMetric = if (shouldShow(config, MetricCode.ALIGNMENT)) {
            calculateAlignmentMetric(report)
        } else null
        
        // Trunk Stability — uses pre-calculated avgStability from SessionMetrics
        val stabilityMetric = if (shouldShow(config, MetricCode.STABILITY)) {
            calculateStabilityMetric(report)
        } else null
        
        return SafetyMetrics(
            overallScore = safetyScore,
            alignmentAccuracy = alignmentMetric,
            stability = stabilityMetric,
            dangerCount = dangerCount
        )
    }
    
    /**
     * Build Control metrics card
     * 
     * V2: Combines Tempo (real data), TUT (rep-based), VelocityLoss, 
     * TempoConsistency, FatigueIndex.
     * 
     * Control Score formula (weighted):
     * - 30% Tempo Consistency (CV of rep durations)
     * - 25% Velocity Loss % (neuromuscular fatigue)
     * - 25% Form Consistency (score variance across reps)
     * - 20% Fatigue penalty (if fatigue detected early)
     */
    private fun buildControlMetrics(report: PostTrainingReport, config: ExerciseConfigSnapshot?): ControlMetrics {
        val summary = report.summary
        val isHold = config?.isHoldExercise() == true
        
        // Tempo — uses real phase data from SessionMetrics
        val tempoDisplay = if (!isHold && shouldShow(config, MetricCode.TEMPO)) {
            calculateTempoDisplay(report)
        } else null
        
        // TUT — uses sum of rep durations (not session duration)
        val totalTUT = if (!isHold && shouldShow(config, MetricCode.TUT)) {
            summary.totalTUT?.let { it / 1000 }
                ?: (summary.durationMs / 1000).toInt()  // Fallback for backward compatibility
        } else null
        
        // Fatigue index - use pre-calculated value from summary
        val fatigueIndex = if (shouldShow(config, MetricCode.FATIGUE_INDEX)) {
            summary.fatigueIndex
        } else null
        
        // Velocity Loss %
        val velocityLossMetric = summary.velocityLoss?.let { vl ->
            formatVelocityLossMetric(vl)
        }
        
        // Tempo Consistency
        val tempoConsistencyMetric = summary.tempoConsistency?.let { tc ->
            formatTempoConsistencyMetric(tc)
        }
        
        // Control score — multi-factor calculation
        val controlScore = calculateControlScore(report, fatigueIndex)
        
        return ControlMetrics(
            overallScore = controlScore,
            tempo = tempoDisplay,
            totalTUT = totalTUT,
            fatigueIndex = fatigueIndex,
            velocityLoss = velocityLossMetric,
            tempoConsistency = tempoConsistencyMetric
        )
    }
    
    /**
     * Build Load metrics (for weighted exercises)
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
    // FORM CARD HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        if (config == null) return true
        return config.shouldShowMetric(metric)
    }
    
    private fun calculateFormScore(report: PostTrainingReport, breakdown: StateBreakdown): MetricWithStatus {
        val timelineScores = report.repTimeline.map { it.score }
        val scoreValue = if (timelineScores.isNotEmpty()) {
            timelineScores.average().toFloat()
        } else {
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
    
    /**
     * Calculate Symmetry metric.
     * 
     * For bilateral alternating exercises: Uses LSI (Limb Symmetry Index).
     * Compares average scores between left-side and right-side reps.
     * 
     * For non-bilateral exercises with paired joints: Uses error-based approach.
     */
    private fun calculateSymmetryMetric(report: PostTrainingReport): MetricWithStatus? {
        if (report.exerciseConfig?.isBilateral != true) {
            return null
        }
        
        val totalReps = report.repTimeline.size.coerceAtLeast(report.summary.totalReps)
        if (totalReps < 2) return null
        
        // For bilateral alternating: Compare left-side reps vs right-side reps
        // Side is deterministic: odd reps = startSide, even reps = otherSide
        // We use rep scores as a proxy for performance comparison (LSI approach)
        val leftScores = mutableListOf<Float>()
        val rightScores = mutableListOf<Float>()
        
        report.repTimeline.forEachIndexed { index, rep ->
            // Simple alternating: even index = right (startSide), odd index = left
            // This matches default bilateral config: startSide = "right", switchEvery = 1
            if (index % 2 == 0) {
                rightScores.add(rep.score)
            } else {
                leftScores.add(rep.score)
            }
        }
        
        if (leftScores.isEmpty() || rightScores.isEmpty()) {
            return MetricWithStatus.fromPercentage(
                100f,
                advice = LocalizedText(ar = "بيانات غير كافية", en = "Insufficient data")
            )
        }
        
        // LSI = min(avgLeft, avgRight) / max(avgLeft, avgRight) × 100
        val avgLeft = leftScores.average().toFloat()
        val avgRight = rightScores.average().toFloat()
        val maxAvg = maxOf(avgLeft, avgRight)
        val minAvg = minOf(avgLeft, avgRight)
        
        val lsiScore = if (maxAvg > 0) {
            (minAvg / maxAvg * 100f).coerceIn(0f, 100f)
        } else 100f
        
        return MetricWithStatus.fromPercentage(
            lsiScore,
            advice = when {
                lsiScore >= 95 -> LocalizedText(ar = "متوازن تماماً", en = "Perfectly balanced")
                lsiScore >= 85 -> LocalizedText(ar = "توازن جيد", en = "Good balance")
                lsiScore >= 75 -> LocalizedText(ar = "فرق طفيف بين الجانبين", en = "Slight side difference")
                else -> LocalizedText(ar = "عدم توازن واضح — ركز على الجانب الأضعف", en = "Noticeable imbalance — focus on weaker side")
            }
        )
    }
    
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
    
    // ═══════════════════════════════════════════════════════════════
    // SAFETY CARD HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Calculate overall Safety Score using multi-factor formula.
     * 
     * Components:
     * - Position Check Alignment: % reps without ERROR/WARNING position violations (40%)
     * - DANGER-free ratio: % reps without DANGER joint state (30%)
     * - Trunk Stability: from MetricsCalculator avgStability (30%)
     */
    private fun calculateSafetyScore(report: PostTrainingReport): MetricWithStatus {
        val summary = report.summary
        val totalReps = summary.totalReps
        val dangerCount = report.dangerAlerts.size
        
        if (totalReps == 0) {
            return MetricWithStatus.fromPercentage(100f)
        }
        
        // Factor 1: Position Check Alignment (40%)
        // Reps without any ERROR or WARNING position check violations
        val positionCleanReps = totalReps - summary.positionErrorReps - summary.positionWarningReps
        val positionAlignmentPct = (positionCleanReps.coerceAtLeast(0).toFloat() / totalReps) * 100f
        
        // Factor 2: DANGER-free ratio (30%)
        val dangerFreePct = ((totalReps - dangerCount).toFloat() / totalReps) * 100f
        
        // Factor 3: Trunk Stability (30%) — from SessionMetrics
        val trunkStabilityPct = summary.avgStability ?: 90f  // Default 90% if not available
        
        val safetyScore = (
            positionAlignmentPct * 0.40f +
            dangerFreePct * 0.30f +
            trunkStabilityPct * 0.30f
        ).coerceIn(0f, 100f)
        
        return MetricWithStatus.fromPercentage(
            safetyScore,
            advice = when {
                dangerCount > 0 -> LocalizedText(
                    ar = "راجع تنبيهات الأمان",
                    en = "Review safety alerts"
                )
                summary.positionErrorReps > 0 -> LocalizedText(
                    ar = "تحقق من وضعية الجسم",
                    en = "Check body positioning"
                )
                safetyScore >= 90 -> LocalizedText(ar = "أداء آمن", en = "Safe performance")
                else -> LocalizedText(ar = "انتبه لوضعية الجسم", en = "Watch your body positioning")
            }
        )
    }
    
    /**
     * Calculate Alignment Accuracy from PositionCheck data.
     * 
     * V2: Uses PositionValidator severity levels (ERROR/WARNING/TIP):
     * - ERROR position violations: Strong negative impact (-15 per rep)
     * - WARNING position violations: Moderate negative impact (-8 per rep)  
     * - TIP position checks: Minor negative impact (-3 per rep)
     * - JointState WARNING/DANGER: Also factor in (-10 per rep)
     * 
     * This produces a more accurate alignment score because PositionChecks
     * measure spatial relationships (knee-over-toe, body alignment) which
     * are fundamentally different from angle-based JointState errors.
     */
    private fun calculateAlignmentMetric(report: PostTrainingReport): MetricWithStatus? {
        val summary = report.summary
        val totalReps = report.repTimeline.size.coerceAtLeast(summary.totalReps)
        
        if (totalReps == 0) return null
        
        // Start at 100, deduct for violations
        var deduction = 0f
        
        // Position Check violations (the core alignment indicators)
        val errorPenalty = summary.positionErrorReps * 15f
        val warningPenalty = summary.positionWarningReps * 8f
        val tipPenalty = summary.positionTipReps * 3f
        
        // JointState violations (angle-based errors, complementary to position checks)
        val stateBreakdown = summary.stateBreakdown
        val jointStatePenalty = (stateBreakdown.warningCount + stateBreakdown.dangerCount) * 10f
        
        deduction = errorPenalty + warningPenalty + tipPenalty + jointStatePenalty
        
        // Normalize deduction against total reps (so it's proportional)
        val maxPossibleDeduction = totalReps * 15f  // If every rep had ERROR, score = 0
        val alignmentScore = if (maxPossibleDeduction > 0) {
            ((1 - deduction / maxPossibleDeduction) * 100).coerceIn(0f, 100f)
        } else 100f
        
        return MetricWithStatus.fromPercentage(
            alignmentScore,
            advice = when {
                summary.positionErrorReps > 0 -> LocalizedText(
                    ar = "أخطاء وضعية في ${summary.positionErrorReps} عدات",
                    en = "Position errors in ${summary.positionErrorReps} reps"
                )
                summary.positionWarningReps > 0 -> LocalizedText(
                    ar = "تحذيرات في ${summary.positionWarningReps} عدات",
                    en = "Warnings in ${summary.positionWarningReps} reps"
                )
                alignmentScore >= 90 -> LocalizedText(ar = "محاذاة ممتازة", en = "Excellent alignment")
                alignmentScore >= 70 -> LocalizedText(ar = "محاذاة جيدة", en = "Good alignment")
                else -> LocalizedText(ar = "تحقق من وضعية المفاصل", en = "Check joint positions")
            }
        )
    }
    
    /**
     * Calculate Trunk Stability metric.
     * 
     * V2: Uses pre-calculated avgStability from SessionMetrics which is based on:
     * - Spine angle variance (if spine is tracked) — preferred
     * - Hip midpoint variance (fallback)
     */
    private fun calculateStabilityMetric(report: PostTrainingReport): MetricWithStatus? {
        val stabilityValue = report.summary.avgStability ?: return null
        
        return MetricWithStatus.fromPercentage(
            stabilityValue,
            advice = when {
                stabilityValue >= 90 -> LocalizedText(ar = "ثبات ممتاز للجذع", en = "Excellent trunk stability")
                stabilityValue >= 75 -> LocalizedText(ar = "ثبات جيد", en = "Good stability")
                else -> LocalizedText(ar = "حافظ على استقامة الجذع", en = "Keep your trunk stable")
            }
        )
    }
    
    // ═══════════════════════════════════════════════════════════════
    // CONTROL CARD HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Calculate Tempo from real phase data in SessionMetrics.
     * 
     * V2: Uses actual eccentric/isometric/concentric timing from MetricsCalculator
     * instead of estimating from total duration.
     */
    private fun calculateTempoDisplay(report: PostTrainingReport): TempoDisplay? {
        // Prefer real tempo data from SessionMetrics
        val avgTempo = report.summary.avgTempo
        if (avgTempo != null && avgTempo.sum() > 0) {
            return TempoDisplay(
                eccentricMs = avgTempo.getOrElse(0) { 0 },
                isometricMs = avgTempo.getOrElse(1) { 0 },
                concentricMs = avgTempo.getOrElse(2) { 0 }
            )
        }
        
        // Fallback: estimate from consistency data (backward compatibility)
        val consistency = report.consistency ?: return null
        val avgMs = consistency.averageDurationMs
        
        return TempoDisplay(
            eccentricMs = (avgMs * 0.4).toInt(),
            isometricMs = (avgMs * 0.2).toInt(),
            concentricMs = (avgMs * 0.4).toInt()
        )
    }
    
    /**
     * Format Velocity Loss % metric.
     * 
     * VL% indicates neuromuscular fatigue within the session.
     * Lower is better (0% = no velocity loss = no mechanical fatigue).
     * 
     * Display: Inverted (100 - VL%) so higher = better for the user.
     */
    private fun formatVelocityLossMetric(velocityLossPct: Float): MetricWithStatus? {
        if (velocityLossPct < 0) return null
        
        // Invert: 0% loss = 100% score, 100% loss = 0% score
        val displayScore = (100f - velocityLossPct).coerceIn(0f, 100f)
        
        return MetricWithStatus.fromPercentage(
            displayScore,
            advice = when {
                velocityLossPct < 10 -> LocalizedText(ar = "سرعة ثابتة", en = "Consistent velocity")
                velocityLossPct < 20 -> LocalizedText(ar = "انخفاض طفيف في السرعة", en = "Slight velocity drop")
                velocityLossPct < 35 -> LocalizedText(ar = "تعب ميكانيكي واضح", en = "Noticeable mechanical fatigue")
                else -> LocalizedText(ar = "تعب شديد — قلل الحمل أو العدات", en = "Severe fatigue — reduce load or reps")
            }
        )
    }
    
    /**
     * Format Tempo Consistency metric from pre-calculated value.
     */
    private fun formatTempoConsistencyMetric(tempoConsistencyPct: Float): MetricWithStatus? {
        return MetricWithStatus.fromPercentage(
            tempoConsistencyPct,
            advice = when {
                tempoConsistencyPct >= 85 -> LocalizedText(ar = "إيقاع ثابت ممتاز", en = "Excellent tempo consistency")
                tempoConsistencyPct >= 70 -> LocalizedText(ar = "إيقاع جيد", en = "Good tempo")
                else -> LocalizedText(ar = "تفاوت في سرعة الأداء", en = "Speed variation detected")
            }
        )
    }
    
    /**
     * Calculate Control Score with multi-factor formula.
     * 
     * V2 Components:
     * - Tempo Consistency: 30% (CV of rep durations)
     * - Velocity Loss inversion: 25% (100 - VL%)
     * - Form Consistency: 25% (score variance across reps)
     * - Fatigue penalty: 20% (penalizes early fatigue detection)
     */
    private fun calculateControlScore(report: PostTrainingReport, fatigueIndex: Int?): MetricWithStatus {
        val summary = report.summary
        val scoreValue = calculateControlScoreValue(
            totalReps = summary.totalReps,
            consistency = report.consistency,
            fatigueIndex = fatigueIndex,
            tempoConsistency = summary.tempoConsistency,
            velocityLoss = summary.velocityLoss,
            formConsistency = summary.formConsistency
        )
        
        return MetricWithStatus.fromPercentage(
            scoreValue,
            advice = when {
                fatigueIndex != null -> LocalizedText(
                    ar = "بدأ التعب من العدة #$fatigueIndex",
                    en = "Fatigue started at rep #$fatigueIndex"
                )
                scoreValue >= 90 -> LocalizedText(ar = "تحكم ممتاز", en = "Excellent control")
                scoreValue >= 70 -> LocalizedText(ar = "تحكم جيد", en = "Good control")
                else -> LocalizedText(ar = "حافظ على الإيقاع", en = "Maintain the tempo")
            }
        )
    }
    
    /**
     * Shared Control score calculation for report and overall quality.
     * 
     * V2: Multi-factor formula with real metrics.
     * Falls back to legacy calculation when new metrics are unavailable.
     */
    internal fun calculateControlScoreValue(
        totalReps: Int,
        consistency: ConsistencyMetrics?,
        fatigueIndex: Int?,
        tempoConsistency: Float? = null,
        velocityLoss: Float? = null,
        formConsistency: Float? = null
    ): Float {
        if (totalReps <= 0) return 50f
        
        // If we have V2 metrics, use the multi-factor formula
        if (tempoConsistency != null || velocityLoss != null || formConsistency != null) {
            // Factor 1: Tempo Consistency (30%)
            val tcScore = tempoConsistency ?: 80f
            
            // Factor 2: Velocity stability (25%) — 100 - VL%
            val vlScore = if (velocityLoss != null) (100f - velocityLoss) else 85f
            
            // Factor 3: Form Consistency (25%)
            val fcScore = formConsistency ?: 80f
            
            // Factor 4: Fatigue penalty (20%) — no fatigue = 100%, early fatigue = lower
            val fatiguePenalty = if (fatigueIndex != null) {
                val fatigueRatio = fatigueIndex.toFloat() / totalReps
                (fatigueRatio * 100f).coerceIn(30f, 100f)
            } else 100f
            
            return (
                tcScore * 0.30f +
                vlScore * 0.25f +
                fcScore * 0.25f +
                fatiguePenalty * 0.20f
            ).coerceIn(0f, 100f)
        }
        
        // Legacy fallback (backward compatibility)
        var score = 80f
        
        if (fatigueIndex == null) {
            score += 10f
        } else {
            val fatigueRatio = fatigueIndex.toFloat() / totalReps
            if (fatigueRatio < 0.5) {
                score -= 20f
            } else if (fatigueRatio < 0.7) {
                score -= 10f
            }
        }
        
        consistency?.let {
            if (it.variationMs < 1000) {
                score += 10f
            }
        }
        
        return score.coerceIn(0f, 100f)
    }
}
