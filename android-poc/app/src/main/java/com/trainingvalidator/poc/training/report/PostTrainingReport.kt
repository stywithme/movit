package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.AngleRange
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ErrorType
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.MetricCode
import com.google.gson.annotations.SerializedName
import com.trainingvalidator.poc.training.models.RepQuality
import com.trainingvalidator.poc.training.models.ReportMetricsConfig
import java.util.UUID

/**
 * PostTrainingReport - Complete report for one exercise execution
 * 
 * STATE-BASED REPORT STRUCTURE:
 * - Uses JointState (PERFECT/NORMAL/PAD/WARNING/DANGER) for quality assessment
 * - Provides user-friendly display names (????????/????/?????????/?????? ???????/???)
 * - Emphasizes DANGER states and celebrates PERFECT moments
 * 
 * Contains all data needed to display a comprehensive post-training report
 * including performance summary, danger alerts, perfect moments, error analysis,
 * timeline, and improvement tips.
 */
data class PostTrainingReport(
    val id: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val exerciseId: String,
    val exerciseName: LocalizedText,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Summary section (with state breakdown)
    val summary: PerformanceSummary,
    
    // DANGER alerts (critical - shown prominently if any) ????
    val dangerAlerts: List<DangerAlert> = emptyList(),
    
    // Perfect moments (for celebration) ?
    val perfectMoments: List<PerfectMoment> = emptyList(),
    
    // Best moments (for motivation) - up to 3
    val bestReps: List<BestRepHighlight>,
    
    // Worst rep (for comparison with best)
    val worstRep: WorstRepHighlight?,
    
    // Error analysis - grouped by state and joint
    val errorAnalysis: List<ErrorAnalysisItem>,
    
    // Rep-by-rep timeline (with state info)
    val repTimeline: List<RepTimelineEntry>,
    
    // Consistency metrics
    val consistency: ConsistencyMetrics?,
    
    // Capture / tracking quality indicators
    val executionQuality: ExecutionQuality,
    
    // Top improvement tips (max 2 + 1 next focus)
    val improvementTips: List<ImprovementTip>,
    
    // Captured frames
    val frameCaptures: List<FrameCapture>,
    
    // Hold-specific (null for rep-based exercises)
    val holdSummary: HoldSummary? = null,
    
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // NEW: Enhanced Report Fields (V2)
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    
    // Quick Insight - The main message at the top of the report
    val quickInsight: QuickInsight? = null,
    
    // Hero image - Best rep frame for display
    val heroFrame: FrameCapture? = null,
    
    // Performance metrics for the 3 main cards
    val performanceMetrics: EnhancedPerformanceMetrics? = null,
    
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // OVERALL QUALITY (Aggregated Score)
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    
    /** Overall quality score (0-100) - aggregates Performance, Safety, Control */
    val overallQuality: OverallQualityScore? = null,
    
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // METRICS CONFIGURATION (from ExerciseConfig)
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    
    /** Exercise configuration - used to determine which metrics to show */
    val exerciseConfig: ExerciseConfigSnapshot? = null,
    
    /** Per-set summaries (populated when exercise has multiple sets) */
    val setSummaries: List<SetSummary> = emptyList()
) {
    /**
     * Check if this is a hold exercise report
     */
    fun isHoldExercise(): Boolean = holdSummary != null
    
    /**
     * Check if there are any DANGER alerts
     */
    fun hasDangerAlerts(): Boolean = summary.invalidatedReps > 0 || dangerAlerts.isNotEmpty()
    
    /**
     * Check if this execution should be celebrated (many perfect, no danger)
     */
    fun shouldCelebrate(): Boolean = summary.shouldCelebrate
    
    /**
     * Get the best frame for comparison.
     * Priority:
     *   1. The resolved frame for the actual top-scoring rep in [bestReps] (already restricted to
     *      BOTTOM-phase captures by the generator).
     *   2. Any BEST_REP capture (promoted peak of a clean rep).
     *   3. Any PEAK_FRAME capture (raw BOTTOM frame).
     */
    fun getBestRepFrame(): FrameCapture? =
        bestReps.firstOrNull()?.frameCapture
            ?: frameCaptures.firstOrNull { it.captureType == CaptureType.BEST_REP }
            ?: frameCaptures.firstOrNull { it.captureType == CaptureType.PEAK_FRAME }
    
    /**
     * Get DANGER frame
     */
    fun getDangerFrame(): FrameCapture? =
        frameCaptures.find { it.captureType == CaptureType.DANGER_FRAME }
    
    /**
     * Get error frame for specific error type
     */
    fun getErrorFrame(errorKey: String): FrameCapture? =
        frameCaptures.find { 
            it.captureType == CaptureType.ERROR_FRAME && it.errorType == errorKey 
        }
}

// ==================== Performance Summary ====================

/**
 * PerformanceSummary - Overview of exercise-run performance
 * 
 * STATE-BASED METRICS:
 * - countedReps: Reps in PERFECT + NORMAL + PAD states
 * - invalidatedReps: Reps that reached DANGER state
 * - averageScore: Average score of all completed reps (0-100)
 * - stateBreakdown: Distribution of reps across states
 */
