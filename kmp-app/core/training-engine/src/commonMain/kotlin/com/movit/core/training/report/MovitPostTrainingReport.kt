package com.movit.core.training.report

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.RepResult
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.StateCode
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.session.ExerciseWorkoutSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt

/**
 * KMP per-exercise post-training report — domain model separate from [ReportDetailUi].
 * Encodes to legacy [PostTrainingReport] Gson JSON shape for `legacyReport` upload field.
 */
@Serializable
enum class MovitPerformanceRating {
    EXCELLENT,
    GOOD,
    FAIR,
    NEEDS_WORK,
    ;

    companion object {
        fun fromScoreAndRatio(averageScore: Float, countedRatio: Float): MovitPerformanceRating = when {
            averageScore >= 80f && countedRatio >= 0.9f -> EXCELLENT
            averageScore >= 60f && countedRatio >= 0.75f -> GOOD
            averageScore >= 40f && countedRatio >= 0.6f -> FAIR
            else -> NEEDS_WORK
        }
    }
}

@Serializable
data class MovitStateBreakdown(
    val perfectCount: Int = 0,
    val normalCount: Int = 0,
    val padCount: Int = 0,
    val warningCount: Int = 0,
    val dangerCount: Int = 0,
) {
    companion object {
        fun fromRepMetrics(reps: List<RepMetricsData>): MovitStateBreakdown {
            var perfect = 0
            var normal = 0
            var pad = 0
            var warning = 0
            var danger = 0
            reps.forEach { rep ->
                when (rep.worstState) {
                    StateCode.PERFECT -> perfect++
                    StateCode.NORMAL -> normal++
                    StateCode.PAD -> pad++
                    StateCode.WARNING -> warning++
                    StateCode.DANGER -> danger++
                }
            }
            return MovitStateBreakdown(perfect, normal, pad, warning, danger)
        }

        fun fromJointStateMap(breakdown: Map<JointState, Int>): MovitStateBreakdown = MovitStateBreakdown(
            perfectCount = breakdown[JointState.PERFECT] ?: 0,
            normalCount = breakdown[JointState.NORMAL] ?: 0,
            padCount = breakdown[JointState.PAD] ?: 0,
            warningCount = breakdown[JointState.WARNING] ?: 0,
            dangerCount = breakdown[JointState.DANGER] ?: 0,
        )

        fun fromRepDetails(reps: List<RepResult>): MovitStateBreakdown =
            fromJointStateMap(reps.groupingBy { it.worstState }.eachCount())
    }
}

@Serializable
data class MovitPerformanceSummary(
    val totalReps: Int,
    val durationMs: Long,
    val rating: MovitPerformanceRating,
    val motivationalMessage: LocalizedText,
    val countedReps: Int,
    val invalidatedReps: Int,
    val averageScore: Float,
    val countedRatio: Float,
    val stateBreakdown: MovitStateBreakdown,
    val shouldCelebrate: Boolean = false,
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val avgRom: Float? = null,
    val avgSymmetry: Float? = null,
    val avgStability: Float? = null,
    val formConsistency: Float? = null,
    val fatigueIndex: Int? = null,
    /** Full count of reps with DANGER worst-state — not capped like [MovitPostTrainingReport.dangerAlerts]. */
    val dangerRepCount: Int = 0,
)

