package com.movit.core.training.report

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.StateRanges
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.TrackingMode
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.RepResult
import com.movit.core.training.engine.ZoneType
import com.movit.core.training.journal.WorkoutExecutionMetrics
import kotlin.math.abs

/**
 * Ports legacy [ReportGenerator] analysis sections to KMP common.
 */
object MovitPostTrainingReportBuilderV2 {
    private const val MAX_BEST_REPS = 3
    private const val MAX_PERFECT_MOMENTS = 3
    private const val MAX_DANGER_ALERTS = 2
    private const val MAX_TIPS = 3

    fun build(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<MovitPeakFrameCapture>,
        performanceSummary: MovitPerformanceSummary,
        executionMetrics: WorkoutExecutionMetrics,
        holdData: MovitHoldReportData? = null,
        poseVariantIndex: Int = 0,
        totalReps: Int = performanceSummary.totalReps,
        setNumber: Int = 1,
        repsTarget: Int = totalReps,
    ): MovitPostTrainingReportAnalysis {
        val dangerAlerts = generateDangerAlerts(repDetails, exerciseConfig, frameCaptures, poseVariantIndex, setNumber)
        val perfectMoments = generatePerfectMoments(repDetails, exerciseConfig, frameCaptures, poseVariantIndex, setNumber)
        val bestReps = findBestRepsByScore(repDetails, frameCaptures, setNumber)
        val worstRep = findWorstRep(repDetails, frameCaptures, exerciseConfig, poseVariantIndex, setNumber)
        val bestRepFrame = bestReps.firstOrNull()?.frameCapture
            ?: resolveFrameForRep(
                repNumber = bestReps.firstOrNull()?.repNumber ?: 0,
                frames = frameCaptures,
                preferredTypes = listOf(MovitPeakCaptureType.BEST_REP, MovitPeakCaptureType.PEAK_FRAME),
                maxNeighborDistance = 0,
                strictTypes = true,
                setNumber = setNumber,
            ).takeIf { bestReps.isNotEmpty() }
        val errorAnalysis = generateErrorAnalysis(
            repDetails = repDetails,
            exerciseConfig = exerciseConfig,
            frameCaptures = frameCaptures,
            bestRepFrame = bestRepFrame,
            poseVariantIndex = poseVariantIndex,
        )
        val timeline = generateTimeline(
            repDetails = repDetails,
            exerciseConfig = exerciseConfig,
            bestRepNumbers = bestReps.map { it.repNumber }.toSet(),
            worstRepNumber = worstRep?.repNumber,
            frameCaptures = frameCaptures,
            poseVariantIndex = poseVariantIndex,
            setNumber = setNumber,
        )
        val consistency = calculateConsistency(repDetails)
        val tips = generateTips(errorAnalysis, exerciseConfig, dangerAlerts, totalReps, poseVariantIndex)
        val holdSummary = holdData?.let { generateHoldSummary(it, frameCaptures) }
        val hasAnySideJoints = exerciseConfig.getTrackedJoints(poseVariantIndex)
            .any { it.trackingMode == TrackingMode.ANY_SIDE }
        val configSnapshot = MovitExerciseConfigSnapshot.from(
            countingMethod = exerciseConfig.countingMethod,
            isBilateral = exerciseConfig.isBilateral,
            supportsWeight = exerciseConfig.supportsWeight,
            hasPositionChecks = exerciseConfig.hasAnyPositionChecks(poseVariantIndex),
            metricsConfig = exerciseConfig.reportMetrics,
            hasAnySideJoints = hasAnySideJoints,
        )
        val overallQuality = calculateOverallQuality(
            performanceSummary = performanceSummary,
            errorAnalysis = errorAnalysis,
            timeline = timeline,
            dangerAlerts = dangerAlerts,
            consistency = consistency,
            isHoldExercise = exerciseConfig.isHoldExercise(),
            executionMetrics = executionMetrics,
        )
        val heroFrame = bestReps.firstOrNull()?.frameCapture
            ?: frameCaptures.firstOrNull { it.captureType == MovitPeakCaptureType.BEST_REP }
            ?: frameCaptures.firstOrNull { it.captureType == MovitPeakCaptureType.PEAK_FRAME }

        return MovitPostTrainingReportAnalysis(
            dangerAlerts = dangerAlerts,
            perfectMoments = perfectMoments,
            bestReps = bestReps,
            worstRep = worstRep,
            errorAnalysis = errorAnalysis,
            repTimeline = timeline,
            consistency = consistency,
            improvementTips = tips,
            holdSummary = holdSummary,
            heroFrame = heroFrame,
            overallQuality = overallQuality,
            exerciseConfig = configSnapshot,
            setSummaries = listOf(
                MovitSetSummary(
                    setNumber = setNumber,
                    repsCompleted = repDetails.size,
                    repsTarget = repsTarget,
                    averageScore = performanceSummary.averageScore,
                    durationMs = performanceSummary.durationMs,
                    countedReps = performanceSummary.countedReps,
                    invalidatedReps = performanceSummary.invalidatedReps,
                    weightKg = performanceSummary.weightKg,
                    dominantState = repDetails.maxByOrNull { it.worstState.priority }?.worstState
                        ?: JointState.NORMAL,
                ),
            ),
        )
    }