data class PerformanceSummary(
    val totalReps: Int,
    val durationMs: Long,
    val rating: PerformanceRating,
    val motivationalMessage: LocalizedText,
    
    // State-based metrics
    val countedReps: Int,           // PERFECT + NORMAL + PAD
    val invalidatedReps: Int,       // DANGER reps
    val averageScore: Float,        // 0-100 average of all completed reps
    val countedRatio: Float,        // countedReps / totalReps
    
    // State breakdown for visual display
    val stateBreakdown: StateBreakdown,
    
    // Celebration flag
    val shouldCelebrate: Boolean = false,
    
    // Weight & Load metrics (optional - for weighted exercises)
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val totalVolume: Float? = null,     // totalReps ? weightKg (actual workload)
    val est1RM: Float? = null,          // Estimated 1 Rep Max
    
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // KINEMATIC METRICS (calculated from frame data)
    // These are the SINGLE SOURCE OF TRUTH for metrics display
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    
    /** Average Range of Motion (0-100 percentage of target ROM) */
    val avgROM: Float? = null,
    
    /** Average Symmetry for bilateral exercises (0-100) */
    val avgSymmetry: Float? = null,
    
    /** Average Stability based on hip variance (0-100) */
    val avgStability: Float? = null,
    
    /** Form Consistency score (0-100) - how consistent form was across reps */
    val formConsistency: Float? = null,
    
    /** Fatigue Index - rep number where fatigue started (null = no fatigue) */
    val fatigueIndex: Int? = null,
    
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // NEW METRICS (V2) ??? From MetricsCalculator/MotionRecorder
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    
    /** Average Tempo [eccentric, isometric, concentric] in ms (from WorkoutExecutionMetrics) */
    val avgTempo: IntArray? = null,
    
    /** Average Velocity ?? 100 (from WorkoutExecutionMetrics) */
    val avgVelocity: Short? = null,
    
    /** Velocity Loss % (0-100) — Max VL% in execution (from WorkoutExecutionMetrics) */
    val velocityLoss: Float? = null,
    
    /** Tempo Consistency (0-100) ??? how consistent rep timing is (from WorkoutExecutionMetrics) */
    val tempoConsistency: Float? = null,
    
    /** Total TUT in ms ??? sum of actual rep durations (from WorkoutExecutionMetrics) */
    val totalTUT: Int? = null,
    
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    // POSITION CHECK STATS ??? From RepResult aggregation
    // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
    
    /** Number of reps that had at least one ERROR-severity position check violation */
    val positionErrorReps: Int = 0,
    
    /** Number of reps that had at least one WARNING-severity position check violation */
    val positionWarningReps: Int = 0,
    
    /** Number of reps that had at least one TIP-severity position check */
    val positionTipReps: Int = 0
) {
    // Legacy compatibility
    val accuracy: Float get() = countedRatio * 100f
    val correctReps: Int get() = countedReps
    val incorrectReps: Int get() = totalReps - countedReps
    val uncountedReps: Int get() = (totalReps - countedReps - invalidatedReps).coerceAtLeast(0)
    val invalidatedRatio: Float get() = if (totalReps > 0) invalidatedReps.toFloat() / totalReps else 0f
    val uncountedRatio: Float get() = if (totalReps > 0) uncountedReps.toFloat() / totalReps else 0f
    
    
    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    fun getFormattedAccuracy(): String = "%.0f%%".format(accuracy)
    fun getFormattedScore(): String = "%.0f%%".format(averageScore)
    
    // Weight helpers
    fun getFormattedWeight(): String? {
        val w = weightKg ?: return null
        return if (weightUnit == "lbs") {
            "%.0f lbs".format(w * 2.20462f)
        } else {
            "%.1f kg".format(w)
        }
    }
    
    fun getFormattedVolume(): String? {
        val v = totalVolume ?: return null
        return if (weightUnit == "lbs") {
            "%.0f lbs".format(v * 2.20462f)
        } else {
            "%.1f kg".format(v)
        }
    }
    
    fun getFormattedEst1RM(): String? {
        val e = est1RM ?: return null
        return if (weightUnit == "lbs") {
            "%.0f lbs".format(e * 2.20462f)
        } else {
            "%.1f kg".format(e)
        }
    }
}

/**
 * StateBreakdown - Distribution of reps across states
 */
data class StateBreakdown(
    val perfectCount: Int = 0,
    val normalCount: Int = 0,
    val padCount: Int = 0,
    val warningCount: Int = 0,
    val dangerCount: Int = 0
) {
    /**
     * Total counted reps (PERFECT + NORMAL + PAD)
     */
    val totalCounted: Int get() = perfectCount + normalCount + padCount
    
    /**
     * Total reps
     */
    val total: Int get() = perfectCount + normalCount + padCount + warningCount + dangerCount
    
    /**
     * Ratio of perfect reps to total counted
     */
    val perfectRatio: Float get() = 
        if (totalCounted > 0) perfectCount.toFloat() / totalCounted else 0f
    
    /**
     * Check if this execution should be celebrated
     * Celebrate if: 50%+ perfect AND no danger
     */
    fun shouldCelebrate(): Boolean = perfectRatio >= 0.5f && dangerCount == 0
    
    /**
     * Get percentage for a specific state
     */
    fun getPercentage(state: JointState): Float {
        val count = getCount(state)
        return if (total > 0) (count.toFloat() / total) * 100f else 0f
    }
    
    /**
     * Get count for a specific state
     */
    fun getCount(state: JointState): Int = when (state) {
        JointState.PERFECT -> perfectCount
        JointState.NORMAL -> normalCount
        JointState.PAD -> padCount
        JointState.WARNING -> warningCount
        JointState.DANGER -> dangerCount
        JointState.TRANSITION -> 0
    }
    
    /**
     * Get the most common state
     */
    fun getDominantState(): JointState {
        val counts = listOf(
            JointState.PERFECT to perfectCount,
            JointState.NORMAL to normalCount,
            JointState.PAD to padCount,
            JointState.WARNING to warningCount,
            JointState.DANGER to dangerCount
        )
        return counts.maxByOrNull { it.second }?.first ?: JointState.NORMAL
    }
    
    companion object {
        /**
         * Create from state map
         */
        fun fromMap(stateMap: Map<JointState, Int>): StateBreakdown {
            return StateBreakdown(
                perfectCount = stateMap[JointState.PERFECT] ?: 0,
                normalCount = stateMap[JointState.NORMAL] ?: 0,
                padCount = stateMap[JointState.PAD] ?: 0,
                warningCount = stateMap[JointState.WARNING] ?: 0,
                dangerCount = stateMap[JointState.DANGER] ?: 0
            )
        }
        
        /**
         * Empty breakdown
         */
        fun empty(): StateBreakdown = StateBreakdown()
    }
}

