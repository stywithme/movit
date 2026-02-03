package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.AngleRange
import com.trainingvalidator.poc.training.models.ErrorType
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import java.util.UUID

/**
 * PostTrainingReport - Complete report for a training session
 * 
 * STATE-BASED REPORT STRUCTURE:
 * - Uses JointState (PERFECT/NORMAL/PAD/WARNING/DANGER) for quality assessment
 * - Provides user-friendly display names (مثالي/جيد/مقبول/يحتاج تحسين/خطر)
 * - Emphasizes DANGER states and celebrates PERFECT moments
 * 
 * Contains all data needed to display a comprehensive post-training report
 * including performance summary, danger alerts, perfect moments, error analysis,
 * timeline, and improvement tips.
 */
data class PostTrainingReport(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: LocalizedText,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Summary section (with state breakdown)
    val summary: PerformanceSummary,
    
    // DANGER alerts (critical - shown prominently if any) 🚨
    val dangerAlerts: List<DangerAlert> = emptyList(),
    
    // Perfect moments (for celebration) ⭐
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
    
    // Session quality indicators
    val sessionQuality: SessionQuality,
    
    // Top improvement tips (max 2 + 1 next focus)
    val improvementTips: List<ImprovementTip>,
    
    // Captured frames
    val frameCaptures: List<FrameCapture>,
    
    // Hold-specific (null for rep-based exercises)
    val holdSummary: HoldSummary? = null
) {
    /**
     * Check if this is a hold exercise report
     */
    fun isHoldExercise(): Boolean = holdSummary != null
    
    /**
     * Check if there are any DANGER alerts
     */
    fun hasDangerAlerts(): Boolean = dangerAlerts.isNotEmpty()
    
    /**
     * Check if session should be celebrated (many perfect, no danger)
     */
    fun shouldCelebrate(): Boolean = summary.shouldCelebrate
    
    /**
     * Get the best frame for comparison (first best rep frame)
     */
    fun getBestRepFrame(): FrameCapture? = 
        frameCaptures.find { it.captureType == CaptureType.BEST_REP }
    
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
 * PerformanceSummary - Overview of training session performance
 * 
 * STATE-BASED METRICS:
 * - countedReps: Reps in PERFECT + NORMAL + PAD states
 * - invalidatedReps: Reps that reached DANGER state
 * - averageScore: Average score of counted reps (0-100)
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
    val averageScore: Float,        // 0-100 average of counted reps
    val countedRatio: Float,        // countedReps / totalReps
    
    // State breakdown for visual display
    val stateBreakdown: StateBreakdown,
    
    // Celebration flag
    val shouldCelebrate: Boolean = false,
    
    // Weight & Load metrics (optional - for weighted exercises)
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val totalVolume: Float? = null,     // countedReps × weightKg
    val est1RM: Float? = null           // Estimated 1 Rep Max
) {
    // Legacy compatibility
    val accuracy: Float get() = countedRatio * 100f
    val correctReps: Int get() = countedReps
    val incorrectReps: Int get() = totalReps - countedReps
    
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
     * Check if session should be celebrated
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
         * Used by SessionSummary.getPerformanceRating()
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
            ar = "ممتاز! أداء شبه مثالي!",
            en = "Outstanding! Nearly perfect form!"
        )
        GOOD -> LocalizedText(
            ar = "عمل رائع! استمر!",
            en = "Great job! Keep it up!"
        )
        FAIR -> LocalizedText(
            ar = "مجهود جيد! يمكنك التحسن.",
            en = "Good effort! Room to improve."
        )
        NEEDS_WORK -> LocalizedText(
            ar = "استمر بالتدريب! ستتحسن!",
            en = "Keep practicing! You'll get better!"
        )
    }
}

// ==================== Danger Alert (Critical) ====================

