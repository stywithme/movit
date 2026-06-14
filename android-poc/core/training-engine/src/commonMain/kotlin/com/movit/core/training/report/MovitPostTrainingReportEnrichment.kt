package com.movit.core.training.report

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.MetricCode
import com.movit.core.training.config.ReportMetricsConfig
import com.movit.core.training.config.TrackingMode
import com.movit.core.training.engine.CountingMethod
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.RepResult
import kotlinx.serialization.Serializable

@Serializable
enum class MovitRepQuality {
    CLEAN,
    NEEDS_CORRECTION,
    DANGER,
    ;

    companion object {
        fun fromRep(rep: RepResult): MovitRepQuality = when {
            rep.isInvalidated -> DANGER
            rep.isCounted -> CLEAN
            else -> NEEDS_CORRECTION
        }
    }
}

@Serializable
enum class MovitRepStatus {
    PERFECT,
    GOOD,
    ACCEPTABLE,
    HAS_ERRORS,
    DANGER,
    BEST_REP,
    WORST_REP,
    FAILED,
    ;

    companion object {
        fun fromState(state: JointState, isBest: Boolean = false, isWorst: Boolean = false): MovitRepStatus = when {
            isBest -> BEST_REP
            isWorst -> WORST_REP
            state == JointState.PERFECT -> PERFECT
            state == JointState.NORMAL -> GOOD
            state == JointState.PAD -> ACCEPTABLE
            state == JointState.WARNING -> HAS_ERRORS
            state == JointState.DANGER -> DANGER
            else -> GOOD
        }
    }
}

@Serializable
enum class MovitTipSeverity {
    CRITICAL,
    IMPORTANT,
    HELPFUL,
}

@Serializable
enum class MovitTipCategory {
    SAFETY,
    DEPTH,
    ALIGNMENT,
    TIMING,
    POSITION,
    STABILITY,
    GENERAL,
}

@Serializable
enum class MovitHoldQuality {
    STABLE,
    MINOR_DRIFT,
    UNSTABLE,
    ;

    companion object {
        fun fromErrorCount(count: Int): MovitHoldQuality = when {
            count == 0 -> STABLE
            count <= 3 -> MINOR_DRIFT
            else -> UNSTABLE
        }
    }
}

@Serializable
enum class MovitQualityRating {
    EXCELLENT,
    GOOD,
    FAIR,
    NEEDS_WORK,
    ;

    companion object {
        fun fromScore(score: Float): MovitQualityRating = when {
            score >= 90f -> EXCELLENT
            score >= 70f -> GOOD
            score >= 50f -> FAIR
            else -> NEEDS_WORK
        }
    }
}

@Serializable
data class MovitDangerAlert(
    val repNumber: Int,
    val jointCode: String,
    val jointName: LocalizedText,
    val actualAngle: Double,
    val safeRange: AngleRange,
    val dangerMessage: LocalizedText,
    val solutionTip: LocalizedText,
    val dangerFrame: MovitPeakFrameCapture? = null,
)

@Serializable
data class MovitPerfectMoment(
    val repNumber: Int,
    val score: Float,
    val durationMs: Long,
    val motivationalMessage: LocalizedText,
    val frameCapture: MovitPeakFrameCapture? = null,
)

/** Grouped joint error analysis — mirrors legacy `ErrorAnalysisItem`. */
@Serializable
data class MovitReportErrorAnalysis(
    val errorKey: String,
    val jointCode: String,
    val jointName: LocalizedText,
    val state: String,
    val stateDisplayName: LocalizedText = LocalizedText(),
    val stateIcon: String = "⚠",
    val count: Int,
    val affectedReps: List<Int> = emptyList(),
    val message: LocalizedText = LocalizedText(),
    val tip: LocalizedText = LocalizedText(),
    val averageActualAngle: Double? = null,
    val expectedRange: AngleRange? = null,
    val bestRepAngle: Double? = null,
    val errorFrame: MovitPeakFrameCapture? = null,
    val bestRepFrame: MovitPeakFrameCapture? = null,
    val errorType: ErrorType = ErrorType.TOO_LOW,
)

/** Single rep in the post-training timeline — mirrors legacy `RepTimelineEntry`. */
@Serializable
data class MovitRepTimelineEntry(
    val repNumber: Int,
    val durationMs: Long,
    val score: Float,
    val setNumber: Int = 1,
    val isBestRep: Boolean = false,
    val isWorstRep: Boolean = false,
    val isCounted: Boolean = true,
    val worstState: String = "NORMAL",
    val status: MovitRepStatus = MovitRepStatus.GOOD,
    val errors: List<String> = emptyList(),
    val frameCapture: MovitPeakFrameCapture? = null,
    val stateDisplayName: LocalizedText = LocalizedText(),
    val stateIcon: String = "✓",
    val quality: MovitRepQuality = MovitRepQuality.CLEAN,
    val isInvalidated: Boolean = false,
    val stateMessage: LocalizedText? = null,
    val positionWarningCount: Int = 0,
    val positionErrorCount: Int = 0,
)

