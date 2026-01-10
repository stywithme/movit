package com.trainingvalidator.poc.training.report

import android.util.Log
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.engine.VisibilityStats
import com.trainingvalidator.poc.training.models.*
import java.util.UUID

/**
 * ReportGenerator - Generates PostTrainingReport from training session data
 * 
 * Converts SessionSummary and additional data into a complete report
 * with all sections: summary, best/worst reps, error analysis, timeline, tips.
 */
object ReportGenerator {
    
    private const val TAG = "ReportGenerator"
    private const val MAX_BEST_REPS = 3
    private const val MAX_TIPS = 3
    
    /**
     * Generate a complete post-training report
     * 
     * @param sessionId Unique session identifier
     * @param summary SessionSummary from TrainingEngine.stop()
     * @param exerciseConfig Exercise configuration
     * @param durationMs Actual session duration (from ViewModel)
     * @param visibilityStats Visibility statistics (optional)
     * @param cameraWarningCount Number of camera position warnings
     * @param frameCaptures List of captured frames
     * @param holdData Hold-specific data (for HOLD exercises)
     */
    fun generate(
        sessionId: String,
        summary: SessionSummary,
        exerciseConfig: ExerciseConfig,
        durationMs: Long,
        visibilityStats: VisibilityStats? = null,
        cameraWarningCount: Int = 0,
        frameCaptures: List<FrameCapture> = emptyList(),
        holdData: HoldData? = null
    ): PostTrainingReport {
        Log.d(TAG, "Generating report for ${summary.exerciseName}, ${summary.totalReps} reps")
        
        // Generate performance summary
        val performanceSummary = generateSummary(summary, durationMs)
        
        // Find best reps (no errors, optimal timing)
        val bestReps = findBestReps(summary.repDetails, frameCaptures)
        
        // Find worst rep (most errors)
        val worstRep = findWorstRep(summary.repDetails, frameCaptures)
        
        // Get best rep frame for comparison
        val bestRepFrame = frameCaptures.find { it.captureType == CaptureType.BEST_REP }
        
        // Generate error analysis
        val errorAnalysis = generateErrorAnalysis(
            repDetails = summary.repDetails,
            exerciseConfig = exerciseConfig,
            frameCaptures = frameCaptures,
            bestRepFrame = bestRepFrame
        )
        
        // Generate timeline
        val timeline = generateTimeline(
            repDetails = summary.repDetails,
            bestRepNumbers = bestReps.map { it.repNumber }.toSet(),
            worstRepNumber = worstRep?.repNumber,
            frameCaptures = frameCaptures
        )
        
        // Calculate consistency metrics
        val consistency = calculateConsistency(summary.repDetails)
        
        // Generate session quality
        val sessionQuality = generateSessionQuality(visibilityStats, cameraWarningCount)
        
        // Generate improvement tips
        val tips = generateTips(errorAnalysis, exerciseConfig)
        
        // Generate hold summary if applicable
        val holdSummary = holdData?.let { generateHoldSummary(it, frameCaptures) }
        
        return PostTrainingReport(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            exerciseId = exerciseConfig.fileName,
            exerciseName = exerciseConfig.name,
            difficulty = summary.difficulty,
            summary = performanceSummary,
            bestReps = bestReps,
            worstRep = worstRep,
            errorAnalysis = errorAnalysis,
            repTimeline = timeline,
            consistency = consistency,
            sessionQuality = sessionQuality,
            improvementTips = tips,
            frameCaptures = frameCaptures,
            holdSummary = holdSummary
        )
    }
    
    /**
     * Generate from TrainingEngine directly
     */
    fun generateFromEngine(
        engine: TrainingEngine,
        exerciseConfig: ExerciseConfig,
        sessionDurationMs: Long,
        frameCaptures: List<FrameCapture> = emptyList()
    ): PostTrainingReport {
        val summary = engine.stop()
        val visibilityStats = engine.getVisibilityStats()
        val cameraWarnings = if (engine.hasPositionChecks()) 1 else 0 // Simplified
        
        val holdData = if (engine.isHoldExercise) {
            HoldData(
                targetMs = engine.getTargetDurationMs(),
                achievedMs = engine.holdElapsedMs.value ?: 0L,
                formQuality = engine.holdFormQuality.value ?: 1f,
                gracePeriodsUsed = engine.getGracePeriodCount(),
                jointErrorMap = engine.holdJointErrorMap.value ?: emptyMap()
            )
        } else null
        
        return generate(
            sessionId = UUID.randomUUID().toString(),
            summary = summary,
            exerciseConfig = exerciseConfig,
            durationMs = sessionDurationMs,
            visibilityStats = visibilityStats,
            cameraWarningCount = cameraWarnings,
            frameCaptures = frameCaptures,
            holdData = holdData
        )
    }
    
