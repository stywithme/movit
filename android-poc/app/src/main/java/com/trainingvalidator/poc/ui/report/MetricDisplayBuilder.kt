package com.trainingvalidator.poc.ui.report

import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.MetricCode
import com.trainingvalidator.poc.training.report.*

/**
 * MetricDisplayBuilder - Builds dynamic metric displays based on exercise configuration
 * 
 * The backend determines which metrics to show for each exercise via ReportMetricsConfig.
 * This builder creates displayable metric items only for configured metrics.
 */
object MetricDisplayBuilder {
    
    /**
     * Build primary stats for exercise summary (3 main stats)
     * Uses Overall Quality if available, otherwise falls back to average score
     */
    fun buildPrimaryStats(
        report: PostTrainingReport,
        isArabic: Boolean
    ): List<StatItem> {
        val stats = mutableListOf<StatItem>()
        val summary = report.summary
        val config = report.exerciseConfig
        val isHold = config?.isHoldExercise() == true
        
        // Handle 0 reps case specially
        val hasNoReps = summary.totalReps == 0 && report.repTimeline.isEmpty()
        
        // 1. Quality/Score - Use Overall Quality if available
        val qualityScore = report.overallQuality?.score ?: summary.averageScore
        val qualityValue = if (hasNoReps) {
            "--"  // No meaningful score when no reps
        } else if (report.overallQuality != null) {
            report.overallQuality.getFormattedScore()
        } else {
            summary.getFormattedScore()
        }
        
        stats.add(StatItem(
            value = qualityValue,
            label = if (isArabic) "الجودة" else "Quality",
            isPrimary = true,
            status = if (hasNoReps) null else getStatusFromScore(qualityScore)
        ))
        
        // 2. Duration - always shown
        stats.add(StatItem(
            value = summary.getFormattedDuration(),
            label = if (isArabic) "المدة" else "Duration",
            isPrimary = false
        ))
        
        // 3. Reps or Hold Duration
        if (isHold) {
            val holdSummary = report.holdSummary
            stats.add(StatItem(
                value = holdSummary?.getFormattedAchieved() ?: "--",
                label = if (isArabic) "مدة الثبات" else "Hold",
                isPrimary = false
            ))
        } else {
            // Use totalReps or timeline size (whichever is larger)
            val repsCount = maxOf(summary.totalReps, report.repTimeline.size)
            stats.add(StatItem(
                value = repsCount.toString(),
                label = if (isArabic) "العدات" else "Reps",
                isPrimary = false
            ))
        }
        
        return stats
    }
    
    /**
     * Build primary stats for a specific rep
     */
    fun buildRepStats(
        rep: RepTimelineEntry,
        isArabic: Boolean
    ): List<StatItem> {
        return listOf(
            StatItem(
                value = rep.getFormattedScore(),
                label = if (isArabic) "النتيجة" else "Score",
                isPrimary = true,
                status = getStatusFromScore(rep.score)
            ),
            StatItem(
                value = rep.getFormattedDuration(),
                label = if (isArabic) "المدة" else "Duration",
                isPrimary = false
            ),
            StatItem(
                value = when {
                    rep.isPerfect() -> if (isArabic) "مثالي" else "Perfect"
                    rep.isBestRep -> if (isArabic) "الأفضل" else "Best"
                    rep.isWorstRep -> if (isArabic) "للتحسين" else "Improve"
                    else -> rep.stateIcon
                },
                label = if (isArabic) "الحالة" else "Status",
                isPrimary = false
            )
        )
    }
    