    private fun generateDangerAlerts(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<MovitPeakFrameCapture>,
        poseVariantIndex: Int,
        setNumber: Int,
    ): List<MovitDangerAlert> {
        val dangerReps = repDetails.filter { it.worstState == JointState.DANGER }
        if (dangerReps.isEmpty()) return emptyList()

        return dangerReps.take(MAX_DANGER_ALERTS).mapNotNull { rep ->
            val dangerError = rep.errors.firstOrNull { it.state == JointState.DANGER }
                ?: rep.errors.maxByOrNull { it.state.priority }
            val jointCode = dangerError?.jointCode ?: return@mapNotNull null
            val trackedJoint = getTrackedJoint(exerciseConfig, jointCode, poseVariantIndex)
            val zone = dangerError.actualAngle.let { trackedJoint?.determineZoneType(it) } ?: ZoneType.DOWN_ZONE
            val dangerMessage = trackedJoint?.stateMessages?.getMessage(JointState.DANGER, zone)
                ?: LocalizedText("وضعية خطيرة! انتبه لسلامتك", "Dangerous position! Watch your form")
            val tip = getRelevantTip(exerciseConfig, jointCode, poseVariantIndex)
                ?: LocalizedText("تحكم في الحركة ولا تتجاوز الحدود الآمنة", "Control the movement and stay within safe limits")
            val safeRange = getRangeFromError(dangerError)
                ?: getSafeRangeForJoint(trackedJoint, JointState.DANGER, dangerError.actualAngle, zone)
            val dangerFrame = resolveFrameForRep(
                repNumber = rep.repNumber,
                frames = frameCaptures,
                preferredTypes = listOf(
                    MovitPeakCaptureType.DANGER_FRAME,
                    MovitPeakCaptureType.ERROR_FRAME,
                    MovitPeakCaptureType.PEAK_FRAME,
                ),
                jointHint = jointCode,
                setNumber = setNumber,
            )
            MovitDangerAlert(
                repNumber = rep.repNumber,
                jointCode = jointCode,
                jointName = ReportJointNameHelper.getSimpleJointName(jointCode),
                actualAngle = dangerError.actualAngle,
                safeRange = safeRange,
                dangerMessage = dangerMessage,
                solutionTip = tip,
                dangerFrame = dangerFrame,
            )
        }
    }

    private fun generatePerfectMoments(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<MovitPeakFrameCapture>,
        poseVariantIndex: Int,
        setNumber: Int,
    ): List<MovitPerfectMoment> {
        val perfectReps = repDetails.filter { it.worstState == JointState.PERFECT }
        if (perfectReps.isEmpty()) return emptyList()

        val motivationals = exerciseConfig.getPoseVariant(poseVariantIndex)
            ?.feedbackMessages?.motivational ?: emptyList()

        return perfectReps.take(MAX_PERFECT_MOMENTS).mapIndexed { index, rep ->
            val frame = resolveFrameForRep(
                repNumber = rep.repNumber,
                frames = frameCaptures,
                preferredTypes = listOf(MovitPeakCaptureType.BEST_REP, MovitPeakCaptureType.PEAK_FRAME),
                maxNeighborDistance = 0,
                strictTypes = true,
                setNumber = setNumber,
            )
            val motivational = motivationals.getOrNull(index % motivationals.size.coerceAtLeast(1))
                ?: LocalizedText("ممتاز! أداء مثالي!", "Excellent! Perfect form!")
            MovitPerfectMoment(
                repNumber = rep.repNumber,
                score = rep.score,
                durationMs = calculateRepDuration(rep),
                motivationalMessage = motivational,
                frameCapture = frame,
            )
        }
    }