/**
 * Performance rating based on accuracy
 */
enum class PerformanceRating {
    EXCELLENT,   // 90%+
    GOOD,        // 75-89%
    FAIR,        // 60-74%
    NEEDS_WORK;  // <60%
    
    companion object {
        /**
         * Get rating from accuracy (legacy)
         */
        fun fromAccuracy(accuracy: Float): PerformanceRating = when {
            accuracy >= 90f -> EXCELLENT
            accuracy >= 75f -> GOOD
            accuracy >= 60f -> FAIR
            else -> NEEDS_WORK
        }
        
        /**
         * Get rating from score and counted ratio (state-based)
         * Used by ExerciseWorkoutSummary.getPerformanceRating()
         */
        fun fromScoreAndRatio(averageScore: Float, countedRatio: Float): PerformanceRating = when {
            averageScore >= 80f && countedRatio >= 0.9f -> EXCELLENT
            averageScore >= 60f && countedRatio >= 0.75f -> GOOD
            averageScore >= 40f && countedRatio >= 0.6f -> FAIR
            else -> NEEDS_WORK
        }
    }
    
    fun getMotivationalMessage(): LocalizedText = when (this) {
        EXCELLENT -> LocalizedText(
            ar = "???????! ???? ???? ????????!",
            en = "Outstanding! Nearly perfect form!"
        )
        GOOD -> LocalizedText(
            ar = "????? ????! ??????!",
            en = "Great job! Keep it up!"
        )
        FAIR -> LocalizedText(
            ar = "???????? ????! ?????????? ????????.",
            en = "Good effort! Room to improve."
        )
        NEEDS_WORK -> LocalizedText(
            ar = "?????? ??????????! ???????!",
            en = "Keep practicing! You'll get better!"
        )
    }
}

// ==================== Danger Alert (Critical) ====================

/**
 * DangerAlert - Critical alert for DANGER state occurrences ????
 * 
 * This should be shown PROMINENTLY in the report.
 * Users need to understand when they reached dangerous positions.
 */
data class DangerAlert(
    val repNumber: Int,
    val jointCode: String,
    val jointName: LocalizedText,
    val actualAngle: Double,
    val safeRange: AngleRange,
    
    // Message from stateMessages.danger in exercise JSON
    val dangerMessage: LocalizedText,
    
    // Solution tip from feedbackMessages.tips
    val solutionTip: LocalizedText,
    
    // CRITICAL: The frame showing the dangerous position
    val dangerFrame: FrameCapture?
) {
    fun getFormattedAngle(): String = "%.0f?".format(actualAngle)
    fun getSafeRangeText(): String = "%.0f? - %.0f?".format(safeRange.min, safeRange.max)
}

// ==================== Perfect Moment (Celebration) ====================

/**
 * PerfectMoment - Highlight of a PERFECT state rep ?
 * 
 * For celebration and motivation. Show these prominently!
 */
data class PerfectMoment(
    val repNumber: Int,
    val score: Float,               // 100% for perfect
    val durationMs: Long,
    
    // Motivational message from feedbackMessages.motivational
    val motivationalMessage: LocalizedText,
    
    // Frame capture for this perfect moment
    val frameCapture: FrameCapture?
) {
    fun getFormattedDuration(): String {
        val seconds = durationMs / 1000.0
        return "%.1fs".format(seconds)
    }
}

// ==================== Best/Worst Rep Highlights ====================

/**
 * BestRepHighlight - Information about a perfect rep
 */
data class BestRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
    val score: Float = 100f,        // Score for this rep
    val worstState: JointState = JointState.PERFECT,
    val quality: RepQuality = RepQuality.CLEAN,
    val reasons: List<LocalizedText>,
    val frameCapture: FrameCapture?,
    val replayClip: RepReplayClip? = null
) {
    fun getFormattedDuration(): String {
        val seconds = durationMs / 1000.0
        return "%.1fs".format(seconds)
    }
}

/**
 * WorstRepHighlight - Information about the rep with most errors
 */
data class WorstRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
    val score: Float = 0f,
    val errorCount: Int,
    val worstState: JointState = JointState.WARNING,
    val quality: RepQuality = RepQuality.NEEDS_CORRECTION,
    val primaryError: LocalizedText,
    val frameCapture: FrameCapture?,
    val replayClip: RepReplayClip? = null
) {
    fun getFormattedDuration(): String {
        val seconds = durationMs / 1000.0
        return "%.1fs".format(seconds)
    }
}

// ==================== Error Analysis ====================

/**
 * ErrorAnalysisItem - Grouped analysis of a specific error type
 * 
 * STATE-BASED ERROR ANALYSIS:
 * - Uses JointState (WARNING/DANGER) instead of ErrorType
 * - Messages come from stateMessages in exercise JSON
 * - Tips come from feedbackMessages.tips
 * - Visual comparison between error and correct form
 */