@Serializable
enum class MovitTrackingQualityLevel {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

@Serializable
data class MovitExecutionQuality(
    val visibilityPauseCount: Int,
    val totalInvisibleMs: Long,
    val cameraWarningCount: Int,
    val overallQuality: MovitTrackingQualityLevel,
    val suggestions: List<LocalizedText> = emptyList(),
) {
    companion object {
        fun calculateLevel(pauseCount: Int, cameraWarnings: Int): MovitTrackingQualityLevel = when {
            pauseCount == 0 && cameraWarnings == 0 -> MovitTrackingQualityLevel.EXCELLENT
            pauseCount <= 1 && cameraWarnings <= 1 -> MovitTrackingQualityLevel.GOOD
            pauseCount <= 3 && cameraWarnings <= 3 -> MovitTrackingQualityLevel.FAIR
            else -> MovitTrackingQualityLevel.POOR
        }

        fun fromSessionQuality(meta: SessionQualityMeta): MovitExecutionQuality {
            val pauseCount = meta.visibilityPauseCount
            val warnings = meta.cameraWarningCount
            return MovitExecutionQuality(
                visibilityPauseCount = pauseCount,
                totalInvisibleMs = 0L,
                cameraWarningCount = warnings,
                overallQuality = calculateLevel(pauseCount, warnings),
                suggestions = buildSuggestions(pauseCount, warnings),
            )
        }

        private fun buildSuggestions(pauseCount: Int, cameraWarnings: Int): List<LocalizedText> {
            val suggestions = mutableListOf<LocalizedText>()
            if (pauseCount > 0) {
                suggestions += LocalizedText(
                    ar = "حاول البقاء داخل إطار الكاميرا طوال التمرين",
                    en = "Try to stay in the camera frame throughout the exercise",
                )
            }
            if (cameraWarnings > 0) {
                suggestions += LocalizedText(
                    ar = "لتتبع أفضل، جرّب التصوير من الجانب",
                    en = "For better tracking, try filming from the side",
                )
            }
            return suggestions
        }
    }
}

@Serializable
data class MovitPostTrainingReport(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val exerciseName: LocalizedText,
    val timestamp: Long,
    val summary: MovitPerformanceSummary,
    val executionQuality: MovitExecutionQuality,
    val sessionQuality: SessionQualityMeta? = null,
    val peakFrameCaptures: List<MovitPeakFrameCapture> = emptyList(),
    val repReplayClips: List<MovitRepReplayClip> = emptyList(),
    val dangerAlerts: List<MovitDangerAlert> = emptyList(),
    val perfectMoments: List<MovitPerfectMoment> = emptyList(),
    val errorAnalysis: List<MovitReportErrorAnalysis> = emptyList(),
    val repTimeline: List<MovitRepTimelineEntry> = emptyList(),
    val bestReps: List<MovitBestRepHighlight> = emptyList(),
    val worstRep: MovitWorstRepHighlight? = null,
    val improvementTips: List<MovitImprovementTip> = emptyList(),
    val consistency: MovitConsistencyMetrics? = null,
    val holdSummary: MovitHoldSummary? = null,
    val heroFrame: MovitPeakFrameCapture? = null,
    val overallQuality: MovitOverallQualityScore? = null,
    val exerciseConfig: MovitExerciseConfigSnapshot? = null,
    val setSummaries: List<MovitSetSummary> = emptyList(),
    val poseVariantIndex: Int = 0,
)

object MovitPostTrainingReportBuilder {
    fun build(
        upload: WorkoutUpload,
        summary: ExerciseWorkoutSummary,
        exerciseConfig: ExerciseConfig,
        exerciseSlug: String = upload.exerciseId,
        sessionQuality: SessionQualityMeta? = null,
        peakFrameCaptures: List<MovitPeakFrameCapture> = emptyList(),
        repReplayClips: List<MovitRepReplayClip> = emptyList(),
        holdData: MovitHoldReportData? = null,
        reportId: String = upload.id,
        workoutId: String = upload.id,
        timestamp: Long = upload.timestamp,
        poseVariantIndex: Int = summary.poseVariantIndex,
        setNumber: Int = 1,
        repsTarget: Int = summary.totalReps,
    ): MovitPostTrainingReport {
        val stateBreakdown = when {
            summary.repDetails.isNotEmpty() -> MovitStateBreakdown.fromRepDetails(summary.repDetails)
            summary.stateBreakdown.isNotEmpty() -> MovitStateBreakdown.fromJointStateMap(summary.stateBreakdown)
            else -> MovitStateBreakdown.fromRepMetrics(upload.repMetrics)
        }
        val rating = MovitPerformanceRating.fromScoreAndRatio(summary.averageScore, summary.countedRatio)
        val metrics = upload.executionMetrics
        val weightKg = summary.weightKg ?: upload.weightKg
        val weightUnit = if (summary.weightKg != null) summary.weightUnit else upload.weightUnit
        val performanceSummary = MovitPerformanceSummary(
            totalReps = summary.totalReps,
            durationMs = summary.durationMs,
            rating = rating,
            motivationalMessage = ratingMotivationalMessage(rating),
            countedReps = summary.countedReps,
            invalidatedReps = summary.invalidatedReps,
            averageScore = summary.averageScore,
            countedRatio = summary.countedRatio,
            stateBreakdown = stateBreakdown,
            shouldCelebrate = stateBreakdown.dangerCount == 0 && summary.averageScore >= 85f,
            weightKg = weightKg,
            weightUnit = weightUnit,
            avgRom = metrics.avgRom.takeIf { it > 0 }?.let { it / 10f },
            avgSymmetry = metrics.avgSymmetry?.let { it / 10f },
            avgStability = metrics.avgStability?.takeIf { it > 0 }?.let { it / 10f },
            formConsistency = metrics.formConsistency?.let { it / 10f },
            fatigueIndex = metrics.fatigueIndex?.toInt(),
            dangerRepCount = when {
                summary.repDetails.isNotEmpty() ->
                    summary.repDetails.count { it.worstState == JointState.DANGER }
                else -> stateBreakdown.dangerCount
            },
        )
        val executionQuality = sessionQuality?.let { MovitExecutionQuality.fromSessionQuality(it) }
            ?: MovitExecutionQuality(
                visibilityPauseCount = 0,
                totalInvisibleMs = 0L,
                cameraWarningCount = 0,
                overallQuality = MovitTrackingQualityLevel.EXCELLENT,
            )
        val analysis = if (summary.repDetails.isNotEmpty()) {
            MovitPostTrainingReportBuilderV2.build(
                repDetails = summary.repDetails,
                exerciseConfig = exerciseConfig,
                frameCaptures = peakFrameCaptures,
                performanceSummary = performanceSummary,
                executionMetrics = metrics,
                holdData = holdData,
                poseVariantIndex = poseVariantIndex,
                totalReps = summary.totalReps,
                setNumber = setNumber,
                repsTarget = repsTarget,
            )
        } else {
            MovitPostTrainingReportAnalysis()
        }
        return MovitPostTrainingReport(
            id = reportId,
            workoutId = workoutId,
            exerciseId = upload.exerciseId,
            exerciseName = exerciseConfig.name,
            timestamp = timestamp,
            summary = performanceSummary,
            executionQuality = executionQuality,
            sessionQuality = sessionQuality,
            peakFrameCaptures = peakFrameCaptures,
            repReplayClips = repReplayClips,
            dangerAlerts = analysis.dangerAlerts,
            perfectMoments = analysis.perfectMoments,
            errorAnalysis = analysis.errorAnalysis,
            repTimeline = analysis.repTimeline,
            bestReps = analysis.bestReps,
            worstRep = analysis.worstRep,
            improvementTips = analysis.improvementTips,
            consistency = analysis.consistency,
            holdSummary = analysis.holdSummary,
            heroFrame = analysis.heroFrame,
            overallQuality = analysis.overallQuality,
            exerciseConfig = analysis.exerciseConfig,
            setSummaries = analysis.setSummaries,
            poseVariantIndex = poseVariantIndex,
        )
    }