    // ==================== Private Generators ====================
    
    private fun generateSummary(summary: SessionSummary, durationMs: Long): PerformanceSummary {
        val rating = PerformanceRating.fromAccuracy(summary.accuracy)
        
        return PerformanceSummary(
            totalReps = summary.totalReps,
            correctReps = summary.correctReps,
            incorrectReps = summary.incorrectReps,
            accuracy = summary.accuracy,
            durationMs = durationMs,
            rating = rating,
            motivationalMessage = rating.getMotivationalMessage()
        )
    }
    
    private fun findBestReps(
        repDetails: List<RepResult>,
        frameCaptures: List<FrameCapture>
    ): List<BestRepHighlight> {
        // Find reps with no errors
        val perfectReps = repDetails.filter { it.isCorrect }
        
        if (perfectReps.isEmpty()) {
            Log.d(TAG, "No perfect reps found")
            return emptyList()
        }
        
        // Sort by duration (prefer optimal timing - not too fast, not too slow)
        // For now, just take first N perfect reps
        return perfectReps.take(MAX_BEST_REPS).map { rep ->
            val frame = frameCaptures.find { 
                it.repNumber == rep.repNumber && 
                (it.captureType == CaptureType.BEST_REP || it.captureType == CaptureType.PEAK_FRAME)
            }
            
            val duration = calculateRepDuration(rep)
            
            BestRepHighlight(
                repNumber = rep.repNumber,
                durationMs = duration,
                reasons = listOf(LocalizedText(
                    ar = "أداء مثالي",
                    en = "Perfect form"
                )),
                frameCapture = frame
            )
        }
    }
    
    private fun findWorstRep(
        repDetails: List<RepResult>,
        frameCaptures: List<FrameCapture>
    ): WorstRepHighlight? {
        // Find rep with most errors
        val worstRep = repDetails
            .filter { !it.isCorrect }
            .maxByOrNull { it.getTotalErrorCount() }
            ?: return null
        
        val primaryError = worstRep.errors.firstOrNull()?.message
            ?: worstRep.positionErrors.firstOrNull()?.message
            ?: LocalizedText(ar = "خطأ في الأداء", en = "Form error")
        
        val frame = frameCaptures.find { 
            it.repNumber == worstRep.repNumber && it.captureType == CaptureType.ERROR_FRAME
        }
        
        return WorstRepHighlight(
            repNumber = worstRep.repNumber,
            durationMs = calculateRepDuration(worstRep),
            errorCount = worstRep.getTotalErrorCount(),
            primaryError = primaryError,
            frameCapture = frame
        )
    }
    
    private fun generateErrorAnalysis(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<FrameCapture>,
        bestRepFrame: FrameCapture?
    ): List<ErrorAnalysisItem> {
        // Collect all errors grouped by type
        val errorGroups = mutableMapOf<String, MutableList<Pair<Int, JointError>>>()
        
        repDetails.forEach { rep ->
            rep.errors.forEach { error ->
                val key = "${error.jointCode}:${error.errorType}"
                errorGroups.getOrPut(key) { mutableListOf() }
                    .add(Pair(rep.repNumber, error))
            }
        }
        
        // Convert to ErrorAnalysisItem, sorted by frequency
        return errorGroups.map { (key, occurrences) ->
            val sample = occurrences.first().second
            val affectedReps = occurrences.map { it.first }
            
            // Calculate average actual angle
            val avgAngle = occurrences.map { it.second.actualAngle }.average()
            
            // Find error frame for this error type
            val errorFrame = frameCaptures.find { 
                it.captureType == CaptureType.ERROR_FRAME && it.errorType == key
            }
            
            // Get tip from exercise config if available
            val tip = getTipForError(sample, exerciseConfig)
            
            ErrorAnalysisItem(
                errorKey = key,
                jointCode = sample.jointCode,
                errorType = sample.errorType,
                count = occurrences.size,
                affectedReps = affectedReps,
                message = sample.message,
                tip = tip,
                averageActualAngle = avgAngle,
                expectedRange = AngleRange(sample.expectedMin, sample.expectedMax),
                errorFrame = errorFrame,
                bestRepFrame = bestRepFrame
            )
        }.sortedByDescending { it.count }
    }
    
