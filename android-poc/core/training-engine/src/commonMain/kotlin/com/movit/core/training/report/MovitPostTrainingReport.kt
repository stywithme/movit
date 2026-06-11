package com.movit.core.training.report

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
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
)

object MovitPostTrainingReportBuilder {
    fun build(
        upload: WorkoutUpload,
        summary: ExerciseWorkoutSummary,
        exerciseConfig: ExerciseConfig,
        exerciseSlug: String = upload.exerciseId,
        sessionQuality: SessionQualityMeta? = null,
        reportId: String = upload.id,
        workoutId: String = upload.id,
        timestamp: Long = upload.timestamp,
    ): MovitPostTrainingReport {
        val stateBreakdown = MovitStateBreakdown.fromRepMetrics(upload.repMetrics)
        val rating = MovitPerformanceRating.fromScoreAndRatio(summary.averageScore, summary.countedRatio)
        val metrics = upload.executionMetrics
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
            weightKg = upload.weightKg,
            weightUnit = upload.weightUnit,
            avgRom = metrics.avgRom.takeIf { it > 0 }?.let { it / 10f },
            avgSymmetry = metrics.avgSymmetry?.let { it / 10f },
            avgStability = metrics.avgStability.takeIf { it > 0 }?.let { it / 10f },
            formConsistency = metrics.formConsistency?.let { it / 10f },
            fatigueIndex = metrics.fatigueIndex?.toInt(),
        )
        val executionQuality = sessionQuality?.let { MovitExecutionQuality.fromSessionQuality(it) }
            ?: MovitExecutionQuality(
                visibilityPauseCount = 0,
                totalInvisibleMs = 0L,
                cameraWarningCount = 0,
                overallQuality = MovitTrackingQualityLevel.EXCELLENT,
            )
        return MovitPostTrainingReport(
            id = reportId,
            workoutId = workoutId,
            exerciseId = upload.exerciseId,
            exerciseName = exerciseConfig.name,
            timestamp = timestamp,
            summary = performanceSummary,
            executionQuality = executionQuality,
            sessionQuality = sessionQuality,
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
        put("dangerAlerts", JsonArray(emptyList()))
        put("perfectMoments", JsonArray(emptyList()))
        put("bestReps", JsonArray(emptyList()))
        put("worstRep", JsonNull)
        put("errorAnalysis", JsonArray(emptyList()))
        put("repTimeline", JsonArray(emptyList()))
        put("consistency", JsonNull)
        put("improvementTips", JsonArray(emptyList()))
        put("frameCaptures", encodePeakCaptures(report.peakFrameCaptures))
        put("holdSummary", JsonNull)
        put("quickInsight", JsonNull)
        put("heroFrame", JsonNull)
        put("performanceMetrics", JsonNull)
        put("overallQuality", JsonNull)
        put("exerciseConfig", JsonNull)
        put("setSummaries", JsonArray(emptyList()))
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
    }

    private fun encodePeakCaptures(captures: List<MovitPeakFrameCapture>): JsonArray = buildJsonArray {
        captures.forEach { capture ->
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(capture.id))
                    put("repNumber", JsonPrimitive(capture.repNumber))
                    put("phase", JsonPrimitive(capture.phaseCode.toInt()))
                    put("timestamp", JsonPrimitive(capture.capturedAtMs))
                    put("captureType", JsonPrimitive(capture.captureType.name))
                    put("errorType", JsonNull)
                    put("frameUri", JsonPrimitive(capture.localPath))
                    put("thumbnailUri", JsonPrimitive(capture.thumbnailPath ?: capture.localPath))
                    put(
                        "metadata",
                        buildJsonObject {
                            put("angles", buildJsonObject { })
                            put("hasError", JsonPrimitive(false))
                            put("errorDetails", JsonNull)
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