/**
 * DangerAlert - Critical alert for DANGER state occurrences 🚨
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
    fun getFormattedAngle(): String = "%.0f°".format(actualAngle)
    fun getSafeRangeText(): String = "%.0f° - %.0f°".format(safeRange.min, safeRange.max)
}

// ==================== Perfect Moment (Celebration) ====================

/**
 * PerfectMoment - Highlight of a PERFECT state rep ⭐
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
    val reasons: List<LocalizedText>,
    val frameCapture: FrameCapture?
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
    val errorCount: Int,
    val worstState: JointState = JointState.WARNING,
    val primaryError: LocalizedText,
    val frameCapture: FrameCapture?
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
    val jointName: LocalizedText,   // "الركبة" / "Knee"
    
    // State info
    val state: JointState,          // WARNING or DANGER
    val stateDisplayName: LocalizedText,  // "يحتاج تحسين" / "Needs Work"
    val stateIcon: String,          // "⚠️" or "🚨"
    
    val count: Int,
    val affectedReps: List<Int>,
    
    // Messages from exercise JSON
    val message: LocalizedText,     // From stateMessages (e.g., "أنت تنزل أكثر من اللازم")
    val tip: LocalizedText,         // From feedbackMessages.tips (e.g., "ادفع من كعبيك")
    
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
     * Get formatted expected range (e.g., "80° - 90°")
     */
    fun getExpectedRangeText(): String = 
        "%.0f° - %.0f°".format(expectedRange.min, expectedRange.max)
    
    /**
     * Get formatted actual angle (e.g., "95°")
     */
    fun getActualAngleText(): String = "%.0f°".format(averageActualAngle)
    
    /**
     * Get formatted best rep angle (e.g., "75°")
     */
    fun getBestRepAngleText(): String? = bestRepAngle?.let { "%.0f°".format(it) }
    
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
    val stateDisplayName: LocalizedText = LocalizedText(ar = "جيد", en = "Good"),
    val stateIcon: String = "✓",
    val score: Float = 0f,          // 0-100
    val isCounted: Boolean = true,
    val isInvalidated: Boolean = false,  // DANGER
    
    // State message (shown for non-PERFECT reps)
    val stateMessage: LocalizedText? = null
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

/**
 * Rep status for timeline display
 */
enum class RepStatus {
    PERFECT,    // PERFECT state
    GOOD,       // NORMAL state
    ACCEPTABLE, // PAD state
    HAS_ERRORS, // WARNING state
    DANGER,     // DANGER state
    BEST_REP,   // Best rep in session
    WORST_REP,  // Worst rep in session
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
    fun getFormattedVariation(): String = "±${formatMs(variationMs / 2)}"
    
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

// ==================== Session Quality ====================

/**
 * SessionQuality - Indicators of tracking quality during session
 */
data class SessionQuality(
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
                    ar = "حاول البقاء في إطار الكاميرا طوال التمرين",
                    en = "Try to stay in the camera frame throughout the exercise"
                ))
            }
            
            if (cameraWarnings > 0) {
                suggestions.add(LocalizedText(
                    ar = "للحصول على نتائج أفضل، صوّر من الجانب",
                    en = "For better tracking, try filming from the side"
                ))
            }
            
            return suggestions
        }
    }
}

/**
 * Quality level for session
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
    val isNextFocus: Boolean = false, // For "Next session focus"
    val icon: String = "💡",        // Tip icon
    val severity: TipSeverity = TipSeverity.HELPFUL,
    val relatedReps: List<Int> = emptyList()  // Reps this tip addresses
)

/**
 * Tip severity - affects presentation
 */
enum class TipSeverity {
    CRITICAL,   // For DANGER fixes 🚨
    IMPORTANT,  // For WARNING fixes ⚠️
    HELPFUL     // General tips 💡
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
 * Type of frame capture
 */
enum class CaptureType {
    BEST_REP,       // Perfect rep with no errors ⭐
    DANGER_FRAME,   // Frame when DANGER state detected 🚨
    ERROR_FRAME,    // Frame when WARNING state detected ⚠️
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