    /**
     * Build secondary metrics based on exercise configuration
     * Shows only metrics that are NOT excluded in the config
     */
    fun buildSecondaryMetrics(
        report: PostTrainingReport,
        isArabic: Boolean
    ): List<MetricDisplayItem> {
        val items = mutableListOf<MetricDisplayItem>()
        val config = report.exerciseConfig
        val metrics = report.performanceMetrics ?: PerformanceMetricsBuilder.build(report)
        val isHold = config?.isHoldExercise() == true
        
        // ═══════════════════════════════════════════════════════════════
        // PERFORMANCE METRICS - Priority order (most important first)
        // Form Score is already in primary stats, so skip it here
        // ═══════════════════════════════════════════════════════════════
        
        // ROM - Range of Motion
        if (shouldShow(config, MetricCode.ROM)) {
            metrics.formCard.rom?.let { rom ->
                items.add(MetricDisplayItem(
                    code = MetricCode.ROM,
                    icon = "📐",
                    label = if (isArabic) "المدى الحركي" else "ROM",
                    value = rom.displayValue,
                    status = rom.status
                ))
            }
        }
        
        // Symmetry - for bilateral exercises
        if (shouldShow(config, MetricCode.SYMMETRY)) {
            metrics.formCard.symmetry?.let { sym ->
                items.add(MetricDisplayItem(
                    code = MetricCode.SYMMETRY,
                    icon = "⚖️",
                    label = if (isArabic) "التوازن" else "Symmetry",
                    value = sym.displayValue,
                    status = sym.status
                ))
            }
        }
        
        // Stability
        if (shouldShow(config, MetricCode.STABILITY)) {
            metrics.safetyCard.stability?.let { stab ->
                items.add(MetricDisplayItem(
                    code = MetricCode.STABILITY,
                    icon = "🏋️",
                    label = if (isArabic) "الثبات" else "Stability",
                    value = stab.displayValue,
                    status = stab.status
                ))
            }
        }
        
        // Alignment Accuracy
        if (shouldShow(config, MetricCode.ALIGNMENT)) {
            metrics.safetyCard.alignmentAccuracy?.let { align ->
                items.add(MetricDisplayItem(
                    code = MetricCode.ALIGNMENT,
                    icon = "🛡️",
                    label = if (isArabic) "دقة المحاذاة" else "Alignment",
                    value = align.displayValue,
                    status = align.status
                ))
            }
        }
        
        // ═══════════════════════════════════════════════════════════════
        // TEMPO METRICS - Only for rep-based exercises
        // ═══════════════════════════════════════════════════════════════
        
        if (!isHold) {
            // Tempo (Eccentric-Isometric-Concentric)
            if (shouldShow(config, MetricCode.TEMPO)) {
                metrics.controlCard.tempo?.let { tempo ->
                    items.add(MetricDisplayItem(
                        code = MetricCode.TEMPO,
                        icon = "⏱️",
                        label = if (isArabic) "الإيقاع" else "Tempo",
                        value = tempo.getFormattedTempo(),
                        status = null
                    ))
                }
            }
            
            // TUT - Time Under Tension
            if (shouldShow(config, MetricCode.TUT)) {
                metrics.controlCard.totalTUT?.let { tut ->
                    items.add(MetricDisplayItem(
                        code = MetricCode.TUT,
                        icon = "⏳",
                        label = if (isArabic) "وقت الشد" else "TUT",
                        value = "${tut}s",
                        status = null
                    ))
                }
            }
        }
        
        // ═══════════════════════════════════════════════════════════════
        // LOAD METRICS - For weighted exercises
        // ═══════════════════════════════════════════════════════════════
        
        // Weight
        if (shouldShow(config, MetricCode.WEIGHT)) {
            report.summary.weightKg?.let { weight ->
                if (weight > 0) {
                    items.add(MetricDisplayItem(
                        code = MetricCode.WEIGHT,
                        icon = "🏋️",
                        label = if (isArabic) "الوزن" else "Weight",
                        value = report.summary.getFormattedWeight() ?: "${weight}kg",
                        status = null
                    ))
                }
            }
        }
        
        // Volume
        if (shouldShow(config, MetricCode.VOLUME)) {
            report.summary.totalVolume?.let { volume ->
                if (volume > 0) {
                    items.add(MetricDisplayItem(
                        code = MetricCode.VOLUME,
                        icon = "📦",
                        label = if (isArabic) "الحجم الكلي" else "Volume",
                        value = report.summary.getFormattedVolume() ?: "${volume}kg",
                        status = null
                    ))
                }
            }
        }
        
        // Est 1RM
        if (shouldShow(config, MetricCode.EST_1RM)) {
            report.summary.est1RM?.let { rm ->
                if (rm > 0) {
                    items.add(MetricDisplayItem(
                        code = MetricCode.EST_1RM,
                        icon = "💪",
                        label = if (isArabic) "القوة القصوى" else "Est. 1RM",
                        value = report.summary.getFormattedEst1RM() ?: "${rm}kg",
                        status = null
                    ))
                }
            }
        }
        
        // ═══════════════════════════════════════════════════════════════
        // QUALITY METRICS
        // ═══════════════════════════════════════════════════════════════
        
        // Form Consistency (need 4+ reps)
        if (shouldShow(config, MetricCode.FORM_CONSISTENCY) && report.repTimeline.size >= 4) {
            metrics.formCard.formConsistency?.let { cons ->
                items.add(MetricDisplayItem(
                    code = MetricCode.FORM_CONSISTENCY,
                    icon = "📊",
                    label = if (isArabic) "ثبات الشكل" else "Consistency",
                    value = cons.displayValue,
                    status = cons.status
                ))
            }
        }
        
        // Fatigue Index
        if (shouldShow(config, MetricCode.FATIGUE_INDEX)) {
            metrics.controlCard.fatigueIndex?.let { fatigue ->
                items.add(MetricDisplayItem(
                    code = MetricCode.FATIGUE_INDEX,
                    icon = "📉",
                    label = if (isArabic) "نقطة التعب" else "Fatigue Point",
                    value = if (isArabic) "العدة #$fatigue" else "Rep #$fatigue",
                    status = MetricStatus.FAIR
                ))
            }
        }
        
        // Danger Count - always show if there are alerts
        val dangerCount = report.dangerAlerts.size
        if (dangerCount > 0) {
            items.add(MetricDisplayItem(
                code = MetricCode.ALIGNMENT, // Using alignment as proxy
                icon = "⚠️",
                label = if (isArabic) "تحذيرات الأمان" else "Safety Alerts",
                value = dangerCount.toString(),
                status = MetricStatus.NEEDS_WORK
            ))
        }
        
        return items
    }
    