    private fun generateTimeline(
        repDetails: List<RepResult>,
        bestRepNumbers: Set<Int>,
        worstRepNumber: Int?,
        frameCaptures: List<FrameCapture>
    ): List<RepTimelineEntry> {
        return repDetails.map { rep ->
            val isBest = rep.repNumber in bestRepNumbers
            val isWorst = rep.repNumber == worstRepNumber
            
            val status = when {
                isBest -> RepStatus.BEST_REP
                isWorst -> RepStatus.WORST_REP
                rep.isCorrect -> RepStatus.CORRECT
                else -> RepStatus.HAS_ERRORS
            }
            
            // Get short error labels
            val errors = rep.errors.map { getShortErrorLabel(it) }
            
            // Find frame for this rep
            val frame = frameCaptures.find { it.repNumber == rep.repNumber }
            
            RepTimelineEntry(
                repNumber = rep.repNumber,
                status = status,
                durationMs = calculateRepDuration(rep),
                errors = errors,
                isBestRep = isBest,
                isWorstRep = isWorst,
                frameCapture = frame
            )
        }
    }
    
    private fun calculateConsistency(repDetails: List<RepResult>): ConsistencyMetrics? {
        if (repDetails.size < 2) return null
        
        val durations = repDetails.associate { 
            it.repNumber to calculateRepDuration(it) 
        }
        
        return ConsistencyMetrics.calculate(durations)
    }
    
    private fun generateSessionQuality(
        visibilityStats: VisibilityStats?,
        cameraWarningCount: Int
    ): SessionQuality {
        val pauseCount = visibilityStats?.totalPauseCount ?: 0
        val warningCount = visibilityStats?.totalWarningCount ?: 0
        
        val quality = SessionQuality.calculateQuality(pauseCount, cameraWarningCount)
        val suggestions = SessionQuality.generateSuggestions(pauseCount, cameraWarningCount)
        
        return SessionQuality(
            visibilityPauseCount = pauseCount,
            totalInvisibleMs = 0L,  // Not tracked in current VisibilityStats
            cameraWarningCount = cameraWarningCount + warningCount,
            overallQuality = quality,
            suggestions = suggestions
        )
    }
    
    private fun generateTips(
        errorAnalysis: List<ErrorAnalysisItem>,
        exerciseConfig: ExerciseConfig
    ): List<ImprovementTip> {
        if (errorAnalysis.isEmpty()) {
            // No errors - return encouragement
            return listOf(
                ImprovementTip(
                    id = "perfect",
                    category = TipCategory.STABILITY,
                    title = LocalizedText(ar = "ممتاز!", en = "Perfect!"),
                    description = LocalizedText(
                        ar = "حافظ على هذا الأداء الرائع!",
                        en = "Keep up this excellent performance!"
                    ),
                    priority = 1,
                    isNextFocus = false
                )
            )
        }
        
        val tips = mutableListOf<ImprovementTip>()
        
        // Top 2 most common errors
        errorAnalysis.take(2).forEachIndexed { index, error ->
            tips.add(
                ImprovementTip(
                    id = error.errorKey,
                    category = mapErrorToCategory(error),
                    title = error.message,
                    description = error.tip,
                    priority = index + 1,
                    isNextFocus = false
                )
            )
        }
        
        // Add "Next Focus" for 3rd error if exists
        if (errorAnalysis.size > 2) {
            val nextError = errorAnalysis[2]
            tips.add(
                ImprovementTip(
                    id = nextError.errorKey,
                    category = mapErrorToCategory(nextError),
                    title = nextError.message,
                    description = LocalizedText(
                        ar = "ركز على هذا في الجلسة القادمة",
                        en = "Focus on this in your next session"
                    ),
                    priority = 3,
                    isNextFocus = true
                )
            )
        }
        
        return tips
    }
    