/** Best rep highlight — mirrors legacy `BestRepHighlight`. */
@Serializable
data class MovitBestRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
    val score: Float,
    val worstState: JointState = JointState.PERFECT,
    val quality: MovitRepQuality = MovitRepQuality.CLEAN,
    val reasons: List<LocalizedText> = emptyList(),
    val frameCapture: MovitPeakFrameCapture? = null,
)

/** Worst rep highlight — mirrors legacy `WorstRepHighlight`. */
@Serializable
data class MovitWorstRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
    val score: Float,
    val errorCount: Int = 0,
    val worstState: JointState = JointState.WARNING,
    val quality: MovitRepQuality = MovitRepQuality.NEEDS_CORRECTION,
    val primaryError: LocalizedText = LocalizedText(),
    val frameCapture: MovitPeakFrameCapture? = null,
)

/** Actionable coaching tip — mirrors legacy `ImprovementTip`. */
@Serializable
data class MovitImprovementTip(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val priority: Int = 1,
    val category: MovitTipCategory = MovitTipCategory.GENERAL,
    val icon: String = "💡",
    val severity: MovitTipSeverity = MovitTipSeverity.HELPFUL,
    val isNextFocus: Boolean = false,
    val relatedReps: List<Int> = emptyList(),
)

@Serializable
data class MovitJointHoldQuality(
    val jointCode: String,
    val jointName: LocalizedText,
    val quality: MovitHoldQuality,
    val errorCount: Int,
)

/** Hold exercise summary — mirrors legacy `HoldSummary`. */
@Serializable
data class MovitHoldSummary(
    val targetMs: Long,
    val achievedMs: Long,
    val percentage: Float,
    val formQuality: Float,
    val gracePeriodsUsed: Int = 0,
    val jointBreakdown: List<MovitJointHoldQuality> = emptyList(),
    val sampleFrames: List<MovitPeakFrameCapture> = emptyList(),
)

@Serializable
data class MovitHoldReportData(
    val targetMs: Long,
    val achievedMs: Long,
    val formQuality: Float,
    val gracePeriodsUsed: Int,
    val jointErrorMap: Map<String, Int>,
)

@Serializable
data class MovitConsistencyMetrics(
    val averageDurationMs: Long,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val fastestRep: Int,
    val slowestRep: Int,
    val variationMs: Long,
) {
    companion object {
        fun calculate(repDurations: Map<Int, Long>): MovitConsistencyMetrics? {
            if (repDurations.size < 2) return null
            val durations = repDurations.values.toList()
            val min = durations.minOrNull() ?: 0L
            val max = durations.maxOrNull() ?: 0L
            val avg = durations.average().toLong()
            val fastestRep = repDurations.entries.minByOrNull { it.value }?.key ?: 1
            val slowestRep = repDurations.entries.maxByOrNull { it.value }?.key ?: 1
            return MovitConsistencyMetrics(avg, min, max, fastestRep, slowestRep, max - min)
        }
    }
}

@Serializable
data class MovitExerciseConfigSnapshot(
    val countingMethod: CountingMethod,
    val isBilateral: Boolean,
    val hasAnySideJoints: Boolean = false,
    val supportsWeight: Boolean,
    val hasPositionChecks: Boolean,
    val metricsConfig: ReportMetricsConfig,
) {
    fun isHoldExercise(): Boolean = countingMethod == CountingMethod.HOLD

    companion object {
        fun from(
            countingMethod: CountingMethod,
            isBilateral: Boolean,
            supportsWeight: Boolean,
            hasPositionChecks: Boolean,
            metricsConfig: ReportMetricsConfig?,
            hasAnySideJoints: Boolean = false,
        ): MovitExerciseConfigSnapshot = MovitExerciseConfigSnapshot(
            countingMethod = countingMethod,
            isBilateral = isBilateral,
            hasAnySideJoints = hasAnySideJoints,
            supportsWeight = supportsWeight,
            hasPositionChecks = hasPositionChecks,
            metricsConfig = metricsConfig ?: ReportMetricsConfig(),
        )
    }
}