    /**
     * Build message/insight for exercise summary
     */
    fun buildMessage(
        report: PostTrainingReport,
        isArabic: Boolean
    ): MessageItem {
        val quickInsight = report.quickInsight ?: QuickInsightGenerator.generate(report)
        val progressLine = buildProgressLine(report, isArabic)
        val baseSubtitle = if (isArabic) quickInsight.subtitle.ar else quickInsight.subtitle.en
        val mergedSubtitle = when {
            progressLine.isBlank() -> baseSubtitle
            baseSubtitle.isBlank() -> progressLine
            else -> "$baseSubtitle\n$progressLine"
        }
        
        return MessageItem(
            icon = quickInsight.icon,
            text = if (isArabic) quickInsight.title.ar else quickInsight.title.en,
            subtext = mergedSubtitle,
            type = quickInsight.type
        )
    }
    
    /**
     * Build message for a specific rep
     */
    fun buildRepMessage(
        rep: RepTimelineEntry,
        isArabic: Boolean
    ): MessageItem {
        val (icon, text) = when {
            rep.isPerfect() -> "⭐" to (if (isArabic) "أداء مثالي!" else "Perfect form!")
            rep.isBestRep -> "🏆" to (if (isArabic) "أفضل عدة في الجلسة" else "Best rep of the session")
            rep.isWorstRep -> "📈" to (if (isArabic) "فرصة للتحسين" else "Room for improvement")
            rep.isDanger() -> "⚠️" to (if (isArabic) "تحقق من الشكل" else "Check your form")
            rep.score >= 80 -> "🟢" to (if (isArabic) "شكل جيد" else "Good form")
            rep.score >= 60 -> "🟡" to (if (isArabic) "مقبول" else "Acceptable")
            else -> "🔴" to (if (isArabic) "يحتاج تحسين" else "Needs work")
        }
        
        val subtext = rep.stateMessage?.let { 
            if (isArabic) it.ar else it.en 
        } ?: ""
        
        return MessageItem(
            icon = icon,
            text = text,
            subtext = subtext,
            type = when {
                rep.isPerfect() || rep.isBestRep -> InsightType.CELEBRATION
                rep.isDanger() -> InsightType.DANGER_WARNING
                else -> InsightType.FOCUS_POINT
            }
        )
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════
    
    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        if (config == null) return true
        return config.shouldShowMetric(metric)
    }
    
    private fun getStatusFromScore(score: Float): MetricStatus {
        return when {
            score >= 90 -> MetricStatus.EXCELLENT
            score >= 80 -> MetricStatus.GOOD
            score >= 70 -> MetricStatus.FAIR
            else -> MetricStatus.NEEDS_WORK
        }
    }

    private fun buildProgressLine(report: PostTrainingReport, isArabic: Boolean): String {
        val summary = report.summary
        val hasNoReps = summary.totalReps == 0 && report.repTimeline.isEmpty()

        val qualityText = if (hasNoReps) {
            ""
        } else {
            val score = report.overallQuality?.getFormattedScore() ?: summary.getFormattedScore()
            if (isArabic) "الجودة $score" else "Quality $score"
        }

        val timeText = if (isArabic) {
            "الوقت ${summary.getFormattedDuration()}"
        } else {
            "Time ${summary.getFormattedDuration()}"
        }

        val repsOrHold = if (report.isHoldExercise()) {
            val hold = report.holdSummary?.getFormattedAchieved() ?: ""
            if (hold.isBlank()) "" else if (isArabic) "الثبات $hold" else "Hold $hold"
        } else {
            val repsCount = maxOf(summary.totalReps, report.repTimeline.size)
            if (isArabic) "العدات $repsCount" else "Reps $repsCount"
        }

        return listOf(qualityText, repsOrHold, timeText)
            .filter { it.isNotBlank() }
            .joinToString(separator = " · ")
    }
}

/**
 * Data class for primary stats display
 */
data class StatItem(
    val value: String,
    val label: String,
    val isPrimary: Boolean = false,
    val status: MetricStatus? = null
)

/**
 * Data class for secondary metric display
 */
data class MetricDisplayItem(
    val code: MetricCode,
    val icon: String,
    val label: String,
    val value: String,
    val status: MetricStatus?
)

/**
 * Data class for message/insight display
 */
data class MessageItem(
    val icon: String,
    val text: String,
    val subtext: String,
    val type: InsightType
)