    private fun generateHoldSummary(
        holdData: HoldData,
        frameCaptures: List<FrameCapture>
    ): HoldSummary {
        val percentage = if (holdData.targetMs > 0) {
            (holdData.achievedMs.toFloat() / holdData.targetMs.toFloat()) * 100f
        } else 0f
        
        // Convert joint error map to quality breakdown
        val jointBreakdown = holdData.jointErrorMap.map { (jointCode, errorCount) ->
            JointHoldQuality(
                jointCode = jointCode,
                jointName = LocalizedText(
                    ar = getJointNameAr(jointCode),
                    en = getJointNameEn(jointCode)
                ),
                quality = HoldQuality.fromErrorCount(errorCount),
                errorCount = errorCount
            )
        }
        
        // Get sample frames for hold
        val sampleFrames = frameCaptures.filter { it.captureType == CaptureType.HOLD_SAMPLE }
        
        return HoldSummary(
            targetMs = holdData.targetMs,
            achievedMs = holdData.achievedMs,
            percentage = percentage,
            formQuality = holdData.formQuality * 100f,
            gracePeriodsUsed = holdData.gracePeriodsUsed,
            jointBreakdown = jointBreakdown,
            sampleFrames = sampleFrames
        )
    }
    
    // ==================== Helper Methods ====================
    
    private fun calculateRepDuration(rep: RepResult): Long {
        // Sum of all phase timings
        return rep.phaseTimings.values.sum().takeIf { it > 0 } ?: 2000L // Default 2s
    }
    
    private fun getShortErrorLabel(error: JointError): String {
        val joint = error.jointCode.substringAfterLast("_")
        return when (error.errorType) {
            ErrorType.TOO_HIGH -> "$joint↑"
            ErrorType.TOO_LOW -> "$joint↓"
        }
    }
    
    private fun getTipForError(error: JointError, exerciseConfig: ExerciseConfig): LocalizedText {
        // Try to get tip from exercise config
        val poseVariant = exerciseConfig.poseVariants.firstOrNull()
        val tips = poseVariant?.feedbackMessages?.tip ?: emptyList()
        
        // Return first relevant tip or generic
        return tips.firstOrNull() ?: when (error.errorType) {
            ErrorType.TOO_HIGH -> LocalizedText(
                ar = "حاول ثني المفصل أكثر",
                en = "Try to bend more at this joint"
            )
            ErrorType.TOO_LOW -> LocalizedText(
                ar = "لا تثني المفصل كثيراً",
                en = "Don't bend too much at this joint"
            )
        }
    }
    
    private fun mapErrorToCategory(error: ErrorAnalysisItem): TipCategory {
        return when {
            error.jointCode.contains("knee") || error.jointCode.contains("hip") -> TipCategory.DEPTH
            error.jointCode.contains("spine") || error.jointCode.contains("shoulder") -> TipCategory.ALIGNMENT
            else -> TipCategory.STABILITY
        }
    }
    
    private fun getJointNameEn(jointCode: String): String {
        return when {
            jointCode.contains("knee") -> "Knee"
            jointCode.contains("hip") -> "Hip"
            jointCode.contains("elbow") -> "Elbow"
            jointCode.contains("shoulder") -> "Shoulder"
            jointCode.contains("ankle") -> "Ankle"
            jointCode.contains("spine") -> "Spine"
            else -> jointCode.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }
    
    private fun getJointNameAr(jointCode: String): String {
        return when {
            jointCode.contains("knee") -> "الركبة"
            jointCode.contains("hip") -> "الورك"
            jointCode.contains("elbow") -> "المرفق"
            jointCode.contains("shoulder") -> "الكتف"
            jointCode.contains("ankle") -> "الكاحل"
            jointCode.contains("spine") -> "العمود الفقري"
            else -> jointCode
        }
    }
}

/**
 * Hold exercise data for report generation
 */
data class HoldData(
    val targetMs: Long,
    val achievedMs: Long,
    val formQuality: Float,
    val gracePeriodsUsed: Int,
    val jointErrorMap: Map<String, Int>
)