    private fun ratingMotivationalMessage(rating: MovitPerformanceRating): LocalizedText = when (rating) {
        MovitPerformanceRating.EXCELLENT -> LocalizedText(
            ar = "ممتاز! أداء قريب من المثالي!",
            en = "Outstanding! Nearly perfect form!",
        )
        MovitPerformanceRating.GOOD -> LocalizedText(
            ar = "عمل رائع! استمر!",
            en = "Great job! Keep it up!",
        )
        MovitPerformanceRating.FAIR -> LocalizedText(
            ar = "جهد جيد! يوجد مجال للتحسين.",
            en = "Good effort! Room to improve.",
        )
        MovitPerformanceRating.NEEDS_WORK -> LocalizedText(
            ar = "استمر بالتمرين! ستتحسن!",
            en = "Keep practicing! You'll get better!",
        )
    }
}

object PostTrainingReportLegacyJson {
    fun encode(report: MovitPostTrainingReport): JsonElement = buildJsonObject {
        put("id", JsonPrimitive(report.id))
        put("workoutId", JsonPrimitive(report.workoutId))
        put("exerciseId", JsonPrimitive(report.exerciseId))
        put("exerciseName", localizedText(report.exerciseName))
        put("timestamp", JsonPrimitive(report.timestamp))
        put("summary", encodeSummary(report.summary))
        put("executionQuality", encodeExecutionQuality(report.executionQuality))
        report.sessionQuality?.let { put("sessionQuality", encodeSessionQuality(it)) }
        put("dangerAlerts", encodeDangerAlerts(report.dangerAlerts))
        put("perfectMoments", encodePerfectMoments(report.perfectMoments))
        put("bestReps", encodeBestReps(report.bestReps))
        put("worstRep", report.worstRep?.let { encodeWorstRep(it) } ?: JsonNull)
        put("errorAnalysis", encodeErrorAnalysis(report.errorAnalysis))
        put("repTimeline", encodeRepTimeline(report.repTimeline))
        put("consistency", report.consistency?.let { encodeConsistency(it) } ?: JsonNull)
        put("improvementTips", encodeImprovementTips(report.improvementTips))
        put("frameCaptures", encodePeakCaptures(report.peakFrameCaptures))
        put("repReplayClips", encodeReplayClips(report.repReplayClips))
        put("holdSummary", report.holdSummary?.let { encodeHoldSummary(it) } ?: JsonNull)
        put("quickInsight", JsonNull)
        put("heroFrame", report.heroFrame?.let { encodePeakCapture(it) } ?: JsonNull)
        put("performanceMetrics", JsonNull)
        put("overallQuality", report.overallQuality?.let { encodeOverallQuality(it) } ?: JsonNull)
        put("exerciseConfig", report.exerciseConfig?.let { encodeExerciseConfig(it) } ?: JsonNull)
        put("setSummaries", encodeSetSummaries(report.setSummaries))
    }

