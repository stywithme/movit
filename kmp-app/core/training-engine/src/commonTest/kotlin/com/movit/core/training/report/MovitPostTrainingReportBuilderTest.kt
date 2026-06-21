package com.movit.core.training.report

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.RepResult
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovitPostTrainingReportBuilderTest {

    @Test
    fun build_prefersSummaryRepDetailsForStateBreakdown() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val upload = sampleUpload()
        val summary = ExerciseWorkoutSummary(
            exerciseName = config.name.en,
            totalReps = 2,
            countedReps = 2,
            invalidatedReps = 0,
            averageScore = 85f,
            countedRatio = 1f,
            durationMs = 84_000L,
            repDetails = listOf(
                com.movit.core.training.engine.RepResult(
                    repNumber = 1,
                    score = 90f,
                    worstState = com.movit.core.training.engine.JointState.PERFECT,
                    isCounted = true,
                ),
                com.movit.core.training.engine.RepResult(
                    repNumber = 2,
                    score = 70f,
                    worstState = com.movit.core.training.engine.JointState.DANGER,
                    isCounted = false,
                    isInvalidated = true,
                ),
            ),
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = summary,
            exerciseConfig = config,
        )
        assertEquals(1, report.summary.stateBreakdown.perfectCount)
        assertEquals(1, report.summary.stateBreakdown.dangerCount)
    }

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
                errorType = "left_knee:DANGER:45",
                metadata = MovitFrameCaptureMetadata(
                    angles = mapOf("left_knee" to 45.0),
                    hasError = true,
                    errorDetails = "left_knee:DANGER:45",
                ),
            ),
        )
        val replayClips = listOf(
            MovitRepReplayClip(
                repNumber = 2,
                frames = listOf(
                    MovitReplayFrameRef("/data/replay-0.jpg", 0L),
                    MovitReplayFrameRef("/data/replay-1.jpg", 180L),
                ),
            ),
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = summary,
            exerciseConfig = config,
            peakFrameCaptures = captures,
            repReplayClips = replayClips,
        )
        val encoded = PostTrainingReportLegacyJson.encode(report).toString()
        assertTrue(encoded.contains("cap-danger-1"))
        assertTrue(encoded.contains("DANGER_FRAME"))
        assertTrue(encoded.contains("/data/frame.jpg"))
        assertTrue(encoded.contains("left_knee"))
        assertTrue(encoded.contains("45.0"))
        assertTrue(encoded.contains("repReplayClips"))
        assertTrue(encoded.contains("/data/replay-1.jpg"))
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
    fun build_populatesRichAnalysisWhenRepDetailsPresent() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val upload = sampleUpload()
        val kneeError = JointError(
            jointCode = "left_knee",
            errorType = ErrorType.TOO_LOW,
            actualAngle = 55.0,
            expectedMin = 70.0,
            expectedMax = 100.0,
            state = JointState.WARNING,
        )
        val summary = ExerciseWorkoutSummary(
            exerciseName = config.name.en,
            totalReps = 3,
            countedReps = 2,
            invalidatedReps = 1,
            averageScore = 72f,
            countedRatio = 2f / 3f,
            durationMs = 12_000L,
            repDetails = listOf(
                RepResult(1, 88f, JointState.NORMAL, true, phaseTimings = mapOf("down" to 1200L)),
                RepResult(
                    2,
                    40f,
                    JointState.WARNING,
                    false,
                    errors = listOf(kneeError),
                    phaseTimings = mapOf("down" to 900L),
                ),
                RepResult(3, 0f, JointState.DANGER, false, isInvalidated = true, phaseTimings = mapOf("down" to 800L)),
            ),
        )
        val report = MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = summary,
            exerciseConfig = config,
        )
        assertEquals(3, report.repTimeline.size)
        assertTrue(report.errorAnalysis.isNotEmpty())
        assertEquals("left_knee", report.errorAnalysis.first().jointCode)
        assertTrue(report.improvementTips.isNotEmpty())
        assertNotNull(report.overallQuality)
        assertNotNull(report.exerciseConfig)
        assertFalse(report.bestReps.isEmpty())
        assertNotNull(report.worstRep)
        val encoded = PostTrainingReportLegacyJson.encode(report).toString()
        assertTrue(encoded.contains("\"errorAnalysis\":[{"))
        assertTrue(encoded.contains("\"repTimeline\":[{"))
        assertTrue(encoded.contains("\"overallQuality\":{"))
        assertTrue(encoded.contains("\"exerciseConfig\":{"))
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
