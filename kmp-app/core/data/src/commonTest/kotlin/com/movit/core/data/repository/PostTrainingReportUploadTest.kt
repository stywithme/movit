package com.movit.core.data.repository

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.StateCode
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.report.MovitPostTrainingReportBuilder
import com.movit.core.training.report.PostTrainingReportLegacyJson
import com.movit.core.training.report.SessionQualityMeta
import com.movit.core.training.session.ExerciseWorkoutSummary
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostTrainingReportUploadTest {
    @Test
    fun legacyReportJson_roundTripsThroughUploadMapper() {
        val config = ExerciseConfigParser.parseConfigJson(readSquatFixture())
        val upload = WorkoutUpload(
            id = "exec-upload-1",
            exerciseId = "bodyweight-squat",
            timestamp = 1_700_000_000_000L,
            durationMs = 60_000,
            totalReps = 2,
            countedReps = 2,
            invalidReps = 0,
            repMetrics = listOf(
                RepMetricsData(
                    num = 1,
                    durationMs = 2500,
                    worstState = StateCode.NORMAL,
                    score = 850,
                    metrics = RepMetrics(
                        rom = 900,
                        symmetry = null,
                        stability = 800,
                        tempo = listOf(1000, 300, 900),
                        velocity = 40,
                        formScore = 850,
                        alignmentAccuracy = 850,
                    ),
                ),
            ),
            executionMetrics = WorkoutExecutionMetrics(
                avgRom = 900,
                avgSymmetry = null,
                avgStability = 800,
                avgTempo = listOf(1000, 300, 900),
                avgVelocity = 40,
                avgFormScore = 850,
                avgAlignmentAccuracy = 850,
                totalTUT = 2500,
                totalVolume = null,
                maxWeight = null,
                est1RM = null,
            ),
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = ExerciseWorkoutSummary(
                exerciseName = config.name.en,
                totalReps = 2,
                countedReps = 2,
                invalidatedReps = 0,
                averageScore = 85f,
                countedRatio = 1f,
                durationMs = 60_000L,
            ),
            exerciseConfig = config,
            sessionQuality = SessionQualityMeta.fromFrameStats(100, 95, 5, 0.9f),
        )
        val legacyJson = PostTrainingReportLegacyJson.encode(report)
        val request = WorkoutUploadMapper.toUploadRequest(upload, legacyReport = legacyJson)
        assertNotNull(request.legacyReport)
        assertNotNull(request.legacyReport!!.jsonObject["summary"])
        assertTrue(request.legacyReport.toString().contains("sessionQuality"))
    }

    private fun readSquatFixture(): String {
        val resourcePath = "fixtures/exercises/squat.json"
        Thread.currentThread().contextClassLoader?.getResource(resourcePath)?.readText()?.let { return it }
        val candidates = listOf(
            "src/commonTest/resources/$resourcePath",
            "core/training-engine/src/commonTest/resources/$resourcePath",
            "../core/training-engine/src/commonTest/resources/$resourcePath",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: $resourcePath")
    }
}
