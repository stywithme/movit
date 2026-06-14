package com.movit.core.training.report

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.StateCode
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.testing.readExerciseFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovitPostTrainingReportBuilderTest {

    @Test
    fun build_mapsUploadMetricsToSummary() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val upload = sampleUpload()
        val summary = ExerciseWorkoutSummary(
            exerciseName = config.name.en,
            totalReps = 3,
            countedReps = 3,
            invalidatedReps = 0,
            averageScore = 85f,
            countedRatio = 1f,
            durationMs = 84_000L,
        )
        val quality = SessionQualityMeta.fromFrameStats(
            framesOffered = 120,
            framesRecorded = 114,
            framesDropped = 6,
            jointCoverageRatio = 0.92f,
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = summary,
            exerciseConfig = config,
            sessionQuality = quality,
        )
        assertEquals("bodyweight-squat", report.exerciseId)
        assertEquals(3, report.summary.totalReps)
        assertEquals(MovitPerformanceRating.EXCELLENT, report.summary.rating)
        assertEquals(85f, report.summary.averageScore)
        assertEquals(9.2f, report.summary.avgRom)
        assertNotNull(report.sessionQuality)
        assertEquals(5f, report.sessionQuality!!.frameDropRate)
    }

    @Test
    fun legacyJson_encodesPeakFrameCaptures() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val upload = sampleUpload()
        val summary = ExerciseWorkoutSummary(
            exerciseName = config.name.en,
            totalReps = 3,
            countedReps = 3,
            invalidatedReps = 0,
            averageScore = 85f,
            countedRatio = 1f,
            durationMs = 84_000L,
        )
        val captures = listOf(
            MovitPeakFrameCapture(
                id = "cap-danger-1",
                repNumber = 2,
                phaseCode = 3,
                capturedAtMs = 1_700_000_000_100L,
                captureType = MovitPeakCaptureType.DANGER_FRAME,
                localPath = "/data/frame.jpg",
                thumbnailPath = "/data/frame_thumb.jpg",
            ),
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = summary,
            exerciseConfig = config,
            peakFrameCaptures = captures,
        )
        val encoded = PostTrainingReportLegacyJson.encode(report).toString()
        assertTrue(encoded.contains("cap-danger-1"))
        assertTrue(encoded.contains("DANGER_FRAME"))
        assertTrue(encoded.contains("/data/frame.jpg"))
    }

    @Test
    fun legacyJson_matchesGoldenParityFields() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val upload = sampleUpload()
        val summary = ExerciseWorkoutSummary(
            exerciseName = config.name.en,
            totalReps = 3,
            countedReps = 3,
            invalidatedReps = 0,
            averageScore = 85f,
            countedRatio = 1f,
            durationMs = 84_000L,
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = summary,
            exerciseConfig = config,
            reportId = "report-golden-001",
            workoutId = "workout-golden-001",
            timestamp = 1_700_000_000_000L,
        )
        val encoded = PostTrainingReportLegacyJson.encode(report)
        val golden = Json.parseToJsonElement(readReportFixture("post-training-squat-golden.json")).jsonObject
        PostTrainingReportFieldComparator.assertParityFields(golden, encoded.jsonObject)
        assertTrue(encoded.toString().contains("report-golden-001"))
    }

    @Test
    fun sessionQualityMeta_computesDropRate() {
        val meta = SessionQualityMeta.fromFrameStats(
            framesOffered = 200,
            framesRecorded = 180,
            framesDropped = 20,
            jointCoverageRatio = 0.88f,
        )
        assertEquals(10f, meta.frameDropRate)
    }

    private fun sampleUpload(): WorkoutUpload = WorkoutUpload(
        id = "exec-golden-001",
        exerciseId = "bodyweight-squat",
        timestamp = 1_700_000_000_000L,
        durationMs = 84_000,
        totalReps = 3,
        countedReps = 3,
        invalidReps = 0,
        repMetrics = listOf(
            rep(1, StateCode.NORMAL, 85),
            rep(2, StateCode.PERFECT, 90),
            rep(3, StateCode.NORMAL, 80),
        ),
        executionMetrics = WorkoutExecutionMetrics(
            avgRom = 92,
            avgSymmetry = null,
            avgStability = 780,
            avgTempo = listOf(1100, 350, 850),
            avgVelocity = 42,
            avgFormScore = 850,
            avgAlignmentAccuracy = 880,
            totalTUT = 8400,
            totalVolume = null,
            maxWeight = null,
            est1RM = null,
        ),
    )

    private fun rep(num: Int, worst: Byte, score: Int): RepMetricsData = RepMetricsData(
        num = num,
        durationMs = 2800,
        worstState = worst,
        score = (score * 10).toShort(),
        metrics = RepMetrics(
            rom = 920,
            symmetry = null,
            stability = 780,
            tempo = listOf(1100, 350, 850),
            velocity = 42,
            formScore = (score * 10).toShort(),
            alignmentAccuracy = 880,
        ),
    )
}

private fun readReportFixture(name: String): String {
    val resourcePath = "fixtures/reports/$name"
    Thread.currentThread().contextClassLoader?.getResource(resourcePath)?.readText()?.let { return it }
    val candidates = listOf(
        "src/commonTest/resources/$resourcePath",
        "core/training-engine/src/commonTest/resources/$resourcePath",
    )
    for (relative in candidates) {
        val file = java.io.File(relative)
        if (file.isFile) return file.readText()
    }
    error("Missing fixture: $resourcePath")
}
