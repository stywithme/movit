package com.movit.feature.reports

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCapture
import com.movit.core.training.report.MovitPerformanceRating
import com.movit.core.training.report.MovitPerformanceSummary
import com.movit.core.training.report.MovitHoldSummary
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitRepTimelineEntry
import com.movit.core.training.report.MovitSessionReportBuilder
import kotlinx.serialization.json.Json
import com.movit.core.training.report.MovitStateBreakdown
import com.movit.core.training.report.MovitTrackingQualityLevel
import com.movit.core.training.report.MovitExecutionQuality
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.resources.strings.ReportDetailStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MovitSessionReportUiMapperTest {

    @Test
    fun mapPostTraining_mapsPeakFrameEvidence() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val report = samplePostTrainingReport().copy(
                peakFrameCaptures = listOf(
                    MovitPeakFrameCapture(
                        id = "cap-1",
                        repNumber = 2,
                        phaseCode = 3,
                        capturedAtMs = 1L,
                        captureType = MovitPeakCaptureType.DANGER_FRAME,
                        localPath = "/tmp/danger.jpg",
                        thumbnailPath = "/tmp/danger-thumb.jpg",
                    ),
                ),
            )
            val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)
            assertEquals(1, ui.frameEvidence.size)
            assertEquals("file:///tmp/danger-thumb.jpg", ui.heroFramePath)
        }
    }

    @Test
    fun mapPostTraining_enrichedReport_mapsAnalysisTipsAndRepCompare() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val report = readEnrichedReportFixture()
            val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)

            assertEquals(2, ui.joints.size)
            assertEquals("Left knee", ui.joints.first().label)
            assertTrue(ui.joints.first().scorePercent in 1..100)

            assertEquals(2, ui.repCompare.size)
            assertEquals(95, ui.repCompare.first().score)
            assertTrue(ui.repCompare.first().isBest)
            assertEquals(72, ui.repCompare.last().score)
            assertTrue(!ui.repCompare.last().isBest)

            assertEquals(2, ui.formBySetLabels.size)
            assertEquals(2, ui.formBySetValues.size)

            assertTrue(ui.tips.isNotEmpty())
            assertEquals("Depth focus", ui.tips.first().title)

            assertTrue(ui.fatigueMessage.contains("Form dropped") || ui.fatigueMessage.contains("dropped"))
            assertEquals(ReportJointsEmptyReason.Generic, ui.jointsEmptyReason)
        }
    }

    @Test
    fun mapPostTraining_holdSummary_mapsDurationAndOverview() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val report = samplePostTrainingReport().copy(
                holdSummary = MovitHoldSummary(
                    targetMs = 30_000L,
                    achievedMs = 24_000L,
                    percentage = 80f,
                    formQuality = 88f,
                ),
            )
            val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)
            assertEquals("24s", ui.durationLabel)
            assertEquals(88, ui.formScore)
            assertTrue(ui.overviewInsightMessage.contains("80"))
        }
    }

    @Test
    fun mapPostTraining_sparseReport_keepsSessionUntrackedJoints() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val ui = MovitSessionReportUiMapper.mapPostTraining(samplePostTrainingReport(), strings)
            assertTrue(ui.joints.isEmpty())
            assertTrue(ui.repCompare.isEmpty())
            assertTrue(ui.tips.isEmpty())
            assertEquals(ReportJointsEmptyReason.SessionUntracked, ui.jointsEmptyReason)
        }
    }

    @Test
    fun mapPostTraining_enrichmentMapper_buildsRepCompareFromTimelineOnly() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val report = samplePostTrainingReport().copy(
                repTimeline = listOf(
                    MovitRepTimelineEntry(repNumber = 1, durationMs = 2000, score = 90f, setNumber = 1),
                    MovitRepTimelineEntry(repNumber = 2, durationMs = 2100, score = 60f, setNumber = 1),
                ),
            )
            val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)
            assertEquals(2, ui.repCompare.size)
            assertEquals(90, ui.repCompare.first().score)
            assertEquals(60, ui.repCompare.last().score)
        }
    }

    @Test
    fun mapPostTraining_doesNotMutateDomainModel() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val report = samplePostTrainingReport()
            val before = report.copy()
            val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)
            assertEquals(before, report)
            assertEquals(85, ui.formScore)
            assertEquals("Squat", ui.exerciseName)
            assertNotEquals(report.id, ui.exerciseName)
        }
    }

    @Test
    fun mapSessionDay_mapsEachExerciseRow() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val upload = WorkoutUpload(
                id = "exec-1",
                exerciseId = "bodyweight-squat",
                timestamp = 1L,
                durationMs = 1000,
                totalReps = 5,
                countedReps = 5,
                invalidReps = 0,
                executionMetrics = WorkoutExecutionMetrics(
                    avgRom = 90,
                    avgSymmetry = null,
                    avgStability = 800,
                    avgTempo = listOf(1, 1, 1),
                    avgVelocity = null,
                    avgFormScore = 820,
                    avgAlignmentAccuracy = 800,
                    totalTUT = 1000,
                    totalVolume = null,
                    maxWeight = null,
                    est1RM = null,
                ),
            )
            val session = MovitSessionReportBuilder.fromExerciseExecution(
                upload = upload,
                summary = ExerciseWorkoutSummary(
                    exerciseName = "Squat",
                    totalReps = 5,
                    countedReps = 5,
                    invalidatedReps = 0,
                    averageScore = 82f,
                    countedRatio = 1f,
                    durationMs = 1000L,
                ),
                exerciseSlug = "bodyweight-squat",
                exerciseName = LocalizedText(en = "Squat"),
            )
            val rows = MovitSessionReportUiMapper.mapSessionDay(session, strings)
            assertEquals(1, rows.size)
            assertEquals(82, rows.first().formScore)
        }
    }

    private fun readEnrichedReportFixture(): MovitPostTrainingReport {
        val json = readReportFixture("post-training-enriched-squat.json")
        return Json { ignoreUnknownKeys = true }.decodeFromString(MovitPostTrainingReport.serializer(), json)
    }

    private fun readReportFixture(name: String): String {
        val resourcePath = "fixtures/reports/$name"
        Thread.currentThread().contextClassLoader?.getResource(resourcePath)?.readText()?.let { return it }
        val candidates = listOf(
            "src/commonTest/resources/$resourcePath",
            "feature/reports/src/commonTest/resources/$resourcePath",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: $resourcePath")
    }

    private fun samplePostTrainingReport(): MovitPostTrainingReport = MovitPostTrainingReport(
        id = "report-1",
        workoutId = "workout-1",
        exerciseId = "bodyweight-squat",
        exerciseName = LocalizedText(en = "Squat", ar = "سكوات"),
        timestamp = 1L,
        summary = MovitPerformanceSummary(
            totalReps = 10,
            durationMs = 60_000L,
            rating = MovitPerformanceRating.GOOD,
            motivationalMessage = LocalizedText(en = "Great", ar = "رائع"),
            countedReps = 9,
            invalidatedReps = 1,
            averageScore = 85f,
            countedRatio = 0.9f,
            stateBreakdown = MovitStateBreakdown(normalCount = 9, dangerCount = 1),
        ),
        executionQuality = MovitExecutionQuality(
            visibilityPauseCount = 0,
            totalInvisibleMs = 0,
            cameraWarningCount = 0,
            overallQuality = MovitTrackingQualityLevel.EXCELLENT,
        ),
    )
}