    private fun encodeSummary(summary: MovitPerformanceSummary): JsonObject = buildJsonObject {
        put("totalReps", JsonPrimitive(summary.totalReps))
        put("durationMs", JsonPrimitive(summary.durationMs))
        put("rating", JsonPrimitive(summary.rating.name))
        put("motivationalMessage", localizedText(summary.motivationalMessage))
        put("countedReps", JsonPrimitive(summary.countedReps))
        put("invalidatedReps", JsonPrimitive(summary.invalidatedReps))
        put("averageScore", JsonPrimitive(roundScore(summary.averageScore)))
        put("countedRatio", JsonPrimitive(roundScore(summary.countedRatio)))
        put("stateBreakdown", buildJsonObject {
            put("perfectCount", JsonPrimitive(summary.stateBreakdown.perfectCount))
            put("normalCount", JsonPrimitive(summary.stateBreakdown.normalCount))
            put("padCount", JsonPrimitive(summary.stateBreakdown.padCount))
            put("warningCount", JsonPrimitive(summary.stateBreakdown.warningCount))
            put("dangerCount", JsonPrimitive(summary.stateBreakdown.dangerCount))
        })
        put("shouldCelebrate", JsonPrimitive(summary.shouldCelebrate))
        summary.weightKg?.let { put("weightKg", JsonPrimitive(it)) }
        put("weightUnit", JsonPrimitive(summary.weightUnit))
        summary.avgRom?.let { put("avgROM", JsonPrimitive(roundScore(it))) }
        summary.avgSymmetry?.let { put("avgSymmetry", JsonPrimitive(roundScore(it))) }
        summary.avgStability?.let { put("avgStability", JsonPrimitive(roundScore(it))) }
        summary.formConsistency?.let { put("formConsistency", JsonPrimitive(roundScore(it))) }
        summary.fatigueIndex?.let { put("fatigueIndex", JsonPrimitive(it)) }
        put("dangerRepCount", JsonPrimitive(summary.dangerRepCount))
    }