    private fun findBestRepsByScore(
        repDetails: List<RepResult>,
        frameCaptures: List<MovitPeakFrameCapture>,
        setNumber: Int,
    ): List<MovitBestRepHighlight> {
        val bestPoolRaw = when {
            repDetails.any { MovitRepQuality.fromRep(it) == MovitRepQuality.CLEAN } ->
                repDetails.filter { MovitRepQuality.fromRep(it) == MovitRepQuality.CLEAN }
            repDetails.any { MovitRepQuality.fromRep(it) == MovitRepQuality.NEEDS_CORRECTION } ->
                repDetails.filter { MovitRepQuality.fromRep(it) == MovitRepQuality.NEEDS_CORRECTION }
            else -> repDetails.filter { MovitRepQuality.fromRep(it) == MovitRepQuality.DANGER }
        }
        val bestPool = bestPoolRaw.ifEmpty { repDetails }
        val sortedReps = bestPool.sortedWith(
            compareByDescending<RepResult> { it.score }
                .thenBy { it.positionWarningCount + it.positionErrors.size }
                .thenBy { it.errors.size },
        )
        if (sortedReps.isEmpty()) return emptyList()

        return sortedReps.take(MAX_BEST_REPS).map { rep ->
            val frame = resolveFrameForRep(
                repNumber = rep.repNumber,
                frames = frameCaptures,
                preferredTypes = listOf(MovitPeakCaptureType.BEST_REP, MovitPeakCaptureType.PEAK_FRAME),
                maxNeighborDistance = 1,
                strictTypes = true,
                setNumber = setNumber,
            )
            val displayInfo = ReportStateDisplayConfig.getDisplayInfo(rep.worstState)
            MovitBestRepHighlight(
                repNumber = rep.repNumber,
                setNumber = setNumber,
                durationMs = calculateRepDuration(rep),
                score = rep.score,
                worstState = rep.worstState,
                quality = MovitRepQuality.fromRep(rep),
                reasons = listOf(displayInfo.toLocalizedText()),
                frameCapture = frame,
            )
        }
    }

    private fun findWorstRep(
        repDetails: List<RepResult>,
        frameCaptures: List<MovitPeakFrameCapture>,
        exerciseConfig: ExerciseConfig,
        poseVariantIndex: Int,
        setNumber: Int,
    ): MovitWorstRepHighlight? {
        if (repDetails.isEmpty()) return null

        val durations = repDetails.map { calculateRepDuration(it) }.sorted()
        val medianDuration = if (durations.size >= 2) durations[durations.size / 2] else durations.firstOrNull() ?: 0L

        val worstRep = repDetails.minByOrNull { rep ->
            val baseFitness = rep.score.toDouble()
            val errorPenalty = rep.positionErrors.size * 20.0 + rep.errors.size * 15.0
            val warningPenalty = rep.positionWarningCount * 5.0
            val repDuration = calculateRepDuration(rep)
            val timingPenalty = if (medianDuration > 0) {
                val deviation = abs(repDuration - medianDuration).toDouble() / medianDuration
                if (deviation > 0.5) deviation * 10.0 else 0.0
            } else {
                0.0
            }
            baseFitness - errorPenalty - warningPenalty - timingPenalty
        } ?: return null

        val primaryError = worstRep.errors.firstOrNull()?.let { error ->
            stateMessageForError(error, exerciseConfig, poseVariantIndex)
        } ?: worstRep.positionErrors.firstOrNull()?.message
            ?: ReportStateDisplayConfig.getDisplayInfo(worstRep.worstState).toLocalizedText()

        val jointHint = worstRep.errors.firstOrNull()?.jointCode
            ?: worstRep.positionErrors.firstOrNull()?.landmark1
        val frame = resolveFrameForRep(
            repNumber = worstRep.repNumber,
            frames = frameCaptures,
            preferredTypes = listOf(
                MovitPeakCaptureType.DANGER_FRAME,
                MovitPeakCaptureType.ERROR_FRAME,
                MovitPeakCaptureType.PEAK_FRAME,
            ),
            jointHint = jointHint,
            setNumber = setNumber,
        )

        return MovitWorstRepHighlight(
            repNumber = worstRep.repNumber,
            setNumber = setNumber,
            durationMs = calculateRepDuration(worstRep),
            score = worstRep.score,
            errorCount = worstRep.getTotalErrorCount() + worstRep.positionWarningCount,
            worstState = worstRep.worstState,
            quality = MovitRepQuality.fromRep(worstRep),
            primaryError = primaryError,
            frameCapture = frame,
        )
    }

