package com.trainingvalidator.poc.training.report

import android.util.Log
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.engine.VisibilityStats
import com.trainingvalidator.poc.training.models.*
import java.util.UUID

/**
 * ReportGenerator - Generates PostTrainingReport from training session data
 * 
 * STATE-BASED REPORT GENERATION:
 * - Uses JointState (PERFECT/NORMAL/PAD/WARNING/DANGER) for assessment
 * - Generates DANGER alerts with visual frames
 * - Celebrates PERFECT moments
 * - Uses stateMessages from exercise JSON for error descriptions
 * - Uses feedbackMessages.tips for improvement suggestions
 * - Uses feedbackMessages.motivational for encouragement
 */
object ReportGenerator {
    
    private const val TAG = "ReportGenerator"
    private const val MAX_BEST_REPS = 3
    private const val MAX_PERFECT_MOMENTS = 3
    private const val MAX_DANGER_ALERTS = 2
    private const val MAX_TIPS = 3
    
    /**
     * Generate a complete post-training report
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
        Log.d(TAG, "Generating state-based report for ${summary.exerciseName}, ${summary.totalReps} reps")
        
        // 1. Generate performance summary with state breakdown
        val performanceSummary = generateStateSummary(summary, durationMs, exerciseConfig)
        
        // 2. Generate DANGER alerts (CRITICAL - shown prominently)
        val dangerAlerts = generateDangerAlerts(summary.repDetails, exerciseConfig, frameCaptures)
        
        // 3. Generate perfect moments (for celebration)
        val perfectMoments = generatePerfectMoments(summary.repDetails, exerciseConfig, frameCaptures)
        
        // 4. Find best reps by score
        val bestReps = findBestRepsByScore(summary.repDetails, frameCaptures)
        
        // 5. Find worst rep
        val worstRep = findWorstRep(summary.repDetails, frameCaptures)
        
        // 6. Get best rep frame for comparison
        val bestRepFrame = frameCaptures.find { it.captureType == CaptureType.BEST_REP }
        
        // 7. Generate state-based error analysis
        val errorAnalysis = generateStateBasedErrorAnalysis(
            repDetails = summary.repDetails,
            exerciseConfig = exerciseConfig,
            frameCaptures = frameCaptures,
            bestRepFrame = bestRepFrame
        )
        
        // 8. Generate state-based timeline
        val timeline = generateStateTimeline(
            repDetails = summary.repDetails,
            exerciseConfig = exerciseConfig,
            bestRepNumbers = bestReps.map { it.repNumber }.toSet(),
            worstRepNumber = worstRep?.repNumber,
            frameCaptures = frameCaptures
        )
        
        // 9. Calculate consistency metrics
        val consistency = calculateConsistency(summary.repDetails)
        
        // 10. Generate session quality
        val sessionQuality = generateSessionQuality(visibilityStats, cameraWarningCount)
        
        // 11. Generate tips from exercise JSON
        val tips = generateExerciseBasedTips(errorAnalysis, exerciseConfig, dangerAlerts)
        
        // 12. Generate hold summary if applicable
        val holdSummary = holdData?.let { generateHoldSummary(it, frameCaptures) }
        
        return PostTrainingReport(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            exerciseId = exerciseConfig.fileName,
            exerciseName = exerciseConfig.name,
            summary = performanceSummary,
            dangerAlerts = dangerAlerts,
            perfectMoments = perfectMoments,
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
        val cameraWarnings = if (engine.hasPositionChecks()) 1 else 0
        
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
    
    // ==================== State-Based Summary ====================
    
    private fun generateStateSummary(
        summary: SessionSummary,
        durationMs: Long,
        exerciseConfig: ExerciseConfig
    ): PerformanceSummary {
        // Build state breakdown
        val stateBreakdown = StateBreakdown.fromMap(summary.stateBreakdown)
        
        // Determine if we should celebrate
        val shouldCelebrate = stateBreakdown.shouldCelebrate()
        
        // Get rating based on score and counted ratio (use fromScoreAndRatio directly)
        val rating = PerformanceRating.fromScoreAndRatio(
            summary.averageScore,
            summary.countedRatio
        )
        
        // Get motivational message from exercise or generate based on rating
        val motivationalMessage = selectMotivationalMessage(
            exerciseConfig = exerciseConfig,
            rating = rating,
            stateBreakdown = stateBreakdown
        )
        
        return PerformanceSummary(
            totalReps = summary.totalReps,
            durationMs = durationMs,
            rating = rating,
            motivationalMessage = motivationalMessage,
            countedReps = summary.countedReps,
            invalidatedReps = summary.invalidatedReps,
            averageScore = summary.averageScore,
            countedRatio = summary.countedRatio,
            stateBreakdown = stateBreakdown,
            shouldCelebrate = shouldCelebrate
        )
    }
    
    // ==================== DANGER Alerts ====================
    
    private fun generateDangerAlerts(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<FrameCapture>
    ): List<DangerAlert> {
        // Find reps with DANGER state
        val dangerReps = repDetails.filter { it.worstState == JointState.DANGER }
        
        if (dangerReps.isEmpty()) {
            return emptyList()
        }
        
        Log.d(TAG, "Found ${dangerReps.size} DANGER reps")
        
        return dangerReps.take(MAX_DANGER_ALERTS).mapNotNull { rep ->
            // Find the joint that caused DANGER
            val dangerError = rep.errors.firstOrNull()
            val jointCode = dangerError?.jointCode ?: return@mapNotNull null
            
            // Get tracked joint config
            val trackedJoint = getTrackedJoint(exerciseConfig, jointCode)
            
            // Get DANGER message from stateMessages
            val dangerMessage = trackedJoint?.stateMessages?.danger
                ?: LocalizedText(
                    ar = "وضعية خطيرة! انتبه لسلامتك",
                    en = "Dangerous position! Watch your form"
                )
            
            // Get tip from feedbackMessages
            val tip = getRelevantTip(exerciseConfig, jointCode)
                ?: LocalizedText(
                    ar = "تحكم في الحركة ولا تتجاوز الحدود الآمنة",
                    en = "Control the movement and stay within safe limits"
                )
            
            // Get safe range
            val safeRange = getSafeRangeForJoint(trackedJoint)
            
            // Find DANGER frame
            val dangerFrame = frameCaptures.find { 
                it.captureType == CaptureType.DANGER_FRAME && it.repNumber == rep.repNumber
            } ?: frameCaptures.find {
                it.captureType == CaptureType.ERROR_FRAME && it.repNumber == rep.repNumber
            }
            
            DangerAlert(
                repNumber = rep.repNumber,
                jointCode = jointCode,
                jointName = JointNameHelper.getSimpleJointName(jointCode),
                actualAngle = dangerError.actualAngle,
                safeRange = safeRange,
                dangerMessage = dangerMessage,
                solutionTip = tip,
                dangerFrame = dangerFrame
            )
        }
    }
    
    // ==================== Perfect Moments ====================
    
    private fun generatePerfectMoments(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<FrameCapture>
    ): List<PerfectMoment> {
        // Find reps with PERFECT state
        val perfectReps = repDetails.filter { it.worstState == JointState.PERFECT }
        
        if (perfectReps.isEmpty()) {
            return emptyList()
        }
        
        Log.d(TAG, "Found ${perfectReps.size} PERFECT reps")
        
        // Get motivational messages from exercise
        val motivationals = exerciseConfig.poseVariants.firstOrNull()
            ?.feedbackMessages?.motivational ?: emptyList()
        
        return perfectReps.take(MAX_PERFECT_MOMENTS).mapIndexed { index, rep ->
            // Get frame for this rep
            val frame = frameCaptures.find { 
                it.repNumber == rep.repNumber && 
                (it.captureType == CaptureType.BEST_REP || it.captureType == CaptureType.PEAK_FRAME)
            }
            
            // Select motivational message
            val motivational = motivationals.getOrNull(index % motivationals.size.coerceAtLeast(1))
                ?: LocalizedText(ar = "ممتاز! أداء مثالي!", en = "Excellent! Perfect form!")
            
            PerfectMoment(
                repNumber = rep.repNumber,
                score = rep.score,
                durationMs = calculateRepDuration(rep),
                motivationalMessage = motivational,
                frameCapture = frame
            )
        }
    }
    
    // ==================== Best/Worst Reps ====================
    
    private fun findBestRepsByScore(
        repDetails: List<RepResult>,
        frameCaptures: List<FrameCapture>
    ): List<BestRepHighlight> {
        // Sort by score descending
        val sortedReps = repDetails
            .filter { it.isCounted }
            .sortedByDescending { it.score }
        
        if (sortedReps.isEmpty()) {
            Log.d(TAG, "No counted reps found")
            return emptyList()
        }
        
        return sortedReps.take(MAX_BEST_REPS).map { rep ->
            val frame = frameCaptures.find { 
                it.repNumber == rep.repNumber && 
                (it.captureType == CaptureType.BEST_REP || it.captureType == CaptureType.PEAK_FRAME)
            }
            
            val displayInfo = StateDisplayConfig.getDisplayInfo(rep.worstState)
            
            BestRepHighlight(
                repNumber = rep.repNumber,
                durationMs = calculateRepDuration(rep),
                score = rep.score,
                worstState = rep.worstState,
                reasons = listOf(displayInfo.toLocalizedText()),
                frameCapture = frame
            )
        }
    }
    
    private fun findWorstRep(
        repDetails: List<RepResult>,
        frameCaptures: List<FrameCapture>
    ): WorstRepHighlight? {
        // Find rep with lowest score or DANGER/WARNING state
        val worstRep = repDetails
            .filter { !it.isCounted || it.isInvalidated }
            .minByOrNull { it.score }
            ?: repDetails.minByOrNull { it.score }
            ?: return null
        
        val primaryError = worstRep.errors.firstOrNull()?.message
            ?: worstRep.positionErrors.firstOrNull()?.message
            ?: StateDisplayConfig.getDisplayInfo(worstRep.worstState).toLocalizedText()
        
        val frame = frameCaptures.find { 
            it.repNumber == worstRep.repNumber && 
            (it.captureType == CaptureType.ERROR_FRAME || it.captureType == CaptureType.DANGER_FRAME)
        }
        
        return WorstRepHighlight(
            repNumber = worstRep.repNumber,
            durationMs = calculateRepDuration(worstRep),
            errorCount = worstRep.getTotalErrorCount(),
            worstState = worstRep.worstState,
            primaryError = primaryError,
            frameCapture = frame
        )
    }
    
    // ==================== State-Based Error Analysis ====================
    
    /**
     * Generate error analysis from ALL errors across ALL reps
     * 
     * IMPORTANT: This now collects errors from rep.errors list, not just worst state.
     * This ensures ALL joint errors are shown (primary + secondary).
     */
    private fun generateStateBasedErrorAnalysis(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<FrameCapture>,
        bestRepFrame: FrameCapture?
    ): List<ErrorAnalysisItem> {
        // Collect ALL errors from ALL reps, grouped by joint + state
        data class ErrorOccurrence(
            val repNumber: Int,
            val error: JointError,
            val actualAngle: Double
        )
        
        val errorGroups = mutableMapOf<String, MutableList<ErrorOccurrence>>()
        
        repDetails.forEach { rep ->
            // Collect from EACH error in the rep (not just worst state)
            rep.errors.forEach { error ->
                // Use the error's own state, not the rep's worst state
                val errorState = error.state
                if (errorState == JointState.WARNING || errorState == JointState.DANGER) {
                    val key = "${error.jointCode}:${errorState.name}"
                    errorGroups.getOrPut(key) { mutableListOf() }.add(
                        ErrorOccurrence(rep.repNumber, error, error.actualAngle)
                    )
                }
            }
        }
        
        Log.d(TAG, "Found ${errorGroups.size} error groups from ${repDetails.size} reps")
        
        // Get best rep for comparison
        val bestRep = repDetails.maxByOrNull { it.score }
        val bestRepAngles = bestRepFrame?.metadata?.angles ?: emptyMap()
        
        return errorGroups.map { (key, occurrences) ->
            val jointCode = key.substringBefore(":")
            val stateName = key.substringAfter(":")
            val state = try { JointState.valueOf(stateName) } catch (e: Exception) { JointState.WARNING }
            
            val affectedReps = occurrences.map { it.repNumber }.distinct()
            
            // Get display info
            val displayInfo = StateDisplayConfig.getDisplayInfo(state)
            
            // Get tracked joint for messages
            val trackedJoint = getTrackedJoint(exerciseConfig, jointCode)
            
            // Get message from stateMessages - use the error's own message if available
            val stateMessage = occurrences.firstOrNull()?.error?.message
                ?: trackedJoint?.stateMessages?.getMessage(state)
                ?: getDefaultStateMessage(state)
            
            // Get tip from feedbackMessages.tips
            val tip = getRelevantTip(exerciseConfig, jointCode)
                ?: getDefaultTip(state)
            
            // Calculate average angle from occurrences
            val avgAngle = occurrences.map { it.actualAngle }.average()
            
            // Get expected range
            val expectedRange = getSafeRangeForJoint(trackedJoint)
            
            // Best rep angle for this joint
            val bestAngle = bestRepAngles[jointCode]
            
            // Find error frame for this specific error type
            val errorFrame = frameCaptures.find { 
                (it.captureType == CaptureType.ERROR_FRAME || it.captureType == CaptureType.DANGER_FRAME) &&
                it.errorType?.contains(jointCode) == true
            } ?: frameCaptures.find {
                (it.captureType == CaptureType.ERROR_FRAME || it.captureType == CaptureType.DANGER_FRAME) &&
                it.repNumber in affectedReps
            }
            
            ErrorAnalysisItem(
                errorKey = key,
                jointCode = jointCode,
                jointName = JointNameHelper.getSimpleJointName(jointCode),
                state = state,
                stateDisplayName = displayInfo.toLocalizedText(),
                stateIcon = displayInfo.icon,
                count = occurrences.size,  // Count of occurrences, not just affected reps
                affectedReps = affectedReps,
                message = stateMessage,
                tip = tip,
                averageActualAngle = avgAngle,
                expectedRange = expectedRange,
                bestRepAngle = bestAngle,
                errorFrame = errorFrame,
                bestRepFrame = bestRepFrame
            )
        }.sortedWith(
            // Sort by: DANGER first, then by count
            compareByDescending<ErrorAnalysisItem> { it.state == JointState.DANGER }
                .thenByDescending { it.count }
        )
    }
    