    private fun encodeExecutionQuality(quality: MovitExecutionQuality): JsonObject = buildJsonObject {
        put("visibilityPauseCount", JsonPrimitive(quality.visibilityPauseCount))
        put("totalInvisibleMs", JsonPrimitive(quality.totalInvisibleMs))
        put("cameraWarningCount", JsonPrimitive(quality.cameraWarningCount))
        put("overallQuality", JsonPrimitive(quality.overallQuality.name))
        put("suggestions", buildJsonArray {
            quality.suggestions.forEach { add(localizedText(it)) }
        })
    }

    private fun encodeSessionQuality(meta: SessionQualityMeta): JsonObject = buildJsonObject {
        put("framesOffered", JsonPrimitive(meta.framesOffered))
        put("framesRecorded", JsonPrimitive(meta.framesRecorded))
        put("framesDropped", JsonPrimitive(meta.framesDropped))
        put("frameDropRate", JsonPrimitive(roundScore(meta.frameDropRate)))
        meta.jointCoverageRatio?.let { put("jointCoverageRatio", JsonPrimitive(roundScore(it))) }
        put("visibilityPauseCount", JsonPrimitive(meta.visibilityPauseCount))
        put("cameraWarningCount", JsonPrimitive(meta.cameraWarningCount))
        meta.throughputProfileId?.let { put("throughputProfileId", JsonPrimitive(it)) }
        meta.avgAchievedFps?.let { put("avgAchievedFps", JsonPrimitive(roundScore(it))) }
        if (meta.adaptiveDowngrades > 0) {
            put("adaptiveDowngrades", JsonPrimitive(meta.adaptiveDowngrades))
        }
    }

    private fun encodeDangerAlerts(alerts: List<MovitDangerAlert>): JsonArray = buildJsonArray {
        alerts.forEach { alert ->
            add(
                buildJsonObject {
                    put("repNumber", JsonPrimitive(alert.repNumber))
                    put("jointCode", JsonPrimitive(alert.jointCode))
                    put("jointName", localizedText(alert.jointName))
                    put("actualAngle", JsonPrimitive(alert.actualAngle))
                    put("safeRange", encodeAngleRange(alert.safeRange))
                    put("dangerMessage", localizedText(alert.dangerMessage))
                    put("solutionTip", localizedText(alert.solutionTip))
                    put("dangerFrame", alert.dangerFrame?.let { encodePeakCapture(it) } ?: JsonNull)
                },
            )
        }
    }

    private fun encodePerfectMoments(moments: List<MovitPerfectMoment>): JsonArray = buildJsonArray {
        moments.forEach { moment ->
            add(
                buildJsonObject {
                    put("repNumber", JsonPrimitive(moment.repNumber))
                    put("score", JsonPrimitive(roundScore(moment.score)))
                    put("durationMs", JsonPrimitive(moment.durationMs))
                    put("motivationalMessage", localizedText(moment.motivationalMessage))
                    put("frameCapture", moment.frameCapture?.let { encodePeakCapture(it) } ?: JsonNull)
                },
            )
        }
    }