    private fun generateErrorAnalysis(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<MovitPeakFrameCapture>,
        bestRepFrame: MovitPeakFrameCapture?,
        poseVariantIndex: Int,
    ): List<MovitReportErrorAnalysis> {
        data class ErrorOccurrence(val repNumber: Int, val error: JointError)

        val errorGroups = mutableMapOf<String, MutableList<ErrorOccurrence>>()
        repDetails.forEach { rep ->
            rep.errors.forEach { error ->
                if (error.state == JointState.WARNING || error.state == JointState.DANGER) {
                    val key = "${error.jointCode}:${error.state.name}"
                    errorGroups.getOrPut(key) { mutableListOf() }.add(ErrorOccurrence(rep.repNumber, error))
                }
            }
        }

        return errorGroups.map { (key, occurrences) ->
            val jointCode = key.substringBefore(":")
            val stateName = key.substringAfter(":")
            val state = runCatching { JointState.valueOf(stateName) }.getOrDefault(JointState.WARNING)
            val affectedReps = occurrences.map { it.repNumber }.distinct()
            val displayInfo = ReportStateDisplayConfig.getDisplayInfo(state)
            val trackedJoint = getTrackedJoint(exerciseConfig, jointCode, poseVariantIndex)
            val firstError = occurrences.first().error
            val zone = trackedJoint?.determineZoneType(firstError.actualAngle) ?: ZoneType.DOWN_ZONE
            val stateMessage = stateMessageForError(firstError, exerciseConfig, poseVariantIndex)
            val tip = getRelevantTip(exerciseConfig, jointCode, poseVariantIndex) ?: defaultTip(state)
            val avgAngle = occurrences.map { it.error.actualAngle }.average()
            val expectedRange = occurrences
                .mapNotNull { getRangeFromError(it.error) }
                .groupingBy { Pair(it.min, it.max) }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?.let { AngleRange(it.first, it.second) }
                ?: getSafeRangeForJoint(trackedJoint, state, avgAngle, zone)
            val errorFrame = affectedReps.asSequence()
                .mapNotNull { repNum ->
                    resolveFrameForRep(
                        repNumber = repNum,
                        frames = frameCaptures,
                        preferredTypes = listOf(MovitPeakCaptureType.DANGER_FRAME, MovitPeakCaptureType.ERROR_FRAME),
                        jointHint = jointCode,
                        maxNeighborDistance = 0,
                        strictTypes = true,
                    )
                }
                .firstOrNull()

            MovitReportErrorAnalysis(
                errorKey = key,
                jointCode = jointCode,
                jointName = ReportJointNameHelper.getSimpleJointName(jointCode),
                state = state.name,
                stateDisplayName = displayInfo.toLocalizedText(),
                stateIcon = displayInfo.icon,
                count = occurrences.size,
                affectedReps = affectedReps,
                message = stateMessage,
                tip = tip,
                averageActualAngle = avgAngle,
                expectedRange = expectedRange,
                bestRepAngle = null,
                errorFrame = errorFrame,
                bestRepFrame = bestRepFrame,
                errorType = firstError.errorType,
            )
        }.sortedWith(
            compareByDescending<MovitReportErrorAnalysis> { it.state == JointState.DANGER.name }
                .thenByDescending { it.count },
        )
    }