data class ErrorAnalysisItem(
    val errorKey: String,           // "left_knee:WARNING" or "left_knee:DANGER"
    val jointCode: String,
    val jointName: LocalizedText,   // "????????" / "Knee"
    
    // State info
    val state: JointState,          // WARNING or DANGER
    val stateDisplayName: LocalizedText,  // "?????? ???????" / "Needs Work"
    val stateIcon: String,          // "????" or "????"
    
    val count: Int,
    val affectedReps: List<Int>,
    
    // Messages from exercise JSON
    val message: LocalizedText,     // From stateMessages (e.g., "???? ?????? ????? ???? ?????????")
    val tip: LocalizedText,         // From feedbackMessages.tips (e.g., "???? ???? ????????")
    
    val averageActualAngle: Double,
    val expectedRange: AngleRange,
    val bestRepAngle: Double? = null,  // Angle in user's best rep (for comparison)
    
    // CRITICAL: Visual comparison frames
    val errorFrame: FrameCapture?,  // User's error frame
    val bestRepFrame: FrameCapture?, // User's best rep for comparison
    
    // Legacy compatibility
    val errorType: ErrorType = ErrorType.TOO_LOW
) {
    /**
     * Get formatted affected reps string (e.g., "#2, #4, #5")
     */
    fun getAffectedRepsText(): String = 
        affectedReps.joinToString(", ") { "#$it" }
    
    /**
     * Get formatted expected range (e.g., "80? - 90?")
     */
    fun getExpectedRangeText(): String = 
        "%.0f? - %.0f?".format(expectedRange.min, expectedRange.max)
    
    /**
     * Get formatted actual angle (e.g., "95?")
     */
    fun getActualAngleText(): String = "%.0f?".format(averageActualAngle)
    
    /**
     * Get formatted best rep angle (e.g., "75?")
     */
    fun getBestRepAngleText(): String? = bestRepAngle?.let { "%.0f?".format(it) }
    
    /**
     * Check if this is a DANGER error
     */
    fun isDanger(): Boolean = state == JointState.DANGER
    
    /**
     * Check if this is a WARNING error
     */
    fun isWarning(): Boolean = state == JointState.WARNING
}

// ==================== Rep Timeline ====================

/**
 * RepTimelineEntry - Single rep in the timeline
 * 
 * STATE-BASED TIMELINE:
 * - Shows worstState for each rep
 * - Shows score (0-100%)
 * - Shows state message for non-PERFECT reps
 * - User-friendly display names
 */
data class RepTimelineEntry(
    val repNumber: Int,
    val status: RepStatus,
    val durationMs: Long,
    val errors: List<String>,       // Short error labels
    val isBestRep: Boolean,
    val isWorstRep: Boolean,
    val frameCapture: FrameCapture?,
    
    // State-based info
    val worstState: JointState = JointState.NORMAL,
    val stateDisplayName: LocalizedText = LocalizedText(ar = "????", en = "Good"),
    val stateIcon: String = "???",
    val score: Float = 0f,          // 0-100
    val quality: RepQuality = RepQuality.CLEAN,
    val isCounted: Boolean = true,
    val isInvalidated: Boolean = false,  // DANGER
    
    // State message (shown for non-PERFECT reps)
    val stateMessage: LocalizedText? = null,

    // Position check counts (for chart + alignment metrics)
    val positionWarningCount: Int = 0,
    val positionErrorCount: Int = 0,

    // Set grouping (1-based, default 1 for single-set exercises)
    val setNumber: Int = 1
) {
    fun getFormattedDuration(): String {
        val seconds = durationMs / 1000.0
        return "%.1fs".format(seconds)
    }
    
    fun getFormattedScore(): String = "%.0f%%".format(score)
    
    /**
     * Get short error summary (first error or count)
     */
    fun getErrorSummary(): String = when {
        errors.isEmpty() -> ""
        errors.size == 1 -> errors.first()
        else -> "${errors.first()} +${errors.size - 1}"
    }
    
    /**
     * Check if this rep is perfect
     */
    fun isPerfect(): Boolean = worstState == JointState.PERFECT
    
    /**
     * Check if this rep is dangerous
     */
    fun isDanger(): Boolean = worstState == JointState.DANGER
}

// ==================== Set Summary ====================

/**
 * SetSummary - Aggregated metrics for a single set within an exercise.
 * Enables set-by-set comparison without expanding individual reps.
 */