    private fun encodeConsistency(metrics: MovitConsistencyMetrics): JsonObject = buildJsonObject {
        put("averageDurationMs", JsonPrimitive(metrics.averageDurationMs))
        put("minDurationMs", JsonPrimitive(metrics.minDurationMs))
        put("maxDurationMs", JsonPrimitive(metrics.maxDurationMs))
        put("fastestRep", JsonPrimitive(metrics.fastestRep))
        put("slowestRep", JsonPrimitive(metrics.slowestRep))
        put("variationMs", JsonPrimitive(metrics.variationMs))
    }

    private fun encodeOverallQuality(quality: MovitOverallQualityScore): JsonObject = buildJsonObject {
        put("score", JsonPrimitive(roundScore(quality.score)))
        put("formScore", JsonPrimitive(roundScore(quality.formScore)))
        put("safetyScore", JsonPrimitive(roundScore(quality.safetyScore)))
        put("controlScore", JsonPrimitive(roundScore(quality.controlScore)))
        put("formWeight", JsonPrimitive(quality.formWeight))
        put("safetyWeight", JsonPrimitive(quality.safetyWeight))
        put("controlWeight", JsonPrimitive(quality.controlWeight))
        put("rating", JsonPrimitive(quality.rating.name))
    }

    private fun encodeExerciseConfig(config: MovitExerciseConfigSnapshot): JsonObject = buildJsonObject {
        put("countingMethod", JsonPrimitive(config.countingMethod.name))
        put("isBilateral", JsonPrimitive(config.isBilateral))
        put("hasAnySideJoints", JsonPrimitive(config.hasAnySideJoints))
        put("supportsWeight", JsonPrimitive(config.supportsWeight))
        put("hasPositionChecks", JsonPrimitive(config.hasPositionChecks))
        put(
            "metricsConfig",
            buildJsonObject {
                put("primary", buildJsonArray { config.metricsConfig.primary.forEach { add(JsonPrimitive(it.name)) } })
                put("optional", buildJsonArray { config.metricsConfig.optional.forEach { add(JsonPrimitive(it.name)) } })
                put("excluded", buildJsonArray { config.metricsConfig.excluded.forEach { add(JsonPrimitive(it.name)) } })
            },
        )
    }

    private fun encodeSetSummaries(summaries: List<MovitSetSummary>): JsonArray = buildJsonArray {
        summaries.forEach { set ->
            add(
                buildJsonObject {
                    put("setNumber", JsonPrimitive(set.setNumber))
                    put("repsCompleted", JsonPrimitive(set.repsCompleted))
                    put("repsTarget", JsonPrimitive(set.repsTarget))
                    put("averageScore", JsonPrimitive(roundScore(set.averageScore)))
                    put("durationMs", JsonPrimitive(set.durationMs))
                    put("countedReps", JsonPrimitive(set.countedReps))
                    put("invalidatedReps", JsonPrimitive(set.invalidatedReps))
                    set.weightKg?.let { put("weightKg", JsonPrimitive(it)) }
                    put("dominantState", JsonPrimitive(set.dominantState.name))
                },
            )
        }
    }

    private fun encodeAngleRange(range: com.movit.core.training.config.AngleRange): JsonObject = buildJsonObject {
        put("min", JsonPrimitive(range.min))
        put("max", JsonPrimitive(range.max))
    }