    // ==================== State-Based Timeline ====================
    
    private fun generateStateTimeline(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        bestRepNumbers: Set<Int>,
        worstRepNumber: Int?,
        frameCaptures: List<FrameCapture>
    ): List<RepTimelineEntry> {
        return repDetails.map { rep ->
            val isBest = rep.repNumber in bestRepNumbers
            val isWorst = rep.repNumber == worstRepNumber
            
            val displayInfo = StateDisplayConfig.getDisplayInfo(rep.worstState)
            
            val status = RepStatus.fromState(rep.worstState, isBest, isWorst)
            
            // Get state message for non-PERFECT reps
            val stateMessage = if (rep.worstState != JointState.PERFECT) {
                val primaryJoint = rep.errors.firstOrNull()?.jointCode
                    ?: exerciseConfig.getPrimaryJoints().firstOrNull()?.joint
                
                val trackedJoint = primaryJoint?.let { getTrackedJoint(exerciseConfig, it) }
                trackedJoint?.stateMessages?.getMessage(rep.worstState)
            } else null
            
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
                frameCapture = frame,
                worstState = rep.worstState,
                stateDisplayName = displayInfo.toLocalizedText(),
                stateIcon = displayInfo.icon,
                score = rep.score,
                isCounted = rep.isCounted,
                isInvalidated = rep.isInvalidated,
                stateMessage = stateMessage
            )
        }
    }
    
    // ==================== Tips from Exercise JSON ====================
    
    private fun generateExerciseBasedTips(
        errorAnalysis: List<ErrorAnalysisItem>,
        exerciseConfig: ExerciseConfig,
        dangerAlerts: List<DangerAlert>
    ): List<ImprovementTip> {
        val tips = mutableListOf<ImprovementTip>()
        
        // Priority 1: DANGER fixes (CRITICAL)
        dangerAlerts.firstOrNull()?.let { danger ->
            tips.add(ImprovementTip(
                id = "danger_fix_${danger.jointCode}",
                category = TipCategory.SAFETY,
                icon = "🚨",
                title = LocalizedText(ar = "الأهم - لسلامتك", en = "Most Important - For Your Safety"),
                description = danger.solutionTip,
                priority = 1,
                severity = TipSeverity.CRITICAL,
                relatedReps = listOf(danger.repNumber)
            ))
        }
        
        // Priority 2: Most common WARNING
        errorAnalysis
            .filter { it.state == JointState.WARNING }
            .maxByOrNull { it.count }
            ?.let { warningError ->
                tips.add(ImprovementTip(
                    id = warningError.errorKey,
                    category = mapJointToCategory(warningError.jointCode),
                    icon = "1️⃣",
                    title = LocalizedText(ar = "نقطة التحسين الرئيسية", en = "Main Improvement Point"),
                    description = warningError.tip,
                    priority = 2,
                    severity = TipSeverity.IMPORTANT,
                    relatedReps = warningError.affectedReps
                ))
            }
        
        // Priority 3: Exercise tips from JSON
        val exerciseTips = exerciseConfig.poseVariants.firstOrNull()
            ?.feedbackMessages?.tips ?: emptyList()
        
        exerciseTips.take(2).forEachIndexed { index, tip ->
            tips.add(ImprovementTip(
                id = "exercise_tip_$index",
                category = TipCategory.GENERAL,
                icon = if (index == 0) "2️⃣" else "🎯",
                title = LocalizedText(
                    ar = if (index == 0) "نصيحة إضافية" else "تركيز الجلسة القادمة",
                    en = if (index == 0) "Additional Tip" else "Next Session Focus"
                ),
                description = tip,
                priority = 3 + index,
                severity = TipSeverity.HELPFUL,
                isNextFocus = index == 1
            ))
        }
        
        // If no tips yet, add encouragement
        if (tips.isEmpty()) {
            tips.add(ImprovementTip(
                id = "perfect",
                category = TipCategory.GENERAL,
                icon = "⭐",
                title = LocalizedText(ar = "ممتاز!", en = "Excellent!"),
                description = LocalizedText(
                    ar = "حافظ على هذا الأداء الرائع!",
                    en = "Keep up this excellent performance!"
                ),
                priority = 1,
                severity = TipSeverity.HELPFUL
            ))
        }
        
        return tips.take(MAX_TIPS)
    }
    
    // ==================== Helper Methods ====================
    
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
            totalInvisibleMs = 0L,
            cameraWarningCount = cameraWarningCount + warningCount,
            overallQuality = quality,
            suggestions = suggestions
        )
    }
    
    private fun generateHoldSummary(
        holdData: HoldData,
        frameCaptures: List<FrameCapture>
    ): HoldSummary {
        val percentage = if (holdData.targetMs > 0) {
            (holdData.achievedMs.toFloat() / holdData.targetMs.toFloat()) * 100f
        } else 0f
        
        val jointBreakdown = holdData.jointErrorMap.map { (jointCode, errorCount) ->
            JointHoldQuality(
                jointCode = jointCode,
                jointName = JointNameHelper.getJointName(jointCode),
                quality = HoldQuality.fromErrorCount(errorCount),
                errorCount = errorCount
            )
        }
        
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
    
    private fun calculateRepDuration(rep: RepResult): Long {
        return rep.phaseTimings.values.sum().takeIf { it > 0 } ?: 2000L
    }
    
    private fun getShortErrorLabel(error: JointError): String {
        val joint = error.jointCode.substringAfterLast("_")
        return when (error.errorType) {
            ErrorType.TOO_HIGH -> "$joint↑"
            ErrorType.TOO_LOW -> "$joint↓"
        }
    }
    
    private fun getTrackedJoint(exerciseConfig: ExerciseConfig, jointCode: String): TrackedJoint? {
        return exerciseConfig.poseVariants.firstOrNull()
            ?.trackedJoints
            ?.find { it.joint == jointCode }
    }
    
    private fun getRelevantTip(exerciseConfig: ExerciseConfig, jointCode: String): LocalizedText? {
        val tips = exerciseConfig.poseVariants.firstOrNull()
            ?.feedbackMessages?.tips ?: return null
        
        // Try to find a tip related to this joint
        return tips.find { tip ->
            tip.en.lowercase().contains(jointCode.replace("_", " ").lowercase()) ||
            tip.en.lowercase().contains(jointCode.substringAfter("_").lowercase())
        } ?: tips.firstOrNull()
    }
    
    private fun getSafeRangeForJoint(trackedJoint: TrackedJoint?): AngleRange {
        if (trackedJoint == null) {
            return AngleRange(0.0, 180.0)
        }
        
        // Get perfect range as safe range
        val perfectRange = trackedJoint.downRange?.perfect
            ?: trackedJoint.upRange?.perfect
            ?: trackedJoint.range?.perfect
        
        return perfectRange ?: AngleRange(0.0, 180.0)
    }
    
    private fun selectMotivationalMessage(
        exerciseConfig: ExerciseConfig,
        rating: PerformanceRating,
        stateBreakdown: StateBreakdown
    ): LocalizedText {
        val motivationals = exerciseConfig.poseVariants.firstOrNull()
            ?.feedbackMessages?.motivational ?: emptyList()
        
        return when {
            // Celebrate excellent performance
            stateBreakdown.perfectRatio >= 0.8f && stateBreakdown.dangerCount == 0 -> {
                motivationals.randomOrNull() 
                    ?: LocalizedText(ar = "ممتاز! أداء مثالي!", en = "Excellent! Perfect form!")
            }
            
            // Good performance
            rating == PerformanceRating.GOOD || rating == PerformanceRating.EXCELLENT -> {
                motivationals.randomOrNull()
                    ?: LocalizedText(ar = "عمل رائع! استمر!", en = "Great job! Keep it up!")
            }
            
            // Needs work but encouraging
            else -> rating.getMotivationalMessage()
        }
    }
    
    private fun getDefaultStateMessage(state: JointState): LocalizedText {
        return when (state) {
            JointState.PERFECT -> LocalizedText(ar = "ممتاز!", en = "Excellent!")
            JointState.NORMAL -> LocalizedText(ar = "جيد", en = "Good")
            JointState.PAD -> LocalizedText(ar = "مقبول", en = "Acceptable")
            JointState.WARNING -> LocalizedText(ar = "يحتاج تحسين", en = "Needs improvement")
            JointState.DANGER -> LocalizedText(ar = "خطر!", en = "Danger!")
            JointState.TRANSITION -> LocalizedText(ar = "انتقال", en = "Moving")
        }
    }
    
    private fun getDefaultTip(state: JointState): LocalizedText {
        return when (state) {
            JointState.DANGER -> LocalizedText(
                ar = "تحكم في الحركة ولا تتجاوز الحدود الآمنة",
                en = "Control the movement and stay within safe limits"
            )
            JointState.WARNING -> LocalizedText(
                ar = "حاول التركيز على الوضعية الصحيحة",
                en = "Try to focus on proper form"
            )
            else -> LocalizedText(
                ar = "استمر بالتدريب للتحسن",
                en = "Keep practicing to improve"
            )
        }
    }
    
    private fun mapJointToCategory(jointCode: String): TipCategory {
        return when {
            jointCode.contains("knee") || jointCode.contains("hip") -> TipCategory.DEPTH
            jointCode.contains("spine") || jointCode.contains("shoulder") -> TipCategory.ALIGNMENT
            jointCode.contains("ankle") -> TipCategory.POSITION
            else -> TipCategory.STABILITY
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