    private fun generateTimeline(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        bestRepNumbers: Set<Int>,
        worstRepNumber: Int?,
        frameCaptures: List<MovitPeakFrameCapture>,
        poseVariantIndex: Int,
        setNumber: Int,
    ): List<MovitRepTimelineEntry> = repDetails.map { rep ->
        val isBest = rep.repNumber in bestRepNumbers
        val isWorst = rep.repNumber == worstRepNumber
        val displayInfo = ReportStateDisplayConfig.getDisplayInfo(rep.worstState)
        val status = MovitRepStatus.fromState(rep.worstState, isBest, isWorst)
        val stateMessage = if (rep.worstState != JointState.PERFECT) {
            val primaryJoint = rep.errors.firstOrNull()?.jointCode
                ?: exerciseConfig.getPrimaryJoints(poseVariantIndex).firstOrNull()?.joint
            val trackedJoint = primaryJoint?.let { getTrackedJoint(exerciseConfig, it, poseVariantIndex) }
            val zone = rep.errors.firstOrNull()?.actualAngle?.let { angle ->
                trackedJoint?.determineZoneType(angle)
            } ?: ZoneType.DOWN_ZONE
            trackedJoint?.stateMessages?.getMessage(rep.worstState, zone)
        } else {
            null
        }
        val errors = rep.errors.map { getShortErrorLabel(it) }
        val frame = resolveFrameForRep(
            repNumber = rep.repNumber,
            frames = frameCaptures,
            preferredTypes = listOf(
                MovitPeakCaptureType.BEST_REP,
                MovitPeakCaptureType.PEAK_FRAME,
                MovitPeakCaptureType.DANGER_FRAME,
                MovitPeakCaptureType.ERROR_FRAME,
            ),
            jointHint = rep.errors.firstOrNull()?.jointCode,
            maxNeighborDistance = 0,
            setNumber = setNumber,
        )
        MovitRepTimelineEntry(
            repNumber = rep.repNumber,
            setNumber = setNumber,
            status = status,
            durationMs = calculateRepDuration(rep),
            errors = errors,
            isBestRep = isBest,
            isWorstRep = isWorst,
            frameCapture = frame,
            worstState = rep.worstState.name,
            stateDisplayName = displayInfo.toLocalizedText(),
            stateIcon = displayInfo.icon,
            score = rep.score,
            quality = MovitRepQuality.fromRep(rep),
            isCounted = rep.isCounted,
            isInvalidated = rep.isInvalidated,
            stateMessage = stateMessage,
            positionWarningCount = rep.positionWarningCount,
            positionErrorCount = rep.positionErrors.size,
        )
    }

