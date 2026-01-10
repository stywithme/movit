package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.AngleRange
import com.trainingvalidator.poc.training.models.DifficultyType
import com.trainingvalidator.poc.training.models.ErrorType
import com.trainingvalidator.poc.training.models.LocalizedText
import java.util.UUID

/**
 * PostTrainingReport - Complete report for a training session
 * 
 * Contains all data needed to display a comprehensive post-training report
 * including performance summary, best/worst reps, error analysis, timeline,
 * and improvement tips.
 */
data class PostTrainingReport(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: LocalizedText,
    val difficulty: DifficultyType,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Summary section
    val summary: PerformanceSummary,
    
    // Best moments (for motivation) - up to 3
    val bestReps: List<BestRepHighlight>,
    
    // Worst rep (for comparison with best)
    val worstRep: WorstRepHighlight?,
    
    // Error analysis - grouped by error type
    val errorAnalysis: List<ErrorAnalysisItem>,
    
    // Rep-by-rep timeline
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
     * Get the best frame for comparison (first best rep frame)
     */
    fun getBestRepFrame(): FrameCapture? = 
        frameCaptures.find { it.captureType == CaptureType.BEST_REP }
    
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
 */
data class PerformanceSummary(
    val totalReps: Int,
    val correctReps: Int,
    val incorrectReps: Int,
    val accuracy: Float,            // 0-100
    val durationMs: Long,
    val rating: PerformanceRating,
    val motivationalMessage: LocalizedText
) {
    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    fun getFormattedAccuracy(): String = "%.0f%%".format(accuracy)
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
        fun fromAccuracy(accuracy: Float): PerformanceRating = when {
            accuracy >= 90f -> EXCELLENT
            accuracy >= 75f -> GOOD
            accuracy >= 60f -> FAIR
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

// ==================== Best/Worst Rep Highlights ====================

/**
 * BestRepHighlight - Information about a perfect rep
 */
data class BestRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
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
 */
data class ErrorAnalysisItem(
    val errorKey: String,           // "left_knee:TOO_HIGH"
    val jointCode: String,
    val errorType: ErrorType,
    val count: Int,
    val affectedReps: List<Int>,
    val message: LocalizedText,     // Error description
    val tip: LocalizedText,         // How to fix
    val averageActualAngle: Double,
    val expectedRange: AngleRange,
    val errorFrame: FrameCapture?,  // User's error frame
    val bestRepFrame: FrameCapture? // User's best rep for comparison
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
}

// ==================== Rep Timeline ====================

/**
 * RepTimelineEntry - Single rep in the timeline
 */
data class RepTimelineEntry(
    val repNumber: Int,
    val status: RepStatus,
    val durationMs: Long,
    val errors: List<String>,       // Short error labels
    val isBestRep: Boolean,
    val isWorstRep: Boolean,
    val frameCapture: FrameCapture?
) {
    fun getFormattedDuration(): String {
        val seconds = durationMs / 1000.0
        return "%.1fs".format(seconds)
    }
    
    /**
     * Get short error summary (first error or count)
     */
    fun getErrorSummary(): String = when {
        errors.isEmpty() -> ""
        errors.size == 1 -> errors.first()
        else -> "${errors.first()} +${errors.size - 1}"
    }
}

/**
 * Rep status for timeline display
 */
enum class RepStatus {
    CORRECT,
    HAS_ERRORS,
    BEST_REP,
    WORST_REP,
    FAILED  // For hold exercises
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
    val isNextFocus: Boolean = false // For "Next session focus"
)

/**
 * Categories for improvement tips
 */
enum class TipCategory {
    DEPTH,          // Not going low enough
    ALIGNMENT,      // Body alignment issues
    TIMING,         // Too fast/slow
    POSITION,       // Position-based errors
    STABILITY       // Form breaking during movement
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
    BEST_REP,       // Perfect rep with no errors
    ERROR_FRAME,    // Frame when error detected
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