data class SetSummary(
    val setNumber: Int,
    val repsCompleted: Int,
    val repsTarget: Int,
    val averageScore: Float,         // 0-100
    val durationMs: Long,
    val countedReps: Int,
    val invalidatedReps: Int,
    val weightKg: Float? = null,
    val dominantState: JointState = JointState.NORMAL,
    val primaryIssue: LocalizedText? = null
) {
    val completionRatio: Float get() = if (repsTarget > 0) repsCompleted.toFloat() / repsTarget else 1f

    fun getFormattedScore(): String = "%.0f%%".format(averageScore)

    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Rep status for timeline display
 */
enum class RepStatus {
    PERFECT,    // PERFECT state
    GOOD,       // NORMAL state
    ACCEPTABLE, // PAD state
    HAS_ERRORS, // WARNING state
    DANGER,     // DANGER state
    BEST_REP,   // Best rep in execution
    WORST_REP,  // Worst rep in execution
    FAILED;     // For hold exercises
    
    companion object {
        fun fromState(state: JointState, isBest: Boolean = false, isWorst: Boolean = false): RepStatus {
            return when {
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
    
    // Legacy compatibility
    val CORRECT: RepStatus get() = PERFECT
}

// ==================== Consistency Metrics ====================

/**
 * ConsistencyMetrics - Analysis of rep timing consistency
 */
data class ConsistencyMetrics(
    val averageDurationMs: Long,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val fastestRep: Int,
    val slowestRep: Int,
    val variationMs: Long           // max - min
) {
    fun getFormattedAverage(): String = formatMs(averageDurationMs)
    fun getFormattedMin(): String = formatMs(minDurationMs)
    fun getFormattedMax(): String = formatMs(maxDurationMs)
    fun getFormattedVariation(): String = "?${formatMs(variationMs / 2)}"
    
    private fun formatMs(ms: Long): String {
        val seconds = ms / 1000.0
        return "%.1fs".format(seconds)
    }
    
    companion object {
        /**
         * Calculate consistency metrics from rep durations
         * @param repDurations Map of rep number to duration in ms
         */
        fun calculate(repDurations: Map<Int, Long>): ConsistencyMetrics? {
            if (repDurations.isEmpty()) return null
            
            val durations = repDurations.values.toList()
            val min = durations.minOrNull() ?: 0L
            val max = durations.maxOrNull() ?: 0L
            val avg = durations.average().toLong()
            
            val fastestRep = repDurations.entries.minByOrNull { it.value }?.key ?: 1
            val slowestRep = repDurations.entries.maxByOrNull { it.value }?.key ?: 1
            
            return ConsistencyMetrics(
                averageDurationMs = avg,
                minDurationMs = min,
                maxDurationMs = max,
                fastestRep = fastestRep,
                slowestRep = slowestRep,
                variationMs = max - min
            )
        }
    }
}

// ==================== Execution Quality ====================

/**
 * ExecutionQuality - Indicators of tracking quality during one exercise run
 */
data class ExecutionQuality(
    val visibilityPauseCount: Int,
    val totalInvisibleMs: Long,
    val cameraWarningCount: Int,
    val overallQuality: QualityLevel,
    val suggestions: List<LocalizedText>
) {
    companion object {
        /**
         * Determine quality level based on issues
         */
        fun calculateQuality(
            pauseCount: Int, 
            cameraWarnings: Int
        ): QualityLevel = when {
            pauseCount == 0 && cameraWarnings == 0 -> QualityLevel.EXCELLENT
            pauseCount <= 1 && cameraWarnings <= 1 -> QualityLevel.GOOD
            pauseCount <= 3 && cameraWarnings <= 3 -> QualityLevel.FAIR
            else -> QualityLevel.POOR
        }
        
        /**
         * Generate suggestions based on issues
         */
        fun generateSuggestions(
            pauseCount: Int, 
            cameraWarnings: Int
        ): List<LocalizedText> {
            val suggestions = mutableListOf<LocalizedText>()
            
            if (pauseCount > 0) {
                suggestions.add(LocalizedText(
                    ar = "?????? ???????? ??? ???? ???????????? ?????? ???????????",
                    en = "Try to stay in the camera frame throughout the exercise"
                ))
            }
            
            if (cameraWarnings > 0) {
                suggestions.add(LocalizedText(
                    ar = "?????????? ????? ?????? ??????? ?????? ???? ????????",
                    en = "For better tracking, try filming from the side"
                ))
            }
            
            return suggestions
        }
    }
}

/**
 * Quality level for capture / tracking
 */
enum class QualityLevel {
    EXCELLENT,  // No issues
    GOOD,       // Minor issues
    FAIR,       // Some issues
    POOR        // Many issues
}

// ==================== Improvement Tips ====================

/**
 * ImprovementTip - Actionable suggestion for improvement
 */
data class ImprovementTip(
    val id: String,
    val category: TipCategory,
    val title: LocalizedText,
    val description: LocalizedText,
    val priority: Int,              // 1 = highest priority
    val isNextFocus: Boolean = false, // For "Next workout focus"
    val icon: String = "????",        // Tip icon
    val severity: TipSeverity = TipSeverity.HELPFUL,
    val relatedReps: List<Int> = emptyList()  // Reps this tip addresses
)

/**
 * Tip severity - affects presentation
 */
enum class TipSeverity {
    CRITICAL,   // For DANGER fixes ????
    IMPORTANT,  // For WARNING fixes ????
    HELPFUL     // General tips ????
}

/**
 * Categories for improvement tips
 */
enum class TipCategory {
    SAFETY,         // For DANGER fixes
    DEPTH,          // Not going low enough
    ALIGNMENT,      // Body alignment issues
    TIMING,         // Too fast/slow
    POSITION,       // Position-based errors
    STABILITY,      // Form breaking during movement
    GENERAL         // General tips from exercise JSON
}

// ==================== Hold Exercise Summary ====================

/**
 * HoldSummary - Summary for hold-type exercises
 */
data class HoldSummary(
    val targetMs: Long,
    val achievedMs: Long,
    val percentage: Float,          // achievedMs / targetMs * 100
    val formQuality: Float,         // 0-100
    val gracePeriodsUsed: Int,
    val jointBreakdown: List<JointHoldQuality>,
    val sampleFrames: List<FrameCapture>
) {
    fun getFormattedTarget(): String = formatMs(targetMs)
    fun getFormattedAchieved(): String = formatMs(achievedMs)
    fun getFormattedFormQuality(): String = "%.0f%%".format(formQuality)
    
    private fun formatMs(ms: Long): String {
        val seconds = ms / 1000
        return "${seconds}s"
    }
}

/**
 * JointHoldQuality - Quality assessment for a joint during hold
 */
data class JointHoldQuality(
    val jointCode: String,
    val jointName: LocalizedText,
    val quality: HoldQuality,
    val errorCount: Int
)

/**
 * Quality level for joint during hold
 */
enum class HoldQuality {
    STABLE,         // No or minimal errors
    MINOR_DRIFT,    // 1-3 corrections needed
    UNSTABLE;       // Frequent corrections
    
    companion object {
        fun fromErrorCount(count: Int): HoldQuality = when {
            count == 0 -> STABLE
            count <= 3 -> MINOR_DRIFT
            else -> UNSTABLE
        }
    }
}

// ==================== Frame Capture ====================

/**
 * FrameCapture - Captured frame during training
 */
data class FrameCapture(
    val id: String,
    val repNumber: Int,
    val phase: Phase,
    val timestamp: Long,
    val captureType: CaptureType,
    val errorType: String?,
    val frameUri: String,
    val thumbnailUri: String,
    val metadata: FrameMetadata
)

/**
 * RepReplayClip - Temporary sampled image sequence for a completed rep.
 *
 * File paths point to cache-backed media and may disappear after the report closes.
 */
data class RepReplayClip(
    val repNumber: Int,
    val frames: List<ReplayFrameRef>,
    val posterFrameUri: String? = frames.getOrNull(frames.size / 2)?.frameUri
) {
    val durationMs: Long
        get() = frames.lastOrNull()?.offsetMs ?: 0L
}

data class ReplayFrameRef(
    val frameUri: String,
    val offsetMs: Long
)

/**
 * Type of frame capture
 */
enum class CaptureType {
    BEST_REP,       // Perfect rep with no errors ?
    DANGER_FRAME,   // Frame when DANGER state detected ????
    ERROR_FRAME,    // Frame when WARNING state detected ????
    PEAK_FRAME,     // Peak of each rep (BOTTOM phase)
    HOLD_SAMPLE     // Sample during hold exercise
}

/**
 * Metadata for captured frame
 */
data class FrameMetadata(
    val angles: Map<String, Double>,
    val hasError: Boolean,
    val errorDetails: String?
)

// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
// ENHANCED REPORT MODELS (V2) - For the new report UI
// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????

/**
 * QuickInsight - The main message displayed at the top of the report
 * 
 * Types:
 * - CELEBRATION: Excellent performance (90%+ score, no danger)
 * - FOCUS_POINT: Good but has area to focus on
 * - DANGER_WARNING: Has danger alerts that need attention
 */
data class QuickInsight(
    val type: InsightType,
    val title: LocalizedText,
    val subtitle: LocalizedText,
    val actionable: LocalizedText? = null,
    val icon: String = "????"
) {
    companion object {
        fun celebration(score: Float): QuickInsight = QuickInsight(
            type = InsightType.CELEBRATION,
            title = LocalizedText(
                ar = "???? ????!",
                en = "Outstanding Performance!"
            ),
            subtitle = LocalizedText(
                ar = "????? ????? ????? ??????? ?????? ???????????",
                en = "You maintained excellent form throughout"
            ),
            actionable = LocalizedText(
                ar = "???????? ??????????: ??? ?????? ???????? ??? ???????",
                en = "Next workout: Try increasing weight or reps"
            ),
            icon = "????"
        )
        
        fun focusPoint(focusArea: LocalizedText, details: LocalizedText): QuickInsight = QuickInsight(
            type = InsightType.FOCUS_POINT,
            title = LocalizedText(
                ar = "???? ?????: ${focusArea.ar}",
                en = "Focus on: ${focusArea.en}"
            ),
            subtitle = details,
            actionable = null,
            icon = "????"
        )
        
        fun dangerWarning(jointName: LocalizedText, repCount: Int): QuickInsight = QuickInsight(
            type = InsightType.DANGER_WARNING,
            title = LocalizedText(
                ar = "???????: ${jointName.ar}",
                en = "Warning: ${jointName.en}"
            ),
            subtitle = LocalizedText(
                ar = "??? $repCount ?????? ?????? ?????? ???? ?????",
                en = "In $repCount reps, you reached an unsafe position"
            ),
            actionable = LocalizedText(
                ar = "???? ??????? ??????? ???????? ???????????",
                en = "Review the images below to understand the issue"
            ),
            icon = "????"
        )
    }
}

/**
 * Insight type determines the visual style and priority
 */
enum class InsightType {
    CELEBRATION,     // ???? Green theme - Great job!
    FOCUS_POINT,     // ???? Yellow theme - Needs attention
    DANGER_WARNING   // ???? Red theme - Safety concern
}

/**
 * EnhancedPerformanceMetrics - Grouped metrics for the 3 main cards
 * 
 * Card 1: Performance (????????) - How well is the exercise performed?
 * Card 2: Safety (?????????) - Is it safe for joints?
 * Card 3: Control (?????????) - Is the movement controlled?
 */
data class EnhancedPerformanceMetrics(
    // Card 1: Performance
    val formCard: FormMetrics,
    
    // Card 2: Safety
    val safetyCard: SafetyMetrics,
    
    // Card 3: Control
    val controlCard: ControlMetrics,
    
    // Load metrics (for weighted exercises)
    val loadMetrics: LoadMetrics? = null
)

/**
 * FormMetrics - Metrics shown inside the Performance card
 */
data class FormMetrics(
    /** Weighted group score shown on the Performance card gauge */
    val overallScore: MetricWithStatus,

    /** Pure form / state score before mixing with ROM or consistency */
    val formQuality: MetricWithStatus,

    val rom: MetricWithStatus?,           // Range of Motion
    val symmetry: MetricWithStatus?,      // Bilateral symmetry
    val formConsistency: MetricWithStatus? // How consistent across reps
) {
    fun getCardScore(): Float = overallScore.value
}

/**
 * SafetyMetrics - Combined metrics for the Safety card
 */
data class SafetyMetrics(
    val overallScore: MetricWithStatus,
    val alignmentAccuracy: MetricWithStatus?,
    val stability: MetricWithStatus?,
    val dangerCount: Int = 0
) {
    fun getCardScore(): Float = overallScore.value
    fun hasDanger(): Boolean = dangerCount > 0
}

/**
 * ControlMetrics - Combined metrics for the Control card
 */
data class ControlMetrics(
    val overallScore: MetricWithStatus,
    val tempo: TempoDisplay?,
    val totalTUT: Int?,                   // Time Under Tension in seconds (sum of rep durations)
    val fatigueIndex: Int?,               // Rep number where fatigue started
    val velocityLoss: MetricWithStatus? = null,     // Velocity Loss % (neuromuscular fatigue)
    val tempoConsistency: MetricWithStatus? = null   // Tempo consistency across reps
) {
    fun getCardScore(): Float = overallScore.value
}

/**
 * LoadMetrics - Metrics for weighted exercises
 */
data class LoadMetrics(
    val weightKg: Float,
    val weightUnit: String = "kg",
    val totalVolume: Float?,              // Total weight lifted
    val est1RM: Float?                    // Estimated 1 Rep Max
) {
    fun getFormattedWeight(): String {
        return if (weightUnit == "lbs") {
            "%.0f lbs".format(weightKg * 2.20462f)
        } else {
            "%.1f kg".format(weightKg)
        }
    }
    
    fun getFormattedVolume(): String? {
        val v = totalVolume ?: return null
        return if (weightUnit == "lbs") {
            "%.0f lbs".format(v * 2.20462f)
        } else {
            "%.1f kg".format(v)
        }
    }
    
    fun getFormattedEst1RM(): String? {
        val e = est1RM ?: return null
        return if (weightUnit == "lbs") {
            "%.0f lbs".format(e * 2.20462f)
        } else {
            "%.1f kg".format(e)
        }
    }
}

/**
 * TempoDisplay - Formatted tempo for display
 */
data class TempoDisplay(
    val eccentricMs: Int,    // Lowering phase
    val isometricMs: Int,    // Hold phase
    val concentricMs: Int,   // Lifting phase
    val displayFormat: String = "" // e.g., "2-1-2"
) {
    fun getFormattedTempo(): String {
        val ecc = (eccentricMs / 1000.0).let { if (it >= 1) "%.0f".format(it) else "%.1f".format(it) }
        val iso = (isometricMs / 1000.0).let { if (it >= 1) "%.0f".format(it) else "%.1f".format(it) }
        val con = (concentricMs / 1000.0).let { if (it >= 1) "%.0f".format(it) else "%.1f".format(it) }
        return "$ecc-$iso-$con"
    }
    
    fun getEccentricSeconds(): String = "%.1fs".format(eccentricMs / 1000.0)
    fun getIsometricSeconds(): String = "%.1fs".format(isometricMs / 1000.0)
    fun getConcentricSeconds(): String = "%.1fs".format(concentricMs / 1000.0)
}

/**
 * MetricWithStatus - A single metric with its status and display info
 */
data class MetricWithStatus(
    val value: Float,                     // Raw value (0-100 for percentages)
    val displayValue: String,             // Formatted for display (e.g., "89%", "92?")
    val status: MetricStatus,             // Visual status
    val statusLabel: LocalizedText,       // User-friendly label
    val advice: LocalizedText? = null     // Optional short advice
) {
    companion object {
        fun fromPercentage(value: Float, advice: LocalizedText? = null): MetricWithStatus {
            val status = MetricStatus.fromPercentage(value)
            return MetricWithStatus(
                value = value,
                displayValue = "%.0f%%".format(value),
                status = status,
                statusLabel = status.getLabel(),
                advice = advice
            )
        }
        
        fun fromAngle(value: Float, maxValue: Float, advice: LocalizedText? = null): MetricWithStatus {
            val percentage = (value / maxValue) * 100
            val status = MetricStatus.fromPercentage(percentage)
            return MetricWithStatus(
                value = value,
                displayValue = "%.0f?".format(value),
                status = status,
                statusLabel = status.getLabel(),
                advice = advice
            )
        }
    }
}

/**
 * MetricStatus - Visual status for metrics
 */
enum class MetricStatus {
    EXCELLENT,   // ???? 90%+ - Green
    GOOD,        // ???? 80-89% - Light Green
    FAIR,        // ???? 70-79% - Yellow
    NEEDS_WORK;  // ???? <70% - Red
    
    companion object {
        fun fromPercentage(value: Float): MetricStatus = when {
            value >= 90 -> EXCELLENT
            value >= 80 -> GOOD
            value >= 70 -> FAIR
            else -> NEEDS_WORK
        }
    }
    
    fun getLabel(): LocalizedText = when (this) {
        EXCELLENT -> LocalizedText(ar = "???????", en = "Excellent")
        GOOD -> LocalizedText(ar = "???? ?????", en = "Very Good")
        FAIR -> LocalizedText(ar = "????", en = "Good")
        NEEDS_WORK -> LocalizedText(ar = "?????? ???????", en = "Needs Work")
    }
    
    fun getColor(): Int = when (this) {
        EXCELLENT -> 0xFF4CAF50.toInt()  // Green
        GOOD -> 0xFF8BC34A.toInt()       // Light Green
        FAIR -> 0xFFFFC107.toInt()       // Yellow
        NEEDS_WORK -> 0xFFFF5252.toInt() // Red
    }
    
    fun getIcon(): String = when (this) {
        EXCELLENT -> "????"
        GOOD -> "????"
        FAIR -> "????"
        NEEDS_WORK -> "????"
    }
}

// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
// EXERCISE CONFIG SNAPSHOT - Lightweight copy of exercise config for report
// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????

/**
 * ExerciseConfigSnapshot - Minimal exercise config data needed for report display
 * 
 * This is stored with the report to determine which metrics to show
 * without needing to reload the full exercise config.
 */
data class ExerciseConfigSnapshot(
    /** Counting method determines tempo/TUT vs hold_duration */
    val countingMethod: CountingMethod,
    
    /** Whether this exercise has bilateral (paired) joints */
    val isBilateral: Boolean,

    /**
     * True when at least one tracked joint used [TrackingMode.ANY_SIDE] (symmetry only
     * from frames where both sides were visible).
     */
    val hasAnySideJoints: Boolean = false,
    
    /** Whether this exercise supports weights */
    val supportsWeight: Boolean,
    
    /** Whether this exercise has position checks (for Alignment metric) */
    val hasPositionChecks: Boolean,
    
    /** Configured report metrics */
    val metricsConfig: ReportMetricsConfig
) {
    /**
     * Check if this is a hold exercise
     */
    fun isHoldExercise(): Boolean = countingMethod == CountingMethod.HOLD
    
    /**
     * Check if a metric should be displayed.
     * 
     * The backend sends a complete excluded list (user + auto-disabled),
     * so we just need to check if the metric is in the primary/optional
     * and not in the excluded list.
     */
    fun shouldShowMetric(metric: MetricCode): Boolean {
        return metricsConfig.shouldShow(metric)
    }
    
    /**
     * Get list of metrics that should be shown as primary cards
     */
    fun getPrimaryMetrics(): List<MetricCode> {
        return metricsConfig.primary.filter { shouldShowMetric(it) }
    }
    
    /**
     * Get list of all visible metrics (primary + optional)
     */
    fun getAllVisibleMetrics(): List<MetricCode> {
        return metricsConfig.getVisibleMetrics().filter { shouldShowMetric(it) }
    }
    
    companion object {
        /**
         * Create from full ExerciseConfig
         */
        fun from(
            countingMethod: CountingMethod,
            isBilateral: Boolean,
            supportsWeight: Boolean,
            hasPositionChecks: Boolean,
            metricsConfig: ReportMetricsConfig?,
            hasAnySideJoints: Boolean = false
        ): ExerciseConfigSnapshot {
            val effectiveConfig = metricsConfig ?: MetricCode.getDefaults(
                isHold = countingMethod == CountingMethod.HOLD,
                isBilateral = isBilateral,
                supportsWeight = supportsWeight
            )
            
            return ExerciseConfigSnapshot(
                countingMethod = countingMethod,
                isBilateral = isBilateral,
                hasAnySideJoints = hasAnySideJoints,
                supportsWeight = supportsWeight,
                hasPositionChecks = hasPositionChecks,
                metricsConfig = effectiveConfig
            )
        }
    }
}

// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
// OVERALL QUALITY SCORE
// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????

/**
 * OverallQualityScore - Aggregated quality score from all metrics
 * 
 * Combines Performance, Safety, and Control scores into a single overall score.
 * Weights differ for Rep-based vs Hold-based exercises.
 */
data class OverallQualityScore(
    /** Overall quality score (0-100) */
    val score: Float,
    
    /** Performance score (0-100) - weighted movement quality / ROM / consistency */
    val formScore: Float,
    
    /** Safety score (0-100) - Alignment, Stability, Danger events */
    val safetyScore: Float,
    
    /** Control score (0-100) - Tempo, Consistency, TUT */
    val controlScore: Float,
    
    /** Weight applied to Performance (0.0-1.0) */
    val formWeight: Float,
    
    /** Weight applied to Safety (0.0-1.0) */
    val safetyWeight: Float,
    
    /** Weight applied to Control (0.0-1.0) */
    val controlWeight: Float,
    
    /** Quality rating based on score */
    val rating: QualityRating = QualityRating.fromScore(score)
) {
    /**
     * Get formatted overall score
     */
    fun getFormattedScore(): String = "${score.toInt()}%"
    
    /**
     * Get formatted breakdown
     */
    fun getFormattedBreakdown(): String {
        return "Performance: ${formScore.toInt()}% | Safety: ${safetyScore.toInt()}% | Control: ${controlScore.toInt()}%"
    }
    
    companion object {
        /**
         * Calculate overall quality from component scores
         */
        fun calculate(
            formScore: Float,
            safetyScore: Float,
            controlScore: Float,
            isHoldExercise: Boolean = false
        ): OverallQualityScore {
            // Different weights for hold vs rep exercises
            val formWeight: Float
            val safetyWeight: Float
            val controlWeight: Float
            
            if (isHoldExercise) {
                // Hold: Safety is more important (holding still)
                formWeight = 0.35f
                safetyWeight = 0.40f
                controlWeight = 0.25f
            } else {
                // Rep: Form is most important
                formWeight = 0.40f
                safetyWeight = 0.35f
                controlWeight = 0.25f
            }
            
            val overall = (
                formScore * formWeight +
                safetyScore * safetyWeight +
                controlScore * controlWeight
            )
            
            return OverallQualityScore(
                score = overall,
                formScore = formScore,
                safetyScore = safetyScore,
                controlScore = controlScore,
                formWeight = formWeight,
                safetyWeight = safetyWeight,
                controlWeight = controlWeight
            )
        }
    }
}

/**
 * Quality rating based on overall score
 */
enum class QualityRating {
    EXCELLENT,  // 90%+
    GOOD,       // 70-89%
    FAIR,       // 50-69%
    NEEDS_WORK; // <50%
    
    companion object {
        fun fromScore(score: Float): QualityRating = when {
            score >= 90 -> EXCELLENT
            score >= 70 -> GOOD
            score >= 50 -> FAIR
            else -> NEEDS_WORK
        }
    }
    
    fun getDisplayText(isArabic: Boolean): String = when (this) {
        EXCELLENT -> if (isArabic) "???????" else "Excellent"
        GOOD -> if (isArabic) "????" else "Good"
        FAIR -> if (isArabic) "?????????" else "Fair"
        NEEDS_WORK -> if (isArabic) "?????? ???????" else "Needs Work"
    }
    
    fun getIcon(): String = when (this) {
        EXCELLENT -> "????"
        GOOD -> "???"
        FAIR -> "????"
        NEEDS_WORK -> "????"
    }
}