    private fun generateTips(
        errorAnalysis: List<MovitReportErrorAnalysis>,
        exerciseConfig: ExerciseConfig,
        dangerAlerts: List<MovitDangerAlert>,
        totalReps: Int,
        poseVariantIndex: Int,
    ): List<MovitImprovementTip> {
        val tips = mutableListOf<MovitImprovementTip>()

        if (totalReps == 0) {
            val isHold = exerciseConfig.isHoldExercise()
            tips += MovitImprovementTip(
                id = "no_reps_tip",
                category = MovitTipCategory.GENERAL,
                icon = "📐",
                title = if (isHold) {
                    LocalizedText("الوصول للوضع الصحيح", "Reach the Correct Position")
                } else {
                    LocalizedText("المدى الحركي الكامل", "Full Range of Motion")
                },
                description = if (isHold) {
                    LocalizedText(
                        "تأكد من الوصول للوضع المطلوب والثبات فيه",
                        "Make sure to reach and hold the required position",
                    )
                } else {
                    LocalizedText(
                        "لإكمال العدة، يجب النزول للوضع السفلي ثم العودة للوضع الأول بالكامل",
                        "To complete a rep, go down to the bottom position and fully return to start",
                    )
                },
                priority = 1,
                severity = MovitTipSeverity.IMPORTANT,
            )
            tips += MovitImprovementTip(
                id = "camera_position_tip",
                category = MovitTipCategory.POSITION,
                icon = "📹",
                title = LocalizedText("وضع الكاميرا", "Camera Position"),
                description = LocalizedText(
                    "تأكد من أن جسمك بالكامل ظاهر في الكاميرا من الجانب",
                    "Ensure your full body is visible in the camera from the side",
                ),
                priority = 2,
                severity = MovitTipSeverity.HELPFUL,
            )
            return tips
        }

        dangerAlerts.firstOrNull()?.let { danger ->
            tips += MovitImprovementTip(
                id = "danger_fix_${danger.jointCode}",
                category = MovitTipCategory.SAFETY,
                icon = "🚨",
                title = LocalizedText("الأهم - لسلامتك", "Most Important - For Your Safety"),
                description = danger.solutionTip,
                priority = 1,
                severity = MovitTipSeverity.CRITICAL,
                relatedReps = listOf(danger.repNumber),
            )
        }

        errorAnalysis
            .filter { it.state == JointState.WARNING.name }
            .maxByOrNull { it.count }
            ?.let { warningError ->
                tips += MovitImprovementTip(
                    id = warningError.errorKey,
                    category = mapJointToCategory(warningError.jointCode),
                    icon = "1️⃣",
                    title = LocalizedText("نقطة التحسين الرئيسية", "Main Improvement Point"),
                    description = warningError.tip,
                    priority = 2,
                    severity = MovitTipSeverity.IMPORTANT,
                    relatedReps = warningError.affectedReps,
                )
            }

        val exerciseTips = exerciseConfig.getPoseVariant(poseVariantIndex)?.feedbackMessages?.tips ?: emptyList()
        exerciseTips.take(2).forEachIndexed { index, tip ->
            tips += MovitImprovementTip(
                id = "exercise_tip_$index",
                category = MovitTipCategory.GENERAL,
                icon = if (index == 0) "2️⃣" else "🎯",
                title = LocalizedText(
                    ar = if (index == 0) "نصيحة إضافية" else "تركيز الجلسة القادمة",
                    en = if (index == 0) "Additional Tip" else "Next Workout Focus",
                ),
                description = tip,
                priority = 3 + index,
                severity = MovitTipSeverity.HELPFUL,
                isNextFocus = index == 1,
            )
        }

        if (tips.isEmpty()) {
            tips += MovitImprovementTip(
                id = "perfect",
                category = MovitTipCategory.GENERAL,
                icon = "⭐",
                title = LocalizedText("ممتاز!", "Excellent!"),
                description = LocalizedText("حافظ على هذا الأداء الرائع!", "Keep up this excellent performance!"),
                priority = 1,
                severity = MovitTipSeverity.HELPFUL,
            )
        }

        return tips.take(MAX_TIPS)
    }

    private fun generateHoldSummary(
        holdData: MovitHoldReportData,
        frameCaptures: List<MovitPeakFrameCapture>,
    ): MovitHoldSummary {
        val percentage = if (holdData.targetMs > 0) {
            (holdData.achievedMs.toFloat() / holdData.targetMs.toFloat()) * 100f
        } else {
            0f
        }
        val jointBreakdown = holdData.jointErrorMap.map { (jointCode, errorCount) ->
            MovitJointHoldQuality(
                jointCode = jointCode,
                jointName = ReportJointNameHelper.getJointName(jointCode),
                quality = MovitHoldQuality.fromErrorCount(errorCount),
                errorCount = errorCount,
            )
        }
        val sampleFrames = frameCaptures.filter { it.captureType == MovitPeakCaptureType.HOLD_SAMPLE }
        return MovitHoldSummary(
            targetMs = holdData.targetMs,
            achievedMs = holdData.achievedMs,
            percentage = percentage,
            formQuality = holdData.formQuality * 100f,
            gracePeriodsUsed = holdData.gracePeriodsUsed,
            jointBreakdown = jointBreakdown,
            sampleFrames = sampleFrames,
        )
    }

    private fun calculateConsistency(repDetails: List<RepResult>): MovitConsistencyMetrics? {
        if (repDetails.size < 2) return null
        val durations = repDetails.associate { it.repNumber to calculateRepDuration(it) }
        return MovitConsistencyMetrics.calculate(durations)
    }