@Serializable
data class MovitOverallQualityScore(
    val score: Float,
    val formScore: Float,
    val safetyScore: Float,
    val controlScore: Float,
    val formWeight: Float,
    val safetyWeight: Float,
    val controlWeight: Float,
    val rating: MovitQualityRating = MovitQualityRating.fromScore(score),
) {
    companion object {
        fun calculate(
            formScore: Float,
            safetyScore: Float,
            controlScore: Float,
            isHoldExercise: Boolean = false,
        ): MovitOverallQualityScore {
            val (formWeight, safetyWeight, controlWeight) = if (isHoldExercise) {
                Triple(0.35f, 0.40f, 0.25f)
            } else {
                Triple(0.40f, 0.35f, 0.25f)
            }
            val overall = formScore * formWeight + safetyScore * safetyWeight + controlScore * controlWeight
            return MovitOverallQualityScore(
                score = overall,
                formScore = formScore,
                safetyScore = safetyScore,
                controlScore = controlScore,
                formWeight = formWeight,
                safetyWeight = safetyWeight,
                controlWeight = controlWeight,
            )
        }
    }
}

@Serializable
data class MovitSetSummary(
    val setNumber: Int,
    val repsCompleted: Int,
    val repsTarget: Int,
    val averageScore: Float,
    val durationMs: Long,
    val countedReps: Int,
    val invalidatedReps: Int,
    val weightKg: Float? = null,
    val dominantState: JointState = JointState.NORMAL,
)

/** Rich analysis sections from [MovitPostTrainingReportBuilderV2]. */
@Serializable
data class MovitPostTrainingReportAnalysis(
    val dangerAlerts: List<MovitDangerAlert> = emptyList(),
    val perfectMoments: List<MovitPerfectMoment> = emptyList(),
    val bestReps: List<MovitBestRepHighlight> = emptyList(),
    val worstRep: MovitWorstRepHighlight? = null,
    val errorAnalysis: List<MovitReportErrorAnalysis> = emptyList(),
    val repTimeline: List<MovitRepTimelineEntry> = emptyList(),
    val consistency: MovitConsistencyMetrics? = null,
    val improvementTips: List<MovitImprovementTip> = emptyList(),
    val holdSummary: MovitHoldSummary? = null,
    val heroFrame: MovitPeakFrameCapture? = null,
    val overallQuality: MovitOverallQualityScore? = null,
    val exerciseConfig: MovitExerciseConfigSnapshot? = null,
    val setSummaries: List<MovitSetSummary> = emptyList(),
)

object ReportStateDisplayConfig {
    data class DisplayInfo(val nameAr: String, val nameEn: String, val icon: String) {
        fun toLocalizedText(): LocalizedText = LocalizedText(ar = nameAr, en = nameEn)
    }

    private val displayMap = mapOf(
        JointState.PERFECT to DisplayInfo("مثالي!", "Excellent!", "⭐"),
        JointState.NORMAL to DisplayInfo("جيد", "Good", "✓"),
        JointState.PAD to DisplayInfo("مقبول", "Acceptable", "~"),
        JointState.WARNING to DisplayInfo("يحتاج تحسين", "Needs Work", "⚠"),
        JointState.DANGER to DisplayInfo("خطر - انتبه!", "Danger - Caution!", "🚨"),
        JointState.TRANSITION to DisplayInfo("انتقال", "Moving", "↔"),
    )

    fun getDisplayInfo(state: JointState): DisplayInfo =
        displayMap[state] ?: displayMap.getValue(JointState.NORMAL)
}

object ReportJointNameHelper {
    private val jointNames = mapOf(
        "left_knee" to LocalizedText("الركبة اليسرى", "Left Knee"),
        "right_knee" to LocalizedText("الركبة اليمنى", "Right Knee"),
        "left_hip" to LocalizedText("الورك الأيسر", "Left Hip"),
        "right_hip" to LocalizedText("الورك الأيمن", "Right Hip"),
        "spine" to LocalizedText("العمود الفقري", "Spine"),
    )

    private val simpleNames = mapOf(
        "knee" to LocalizedText("الركبة", "Knee"),
        "hip" to LocalizedText("الورك", "Hip"),
        "spine" to LocalizedText("العمود الفقري", "Spine"),
    )

    fun getJointName(jointCode: String): LocalizedText =
        jointNames[jointCode] ?: getSimpleJointName(jointCode)

    fun getSimpleJointName(jointCode: String): LocalizedText {
        jointNames[jointCode]?.let { return it }
        val token = jointCode.substringAfterLast('_')
        simpleNames[token]?.let { return it }
        val label = token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return LocalizedText(ar = label, en = label)
    }
}
