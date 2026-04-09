package com.trainingvalidator.poc.training.report

import android.util.Log
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.analytics.MetricsCalculator
import com.trainingvalidator.poc.training.engine.ScoreCalculator
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
 *
 * SINGLE SOURCE OF TRUTH:
 * - Uses ScoreCalculator for all score calculations
 * - Uses MetricsCalculator for fatigue/consistency metrics
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
        holdData: HoldData? = null,
        sessionMetrics: SessionMetrics? = null
    ): PostTrainingReport {
        Log.d(TAG, "Generating state-based report for ${summary.exerciseName}, ${summary.totalReps} reps")

        // Handle 0 reps case - log for debugging
        if (summary.totalReps == 0) {
            Log.w(TAG, "Session completed with 0 reps - user may not have completed full range of motion")
        }

        // 1. Generate performance summary with state breakdown
        val performanceSummary = generateStateSummary(summary, durationMs, exerciseConfig, sessionMetrics)

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

        // 11. Generate tips from exercise JSON (pass totalReps for 0-reps handling)
        val tips = generateExerciseBasedTips(errorAnalysis, exerciseConfig, dangerAlerts, summary.totalReps)

        // 12. Generate hold summary if applicable
        val holdSummary = holdData?.let { generateHoldSummary(it, frameCaptures) }

        // 13. Create exercise config snapshot for report metrics filtering
        Log.d(TAG, "Exercise reportMetrics: ${exerciseConfig.reportMetrics}")
        Log.d(TAG, "  - excluded: ${exerciseConfig.reportMetrics?.excluded}")

        val configSnapshot = ExerciseConfigSnapshot.from(
            countingMethod = exerciseConfig.countingMethod,
            isBilateral = exerciseConfig.isBilateralExercise(),
            supportsWeight = exerciseConfig.supportsWeight,
            hasPositionChecks = exerciseConfig.hasPositionChecks,
            metricsConfig = exerciseConfig.reportMetrics
        )

        Log.d(TAG, "ConfigSnapshot metricsConfig: ${configSnapshot.metricsConfig}")
        Log.d(TAG, "  - excluded: ${configSnapshot.metricsConfig.excluded}")

        // 14. Calculate Overall Quality Score
        // Uses fatigueIndex from performanceSummary (Single Source of Truth)
        val overallQuality = calculateOverallQuality(
            summary = summary,
            errorAnalysis = errorAnalysis,
            timeline = timeline,
            dangerAlerts = dangerAlerts,
            consistency = consistency,
            isHoldExercise = exerciseConfig.isHoldExercise(),
            fatigueIndex = performanceSummary.fatigueIndex,
            performanceSummary = performanceSummary
        )

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
            holdSummary = holdSummary,
            overallQuality = overallQuality,
            exerciseConfig = configSnapshot
        )
    }

    /**
     * Generate from TrainingEngine directly.
     * @param allSets When provided (multi-set session), the timeline is built from
     *               all sets' rep details with proper setNumber tagging, and
     *               SetSummary entries are generated. When null, falls back to
     *               engine.stop() single-set behavior.
     */
    fun generateFromEngine(
        engine: TrainingEngine,
        exerciseConfig: ExerciseConfig,
        sessionDurationMs: Long,
        frameCaptures: List<FrameCapture> = emptyList(),
        sessionMetrics: SessionMetrics? = null,
        weightKg: Float? = null,
        weightUnit: String = "kg",
        allSets: List<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SetMetrics>? = null
    ): PostTrainingReport {
        val baseSummary = engine.stop()

        val summary = if (weightKg != null && weightKg > 0) {
            baseSummary.copy(weightKg = weightKg, weightUnit = weightUnit)
        } else baseSummary

        val visibilityStats = engine.getVisibilityStats()
        val cameraWarnings = engine.getCameraWarningCount()

        val holdData = if (engine.isHoldExercise) {
            HoldData(
                targetMs = engine.getTargetDurationMs(),
                achievedMs = engine.holdElapsedMs.value ?: 0L,
                formQuality = engine.holdFormQuality.value ?: 1f,
                gracePeriodsUsed = engine.getGracePeriodCount(),
                jointErrorMap = engine.holdJointErrorMap.value ?: emptyMap()
            )
        } else null

        val report = generate(
            sessionId = UUID.randomUUID().toString(),
            summary = summary,
            exerciseConfig = exerciseConfig,
            durationMs = sessionDurationMs,
            visibilityStats = visibilityStats,
            cameraWarningCount = cameraWarnings,
            frameCaptures = frameCaptures,
            holdData = holdData,
            sessionMetrics = sessionMetrics
        )

        if (allSets == null || allSets.size <= 1) return report

        return enrichWithSetData(report, allSets)
    }

    /**
     * Enrich a generated report with multi-set data.
     * 
     * Because TrainingEngine is reset between sets, [report.repTimeline] only contains
     * the last set's reps. We rebuild the full timeline from the session engine's
     * [SetMetrics.repDetails] (which stores per-rep data for every set) and
     * assign global repNumbers + setNumber tags.
     */
    private fun enrichWithSetData(
        report: PostTrainingReport,
        allSets: List<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SetMetrics>
    ): PostTrainingReport {
        // Rebuild the full timeline from all sets' rep details
        val fullTimeline = mutableListOf<RepTimelineEntry>()
        var globalRepNumber = 0

        for (set in allSets) {
            for (rep in set.repDetails) {
                globalRepNumber++
                val worstState = JointState.entries.getOrElse(rep.worstState) { JointState.NORMAL }
                val displayInfo = StateDisplayConfig.getDisplayInfo(worstState)
                val status = RepStatus.fromState(worstState, isBest = false, isWorst = false)

                fullTimeline.add(RepTimelineEntry(
                    repNumber = globalRepNumber,
                    status = status,
                    durationMs = rep.durationMs,
                    errors = emptyList(),
                    isBestRep = false,
                    isWorstRep = false,
                    frameCapture = null,
                    worstState = worstState,
                    stateDisplayName = displayInfo.toLocalizedText(),
                    stateIcon = displayInfo.icon,
                    score = rep.score,
                    isCounted = rep.isCounted,
                    isInvalidated = worstState == JointState.DANGER,
                    setNumber = set.setNumber
                ))
            }
        }

        // Mark best/worst reps globally
        if (fullTimeline.isNotEmpty()) {
            val bestIdx = fullTimeline.indices.maxByOrNull { fullTimeline[it].score } ?: 0
            val worstIdx = fullTimeline.indices.minByOrNull { fullTimeline[it].score } ?: 0
            if (bestIdx != worstIdx) {
                fullTimeline[bestIdx] = fullTimeline[bestIdx].copy(isBestRep = true)
                fullTimeline[worstIdx] = fullTimeline[worstIdx].copy(isWorstRep = true)
            }
        }

        // Transfer error labels and frame captures from the original report's timeline
        // where rep numbers match (the last set's reps are in the original timeline)
        val lastSetStartRep = globalRepNumber - (allSets.lastOrNull()?.repDetails?.size ?: 0) + 1
        val originalByLocal = report.repTimeline.associateBy { it.repNumber }
        for (i in fullTimeline.indices) {
            val entry = fullTimeline[i]
            if (entry.setNumber == allSets.last().setNumber) {
                val localRepNum = entry.repNumber - lastSetStartRep + 1
                val original = originalByLocal[localRepNum]
                if (original != null) {
                    fullTimeline[i] = entry.copy(
                        errors = original.errors,
                        frameCapture = original.frameCapture,
                        stateMessage = original.stateMessage,
                        positionWarningCount = original.positionWarningCount,
                        positionErrorCount = original.positionErrorCount
                    )
                }
            }
        }

        val setSummaries = allSets.map { set ->
            val dominantState = when {
                set.repDetails.any { it.worstState == 4 } -> JointState.DANGER
                set.repDetails.any { it.worstState == 3 } -> JointState.WARNING
                set.repDetails.all { it.worstState == 0 } -> JointState.PERFECT
                else -> JointState.NORMAL
            }

            SetSummary(
                setNumber = set.setNumber,
                repsCompleted = set.repsCompleted,
                repsTarget = set.repsTarget,
                averageScore = set.formScore,
                durationMs = set.durationMs,
                countedReps = set.repDetails.count { it.isCounted },
                invalidatedReps = set.repDetails.count { it.worstState == 4 },
                weightKg = set.weightKg,
                dominantState = dominantState
            )
        }

        // Update the summary to reflect aggregated multi-set totals
        val totalReps = allSets.sumOf { it.repsCompleted }
        val totalCounted = allSets.sumOf { s -> s.repDetails.count { it.isCounted } }
        val totalInvalidated = allSets.sumOf { s -> s.repDetails.count { it.worstState == 4 } }
        val avgScore = if (totalReps > 0) {
            allSets.flatMap { it.repDetails }.map { it.score }.average().toFloat()
        } else report.summary.averageScore
        val totalDuration = allSets.sumOf { it.durationMs }
        val countedRatio = if (totalReps > 0) totalCounted.toFloat() / totalReps else 0f

        val updatedSummary = report.summary.copy(
            totalReps = totalReps,
            countedReps = totalCounted,
            invalidatedReps = totalInvalidated,
            averageScore = avgScore,
            countedRatio = countedRatio,
            durationMs = totalDuration
        )

        return report.copy(
            repTimeline = fullTimeline,
            setSummaries = setSummaries,
            summary = updatedSummary
        )
    }

    // ==================== State-Based Summary ====================

    private fun generateStateSummary(
        summary: SessionSummary,
        durationMs: Long,
        exerciseConfig: ExerciseConfig,
        sessionMetrics: SessionMetrics?
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

        // ═══════════════════════════════════════════════════════════════
        // CALCULATE METRICS ONCE (Single Source of Truth)
        // These values are stored and reused by PerformanceMetricsBuilder
        // ═══════════════════════════════════════════════════════════════

        // Extract scores from repDetails (0-100 format)
        val scores = summary.repDetails.map { it.score }

        val shouldComputeRom = exerciseConfig.shouldShowMetric(MetricCode.ROM)
        val shouldComputeFormConsistency = exerciseConfig.shouldShowMetric(MetricCode.FORM_CONSISTENCY)
        val shouldComputeFatigueIndex = exerciseConfig.shouldShowMetric(MetricCode.FATIGUE_INDEX)

        // Form Consistency - using MetricsCalculator (Single Source of Truth)
        val formConsistency = if (shouldComputeFormConsistency && scores.size >= 4) {
            MetricsCalculator.calculateFormConsistencyFromScores(scores)?.let { it / 10f }
        } else null

        // Fatigue Index - using MetricsCalculator (Single Source of Truth)
        val fatigueIndex = if (shouldComputeFatigueIndex && scores.size >= 4) {
            MetricsCalculator.calculateFatigueIndexFromScores(scores)?.toInt()
        } else null

        // Average ROM - prefer real ROM from MotionRecorder metrics when available
        val avgROM = if (shouldComputeRom) {
            val romFromSession = calculateAverageRomFromSession(sessionMetrics, exerciseConfig)
            romFromSession ?: if (scores.isNotEmpty()) scores.average().toFloat() else null
        } else null

        // ═══════════════════════════════════════════════════════════════
        // KINEMATIC & V2 METRICS — Extract from SessionMetrics
        // ═══════════════════════════════════════════════════════════════

        // Symmetry (0-1000 scale → 0-100%)
        val avgSymmetry = sessionMetrics?.avgSymmetry?.let { it / 10f }

        // Trunk Stability (0-1000 scale → 0-100%)
        val avgStability = sessionMetrics?.avgStability?.let { it / 10f }

        val avgTempo = sessionMetrics?.avgTempo
        val avgVelocity = sessionMetrics?.avgVelocity
        val velocityLoss = sessionMetrics?.velocityLoss?.let { it / 10f } // Convert ×10 to %
        val tempoConsistency = sessionMetrics?.tempoConsistency?.let { it / 10f } // Convert ×10 to %
        val totalTUT = sessionMetrics?.totalTUT

        // Position Check stats — aggregate from repDetails
        val positionErrorReps = summary.repDetails.count { it.positionErrors.isNotEmpty() }
        val positionWarningReps = summary.repDetails.count { it.positionWarningCount > 0 }
        val positionTipReps = summary.repDetails.count { it.positionTipCount > 0 }

        // ═══════════════════════════════════════════════════════════════
        // WEIGHT & LOAD (from SessionSummary)
        // ═══════════════════════════════════════════════════════════════
        val weightKg = summary.weightKg
        val weightUnit = summary.weightUnit
        val totalVolume = if (weightKg != null && weightKg > 0) {
            weightKg * summary.totalReps
        } else null
        val est1RM = if (weightKg != null && weightKg > 0 && summary.countedReps > 0) {
            MetricsCalculator.calculateEst1RM(weightKg, summary.countedReps)
        } else null

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
            shouldCelebrate = shouldCelebrate,
            weightKg = weightKg,
            weightUnit = weightUnit,
            totalVolume = totalVolume,
            est1RM = est1RM,
            avgROM = avgROM,
            avgSymmetry = avgSymmetry,
            avgStability = avgStability,
            formConsistency = formConsistency,
            fatigueIndex = fatigueIndex,
            avgTempo = avgTempo,
            avgVelocity = avgVelocity,
            velocityLoss = velocityLoss,
            tempoConsistency = tempoConsistency,
            totalTUT = totalTUT,
            positionErrorReps = positionErrorReps,
            positionWarningReps = positionWarningReps,
            positionTipReps = positionTipReps
        )
    }

    /**
     * Convert session ROM (degrees × 10) to percentage of target ROM.
     */
    private fun calculateAverageRomFromSession(
        sessionMetrics: SessionMetrics?,
        exerciseConfig: ExerciseConfig
    ): Float? {
        val avgRomDegrees = sessionMetrics?.avgRom?.toInt()?.let { it / 10f } ?: return null
        val targetRomDegrees = getTargetRomDegrees(exerciseConfig) ?: return null
        if (targetRomDegrees <= 0f) return null
        return ((avgRomDegrees / targetRomDegrees) * 100f).coerceIn(0f, 100f)
    }

    /**
     * Target ROM is derived from primary joint up/down ranges.
     */
    private fun getTargetRomDegrees(exerciseConfig: ExerciseConfig): Float? {
        val primaryJoint = exerciseConfig.getPrimaryJoints().firstOrNull { it.hasStateUpDownRanges() }
            ?: return null
        val upRange = primaryJoint.getStateUpRange()
        val downRange = primaryJoint.getStateDownRange()
        val maxAngle = maxOf(upRange.effectiveMax, downRange.effectiveMax)
        val minAngle = minOf(upRange.effectiveMin, downRange.effectiveMin)
        val target = (maxAngle - minAngle).toFloat()
        return if (target > 0f) target else null
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
            // Use the actual DANGER error when available (not just first error in rep).
            val dangerError = rep.errors.firstOrNull { it.state == JointState.DANGER }
                ?: rep.errors.maxByOrNull { it.state.priority }
            val jointCode = dangerError?.jointCode ?: return@mapNotNull null

            // Get tracked joint config
            val trackedJoint = getTrackedJoint(exerciseConfig, jointCode)

            // Get DANGER message from stateMessages
            val dangerMessage = trackedJoint?.stateMessages?.getMessage(JointState.DANGER)
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

            // Prefer exact range captured during validation, fallback to config state range.
            val safeRange = getRangeFromError(dangerError)
                ?: getSafeRangeForJoint(
                    trackedJoint = trackedJoint,
                    state = JointState.DANGER,
                    actualAngle = dangerError.actualAngle
                )

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
        // Sort by score descending, then by fewest issues (position warnings/errors)
        val sortedReps = repDetails
            .filter { it.isCounted }
            .sortedWith(compareByDescending<RepResult> { it.score }
                .thenBy { it.positionWarningCount + it.positionErrors.size }
                .thenBy { it.errors.size })

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
        if (repDetails.isEmpty()) return null

        // Compute median rep duration to detect timing anomalies
        val durations = repDetails.map { calculateRepDuration(it) }.sorted()
        val medianDuration = if (durations.size >= 2) {
            durations[durations.size / 2]
        } else {
            durations.firstOrNull() ?: 0L
        }

        // Composite "badness" score: lower is worse, higher is better
        // Start from rep score, then subtract penalties
        val worstRep = repDetails.minByOrNull { rep ->
            val baseFitness = rep.score.toDouble()

            // Penalty for position errors (most severe)
            val errorPenalty = rep.positionErrors.size * 20.0 +
                    rep.errors.size * 15.0

            // Penalty for position warnings
            val warningPenalty = rep.positionWarningCount * 5.0

            // Penalty for timing anomaly (duration deviating from median by > 50%)
            val repDuration = calculateRepDuration(rep)
            val timingPenalty = if (medianDuration > 0) {
                val deviation = kotlin.math.abs(repDuration - medianDuration).toDouble() / medianDuration
                if (deviation > 0.5) deviation * 10.0 else 0.0
            } else 0.0

            baseFitness - errorPenalty - warningPenalty - timingPenalty
        } ?: return null

        val primaryError = worstRep.errors.firstOrNull()?.message
            ?: worstRep.positionErrors.firstOrNull()?.message
            ?: StateDisplayConfig.getDisplayInfo(worstRep.worstState).toLocalizedText()

        val frame = frameCaptures.find {
            it.repNumber == worstRep.repNumber &&
                (it.captureType == CaptureType.ERROR_FRAME || it.captureType == CaptureType.DANGER_FRAME)
        } ?: frameCaptures.find { it.repNumber == worstRep.repNumber }

        return WorstRepHighlight(
            repNumber = worstRep.repNumber,
            durationMs = calculateRepDuration(worstRep),
            errorCount = worstRep.getTotalErrorCount()
                + worstRep.positionWarningCount,     // Include warnings in count
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

            // Get expected range (prefer exact ranges captured during training).
            val expectedRangeFromErrors = occurrences
                .mapNotNull { getRangeFromError(it.error) }
                .groupingBy { Pair(it.min, it.max) }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?.let { AngleRange(it.first, it.second) }

            val expectedRange = expectedRangeFromErrors
                ?: getSafeRangeForJoint(trackedJoint, state, avgAngle)

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
                stateMessage = stateMessage,
                positionWarningCount = rep.positionWarningCount,
                positionErrorCount = rep.positionErrors.size
            )
        }
    }

    // ==================== Tips from Exercise JSON ====================

    private fun generateExerciseBasedTips(
        errorAnalysis: List<ErrorAnalysisItem>,
        exerciseConfig: ExerciseConfig,
        dangerAlerts: List<DangerAlert>,
        totalReps: Int = -1  // Pass -1 to skip 0-reps handling
    ): List<ImprovementTip> {
        val tips = mutableListOf<ImprovementTip>()

        // Priority 0: Handle 0 reps case - provide guidance
        if (totalReps == 0) {
            val isHoldExercise = exerciseConfig.isHoldExercise()

            tips.add(ImprovementTip(
                id = "no_reps_tip",
                category = TipCategory.GENERAL,
                icon = "📐",
                title = if (isHoldExercise) {
                    LocalizedText(ar = "الوصول للوضع الصحيح", en = "Reach the Correct Position")
                } else {
                    LocalizedText(ar = "المدى الحركي الكامل", en = "Full Range of Motion")
                },
                description = if (isHoldExercise) {
                    LocalizedText(
                        ar = "تأكد من الوصول للوضع المطلوب والثبات فيه",
                        en = "Make sure to reach and hold the required position"
                    )
                } else {
                    LocalizedText(
                        ar = "لإكمال العدة، يجب النزول للوضع السفلي ثم العودة للوضع الأول بالكامل",
                        en = "To complete a rep, go down to the bottom position and fully return to start"
                    )
                },
                priority = 1,
                severity = TipSeverity.IMPORTANT
            ))

            tips.add(ImprovementTip(
                id = "camera_position_tip",
                category = TipCategory.POSITION,
                icon = "📹",
                title = LocalizedText(ar = "وضع الكاميرا", en = "Camera Position"),
                description = LocalizedText(
                    ar = "تأكد من أن جسمك بالكامل ظاهر في الكاميرا من الجانب",
                    en = "Ensure your full body is visible in the camera from the side"
                ),
                priority = 2,
                severity = TipSeverity.HELPFUL
            ))

            return tips
        }

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

    private fun getRangeFromError(error: JointError?): AngleRange? {
        if (error == null) return null
        if (!error.expectedMin.isFinite() || !error.expectedMax.isFinite()) return null
        if (error.expectedMin > error.expectedMax) return null
        return AngleRange(error.expectedMin, error.expectedMax)
    }

    private fun getSafeRangeForJoint(
        trackedJoint: TrackedJoint?,
        state: JointState,
        actualAngle: Double? = null
    ): AngleRange {
        if (trackedJoint == null) {
            return AngleRange(0.0, 180.0)
        }

        val activeRanges = resolveActiveRangesForAngle(trackedJoint, actualAngle)

        return activeRanges?.let { getRangeForState(it, state) }
            ?: trackedJoint.range?.let { getRangeForState(it, state) }
            ?: trackedJoint.upRange?.let { getRangeForState(it, state) }
            ?: trackedJoint.downRange?.let { getRangeForState(it, state) }
            ?: AngleRange(0.0, 180.0)
    }

    private fun resolveActiveRangesForAngle(
        trackedJoint: TrackedJoint,
        actualAngle: Double?
    ): StateRanges? {
        trackedJoint.range?.let { return it }
        if (!trackedJoint.hasStateUpDownRanges()) return null

        if (actualAngle == null) {
            return trackedJoint.upRange ?: trackedJoint.downRange
        }

        return when (trackedJoint.determineZoneType(actualAngle)) {
            ZoneType.UP_ZONE -> trackedJoint.upRange
            ZoneType.DOWN_ZONE -> trackedJoint.downRange
            ZoneType.TRANSITION -> {
                val upRange = trackedJoint.upRange
                val downRange = trackedJoint.downRange
                when {
                    upRange == null -> downRange
                    downRange == null -> upRange
                    kotlin.math.abs(actualAngle - upRange.effectiveMin) <= kotlin.math.abs(actualAngle - downRange.effectiveMax) -> upRange
                    else -> downRange
                }
            }
        }
    }

    private fun getRangeForState(stateRanges: StateRanges, state: JointState): AngleRange {
        return when (state) {
            JointState.PERFECT -> stateRanges.perfect
            JointState.NORMAL -> stateRanges.normal ?: stateRanges.perfect
            JointState.PAD -> stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
            JointState.WARNING -> stateRanges.warning ?: stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
            JointState.DANGER -> stateRanges.danger ?: stateRanges.warning ?: stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
            JointState.TRANSITION -> AngleRange(stateRanges.effectiveMin, stateRanges.effectiveMax)
        }
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

    // ═══════════════════════════════════════════════════════════════
    // OVERALL QUALITY CALCULATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate overall quality score from report components
     *
     * This combines Form, Safety, and Control scores into a single metric.
     *
     * @param fatigueIndex Pre-calculated fatigue index for Control score (Single Source of Truth)
     */
    private fun calculateOverallQuality(
        summary: SessionSummary,
        errorAnalysis: List<ErrorAnalysisItem>,
        timeline: List<RepTimelineEntry>,
        dangerAlerts: List<DangerAlert>,
        consistency: ConsistencyMetrics?,
        isHoldExercise: Boolean,
        fatigueIndex: Int?,
        performanceSummary: PerformanceSummary? = null
    ): OverallQualityScore {

        // 1. Calculate Form Score
        val formScore = calculateFormScoreForOverall(summary, timeline)

        // 2. Calculate Safety Score
        val safetyScore = calculateSafetyScoreForOverall(errorAnalysis, summary.invalidatedReps, summary.totalReps)

        // 3. Calculate Control Score (uses V2 metrics when available)
        val controlScore = calculateControlScoreForOverall(
            timeline = timeline,
            consistency = consistency,
            fatigueIndex = fatigueIndex,
            tempoConsistency = performanceSummary?.tempoConsistency,
            velocityLoss = performanceSummary?.velocityLoss,
            formConsistency = performanceSummary?.formConsistency
        )

        // 4. Calculate Overall using OverallQualityScore
        return OverallQualityScore.calculate(
            formScore = formScore,
            safetyScore = safetyScore,
            controlScore = controlScore,
            isHoldExercise = isHoldExercise
        )
    }

    /**
     * Calculate Form Score from summary and timeline
     *
     * Uses ScoreCalculator rates for consistency (Single Source of Truth).
     */
    private fun calculateFormScoreForOverall(
        summary: SessionSummary,
        timeline: List<RepTimelineEntry>
    ): Float {
        // Use average score from timeline if available
        if (timeline.isNotEmpty()) {
            val avgScore = timeline.map { it.score }.average().toFloat()
            return avgScore
        }

        // Fallback: calculate from state breakdown using ScoreCalculator rates
        val breakdownMap = summary.stateBreakdown
        val perfectCount = breakdownMap[JointState.PERFECT] ?: 0
        val normalCount = breakdownMap[JointState.NORMAL] ?: 0
        val padCount = breakdownMap[JointState.PAD] ?: 0
        val warningCount = breakdownMap[JointState.WARNING] ?: 0
        val dangerCount = breakdownMap[JointState.DANGER] ?: 0

        val total = (perfectCount + normalCount + padCount + warningCount + dangerCount).toFloat()
        if (total == 0f) return 0f

        // Use ScoreCalculator rates (Single Source of Truth)
        return (
            perfectCount * ScoreCalculator.getScoreRate(JointState.PERFECT) +
            normalCount * ScoreCalculator.getScoreRate(JointState.NORMAL) +
            padCount * ScoreCalculator.getScoreRate(JointState.PAD) +
            warningCount * ScoreCalculator.getScoreRate(JointState.WARNING) +
            dangerCount * ScoreCalculator.getScoreRate(JointState.DANGER)
        ) / total
    }

    /**
     * Calculate Safety Score from error analysis and danger rep count
     */
    private fun calculateSafetyScoreForOverall(
        errorAnalysis: List<ErrorAnalysisItem>,
        dangerRepCount: Int,
        totalReps: Int
    ): Float {
        if (totalReps == 0) return 100f

        // Count warning and danger events
        val warningEvents = errorAnalysis.filter { it.state == JointState.WARNING }.sumOf { it.count }
        val dangerEvents = dangerRepCount.coerceAtLeast(0)

        // Calculate penalty
        val warningPenalty = (warningEvents.toFloat() / totalReps) * 30f  // Max 30% penalty for warnings
        val dangerPenalty = (dangerEvents.toFloat() / totalReps) * 50f    // Max 50% penalty for dangers

        return (100f - warningPenalty - dangerPenalty).coerceIn(0f, 100f)
    }

    /**
     * Calculate Control Score from timeline, consistency, and fatigue
     *
     * UNIFIED LOGIC: Same calculation as PerformanceMetricsBuilder.calculateControlScore
     * to ensure consistency between OverallQuality and Control card.
     *
     * @param timeline Rep timeline entries
     * @param consistency Timing consistency metrics
     * @param fatigueIndex Pre-calculated fatigue index (Single Source of Truth)
     */
    private fun calculateControlScoreForOverall(
        timeline: List<RepTimelineEntry>,
        consistency: ConsistencyMetrics?,
        fatigueIndex: Int?,
        tempoConsistency: Float? = null,
        velocityLoss: Float? = null,
        formConsistency: Float? = null
    ): Float {
        return PerformanceMetricsBuilder.calculateControlScoreValue(
            totalReps = timeline.size,
            consistency = consistency,
            fatigueIndex = fatigueIndex,
            tempoConsistency = tempoConsistency,
            velocityLoss = velocityLoss,
            formConsistency = formConsistency
        )
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