    private fun calculateOverallQuality(
        performanceSummary: MovitPerformanceSummary,
        errorAnalysis: List<MovitReportErrorAnalysis>,
        timeline: List<MovitRepTimelineEntry>,
        dangerAlerts: List<MovitDangerAlert>,
        consistency: MovitConsistencyMetrics?,
        isHoldExercise: Boolean,
        executionMetrics: WorkoutExecutionMetrics,
    ): MovitOverallQualityScore {
        val formScore = ReportQualityScoring.calculateFormScore(
            summary = performanceSummary,
            timeline = timeline,
            isHoldExercise = isHoldExercise,
        )
        val safetyScore = ReportQualityScoring.calculateSafetyScore(
            errorAnalysis = errorAnalysis,
            invalidatedReps = performanceSummary.invalidatedReps,
            totalReps = performanceSummary.totalReps,
        )
        val controlScore = ReportQualityScoring.calculateControlScore(
            totalReps = timeline.size,
            consistency = consistency,
            fatigueIndex = performanceSummary.fatigueIndex,
            tempoConsistency = executionMetrics.tempoConsistency?.let { it / 10f },
            velocityLoss = executionMetrics.velocityLoss?.let { it / 10f },
            formConsistency = performanceSummary.formConsistency,
        )
        return MovitOverallQualityScore.calculate(
            formScore = formScore,
            safetyScore = safetyScore,
            controlScore = controlScore,
            isHoldExercise = isHoldExercise,
        )
    }

    private fun resolveFrameForRep(
        repNumber: Int,
        frames: List<MovitPeakFrameCapture>,
        preferredTypes: List<MovitPeakCaptureType>,
        jointHint: String? = null,
        maxNeighborDistance: Int = 2,
        strictTypes: Boolean = false,
        setNumber: Int? = null,
    ): MovitPeakFrameCapture? {
        if (frames.isEmpty() || repNumber < 1) return null
        val scopedFrames = setNumber?.let { set -> frames.filter { it.setNumber == set } } ?: frames

        fun pickForRep(rep: Int): MovitPeakFrameCapture? {
            for (type in preferredTypes) {
                val list = scopedFrames.filter { it.repNumber == rep && it.captureType == type }
                list.firstOrNull()?.let { return it }
            }
            if (strictTypes) return null
            return scopedFrames.firstOrNull { it.repNumber == rep }
        }

        pickForRep(repNumber)?.let { return it }
        for (d in 1..maxNeighborDistance) {
            for (neighbor in listOf(repNumber - d, repNumber + d)) {
                if (neighbor < 1) continue
                pickForRep(neighbor)?.let { return it }
            }
        }
        return null
    }

    private fun calculateRepDuration(rep: RepResult): Long =
        rep.phaseTimings.values.sum().takeIf { it > 0 } ?: 2_000L

    private fun getShortErrorLabel(error: JointError): String {
        val joint = error.jointCode.substringAfterLast("_")
        return when (error.errorType) {
            ErrorType.TOO_HIGH -> "$joint↑"
            ErrorType.TOO_LOW -> "$joint↓"
        }
    }

    private fun getTrackedJoint(
        exerciseConfig: ExerciseConfig,
        jointCode: String,
        poseVariantIndex: Int,
    ): TrackedJoint? = exerciseConfig.getPoseVariant(poseVariantIndex)
        ?.trackedJoints
        ?.find { it.joint == jointCode }

    private fun getRelevantTip(
        exerciseConfig: ExerciseConfig,
        jointCode: String,
        poseVariantIndex: Int,
    ): LocalizedText? {
        val tips = exerciseConfig.getPoseVariant(poseVariantIndex)?.feedbackMessages?.tips ?: return null
        return tips.find { tip ->
            tip.en.lowercase().contains(jointCode.replace("_", " ").lowercase()) ||
                tip.en.lowercase().contains(jointCode.substringAfter("_").lowercase())
        } ?: tips.firstOrNull()
    }