    private fun encodePeakCapture(capture: MovitPeakFrameCapture): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(capture.id))
        put("repNumber", JsonPrimitive(capture.repNumber))
        put("setNumber", JsonPrimitive(capture.setNumber))
        put("phase", JsonPrimitive(capture.phaseCode.toInt()))
        put("timestamp", JsonPrimitive(capture.capturedAtMs))
        put("captureType", JsonPrimitive(capture.captureType.name))
        put("errorType", capture.errorType?.let { JsonPrimitive(it) } ?: JsonNull)
        put("frameUri", JsonPrimitive(capture.localPath))
        put("thumbnailUri", JsonPrimitive(capture.thumbnailPath ?: capture.localPath))
        put("metadata", encodeCaptureMetadata(capture.metadata))
    }

    private fun encodeErrorAnalysis(items: List<MovitReportErrorAnalysis>): JsonArray = buildJsonArray {
        items.forEach { item ->
            add(
                buildJsonObject {
                    put("errorKey", JsonPrimitive(item.errorKey))
                    put("jointCode", JsonPrimitive(item.jointCode))
                    put("jointName", localizedText(item.jointName))
                    put("state", JsonPrimitive(item.state))
                    put("stateDisplayName", localizedText(item.stateDisplayName))
                    put("stateIcon", JsonPrimitive(item.stateIcon))
                    put("count", JsonPrimitive(item.count))
                    put("affectedReps", buildJsonArray { item.affectedReps.forEach { add(JsonPrimitive(it)) } })
                    put("message", localizedText(item.message))
                    put("tip", localizedText(item.tip))
                    item.averageActualAngle?.let { put("averageActualAngle", JsonPrimitive(it)) }
                    item.expectedRange?.let { put("expectedRange", encodeAngleRange(it)) }
                    item.bestRepAngle?.let { put("bestRepAngle", JsonPrimitive(it)) }
                    put("errorFrame", item.errorFrame?.let { encodePeakCapture(it) } ?: JsonNull)
                    put("bestRepFrame", item.bestRepFrame?.let { encodePeakCapture(it) } ?: JsonNull)
                    put("errorType", JsonPrimitive(item.errorType.name))
                },
            )
        }
    }

    private fun encodeRepTimeline(entries: List<MovitRepTimelineEntry>): JsonArray = buildJsonArray {
        entries.forEach { entry ->
            add(
                buildJsonObject {
                    put("repNumber", JsonPrimitive(entry.repNumber))
                    put("status", JsonPrimitive(entry.status.name))
                    put("durationMs", JsonPrimitive(entry.durationMs))
                    put("errors", buildJsonArray { entry.errors.forEach { add(JsonPrimitive(it)) } })
                    put("score", JsonPrimitive(roundScore(entry.score)))
                    put("setNumber", JsonPrimitive(entry.setNumber))
                    put("isBestRep", JsonPrimitive(entry.isBestRep))
                    put("isWorstRep", JsonPrimitive(entry.isWorstRep))
                    put("isCounted", JsonPrimitive(entry.isCounted))
                    put("isInvalidated", JsonPrimitive(entry.isInvalidated))
                    put("worstState", JsonPrimitive(entry.worstState))
                    put("stateDisplayName", localizedText(entry.stateDisplayName))
                    put("stateIcon", JsonPrimitive(entry.stateIcon))
                    put("quality", JsonPrimitive(entry.quality.name))
                    put("positionWarningCount", JsonPrimitive(entry.positionWarningCount))
                    put("positionErrorCount", JsonPrimitive(entry.positionErrorCount))
                    entry.stateMessage?.let { put("stateMessage", localizedText(it)) }
                    put("frameCapture", entry.frameCapture?.let { encodePeakCapture(it) } ?: JsonNull)
                },
            )
        }
    }

    private fun encodeBestReps(reps: List<MovitBestRepHighlight>): JsonArray = buildJsonArray {
        reps.forEach { rep ->
            add(
                buildJsonObject {
                    put("repNumber", JsonPrimitive(rep.repNumber))
                    put("durationMs", JsonPrimitive(rep.durationMs))
                    put("score", JsonPrimitive(roundScore(rep.score)))
                    put("worstState", JsonPrimitive(rep.worstState.name))
                    put("quality", JsonPrimitive(rep.quality.name))
                    put("reasons", buildJsonArray { rep.reasons.forEach { add(localizedText(it)) } })
                    put("frameCapture", rep.frameCapture?.let { encodePeakCapture(it) } ?: JsonNull)
                },
            )
        }
    }

    private fun encodeWorstRep(rep: MovitWorstRepHighlight): JsonObject = buildJsonObject {
        put("repNumber", JsonPrimitive(rep.repNumber))
        put("durationMs", JsonPrimitive(rep.durationMs))
        put("score", JsonPrimitive(roundScore(rep.score)))
        put("errorCount", JsonPrimitive(rep.errorCount))
        put("worstState", JsonPrimitive(rep.worstState.name))
        put("quality", JsonPrimitive(rep.quality.name))
        put("primaryError", localizedText(rep.primaryError))
        put("frameCapture", rep.frameCapture?.let { encodePeakCapture(it) } ?: JsonNull)
    }

    private fun encodeImprovementTips(tips: List<MovitImprovementTip>): JsonArray = buildJsonArray {
        tips.forEach { tip ->
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(tip.id))
                    put("category", JsonPrimitive(tip.category.name))
                    put("icon", JsonPrimitive(tip.icon))
                    put("title", localizedText(tip.title))
                    put("description", localizedText(tip.description))
                    put("priority", JsonPrimitive(tip.priority))
                    put("severity", JsonPrimitive(tip.severity.name))
                    put("isNextFocus", JsonPrimitive(tip.isNextFocus))
                    put("relatedReps", buildJsonArray { tip.relatedReps.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
    }

    private fun encodeHoldSummary(summary: MovitHoldSummary): JsonObject = buildJsonObject {
        put("targetMs", JsonPrimitive(summary.targetMs))
        put("achievedMs", JsonPrimitive(summary.achievedMs))
        put("percentage", JsonPrimitive(roundScore(summary.percentage)))
        put("formQuality", JsonPrimitive(roundScore(summary.formQuality)))
        put("gracePeriodsUsed", JsonPrimitive(summary.gracePeriodsUsed))
        put(
            "jointBreakdown",
            buildJsonArray {
                summary.jointBreakdown.forEach { joint ->
                    add(
                        buildJsonObject {
                            put("jointCode", JsonPrimitive(joint.jointCode))
                            put("jointName", localizedText(joint.jointName))
                            put("quality", JsonPrimitive(joint.quality.name))
                            put("errorCount", JsonPrimitive(joint.errorCount))
                        },
                    )
                }
            },
        )
        put(
            "sampleFrames",
            buildJsonArray { summary.sampleFrames.forEach { add(encodePeakCapture(it)) } },
        )
    }

    private fun encodePeakCaptures(captures: List<MovitPeakFrameCapture>): JsonArray = buildJsonArray {
        captures.forEach { add(encodePeakCapture(it)) }
    }

    private fun encodeCaptureMetadata(metadata: MovitFrameCaptureMetadata): JsonObject = buildJsonObject {
        put(
            "angles",
            buildJsonObject {
                metadata.angles.forEach { (joint, angle) ->
                    put(joint, JsonPrimitive(angle))
                }
            },
        )
        put("hasError", JsonPrimitive(metadata.hasError))
        put(
            "errorDetails",
            metadata.errorDetails?.let { JsonPrimitive(it) } ?: JsonNull,
        )
    }

    private fun encodeReplayClips(clips: List<MovitRepReplayClip>): JsonArray = buildJsonArray {
        clips.forEach { clip ->
            add(
                buildJsonObject {
                    put("repNumber", JsonPrimitive(clip.repNumber))
                    put("setNumber", JsonPrimitive(clip.setNumber))
                    clip.posterFrameUri?.let { put("posterFrameUri", JsonPrimitive(it)) }
                    put("durationMs", JsonPrimitive(clip.durationMs))
                    put(
                        "frames",
                        buildJsonArray {
                            clip.frames.forEach { frame ->
                                add(
                                    buildJsonObject {
                                        put("frameUri", JsonPrimitive(frame.frameUri))
                                        put("offsetMs", JsonPrimitive(frame.offsetMs))
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    private fun localizedText(text: LocalizedText): JsonObject = buildJsonObject {
        put("ar", JsonPrimitive(text.ar))
        put("en", JsonPrimitive(text.en))
    }

    private fun roundScore(value: Float): Float {
        return (value * 1000f).roundToInt() / 1000f
    }
}