    private fun stateMessageForError(
        error: JointError,
        exerciseConfig: ExerciseConfig,
        poseVariantIndex: Int,
    ): LocalizedText {
        val trackedJoint = getTrackedJoint(exerciseConfig, error.jointCode, poseVariantIndex)
        val zone = trackedJoint?.determineZoneType(error.actualAngle) ?: ZoneType.DOWN_ZONE
        return trackedJoint?.stateMessages?.getMessage(error.state, zone)
            ?: defaultStateMessage(error.state)
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
        actualAngle: Double?,
        zone: ZoneType,
    ): AngleRange {
        if (trackedJoint == null) return AngleRange(0.0, 180.0)
        val activeRanges = resolveActiveRangesForAngle(trackedJoint, actualAngle, zone)
        return activeRanges?.let { getRangeForState(it, state) }
            ?: trackedJoint.range?.let { getRangeForState(it, state) }
            ?: trackedJoint.upRange?.let { getRangeForState(it, state) }
            ?: trackedJoint.downRange?.let { getRangeForState(it, state) }
            ?: AngleRange(0.0, 180.0)
    }

    private fun resolveActiveRangesForAngle(
        trackedJoint: TrackedJoint,
        actualAngle: Double?,
        fallbackZone: ZoneType,
    ): StateRanges? {
        trackedJoint.range?.let { return it }
        if (!trackedJoint.hasStateUpDownRanges()) return null
        if (actualAngle == null) return trackedJoint.upRange ?: trackedJoint.downRange
        return when (trackedJoint.determineZoneType(actualAngle)) {
            ZoneType.UP_ZONE -> trackedJoint.upRange
            ZoneType.DOWN_ZONE -> trackedJoint.downRange
            ZoneType.TRANSITION -> {
                val upRange = trackedJoint.upRange
                val downRange = trackedJoint.downRange
                when {
                    upRange == null -> downRange
                    downRange == null -> upRange
                    abs(actualAngle - upRange.effectiveMin) <= abs(actualAngle - downRange.effectiveMax) -> upRange
                    else -> downRange
                }
            }
        } ?: when (fallbackZone) {
            ZoneType.UP_ZONE -> trackedJoint.upRange
            ZoneType.DOWN_ZONE -> trackedJoint.downRange
            ZoneType.TRANSITION -> trackedJoint.upRange ?: trackedJoint.downRange
        }
    }

    private fun getRangeForState(stateRanges: StateRanges, state: JointState): AngleRange = when (state) {
        JointState.PERFECT -> stateRanges.perfect
        JointState.NORMAL -> stateRanges.normal ?: stateRanges.perfect
        JointState.PAD -> stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
        JointState.WARNING -> stateRanges.warning ?: stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
        JointState.DANGER -> stateRanges.danger ?: stateRanges.warning ?: stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
        JointState.TRANSITION -> AngleRange(stateRanges.effectiveMin, stateRanges.effectiveMax)
    }

    private fun defaultStateMessage(state: JointState): LocalizedText = when (state) {
        JointState.PERFECT -> LocalizedText("ممتاز!", "Excellent!")
        JointState.NORMAL -> LocalizedText("جيد", "Good")
        JointState.PAD -> LocalizedText("مقبول", "Acceptable")
        JointState.WARNING -> LocalizedText("يحتاج تحسين", "Needs improvement")
        JointState.DANGER -> LocalizedText("خطر!", "Danger!")
        JointState.TRANSITION -> LocalizedText("انتقال", "Moving")
    }

    private fun defaultTip(state: JointState): LocalizedText = when (state) {
        JointState.DANGER -> LocalizedText(
            "تحكم في الحركة ولا تتجاوز الحدود الآمنة",
            "Control the movement and stay within safe limits",
        )
        JointState.WARNING -> LocalizedText("حاول التركيز على الوضعية الصحيحة", "Try to focus on proper form")
        else -> LocalizedText("استمر بالتدريب للتحسن", "Keep practicing to improve")
    }

    private fun mapJointToCategory(jointCode: String): MovitTipCategory = when {
        jointCode.contains("knee") || jointCode.contains("hip") -> MovitTipCategory.DEPTH
        jointCode.contains("spine") || jointCode.contains("shoulder") -> MovitTipCategory.ALIGNMENT
        jointCode.contains("ankle") -> MovitTipCategory.POSITION
        else -> MovitTipCategory.STABILITY
    }
}
