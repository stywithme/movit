package com.movit.feature.reports

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.report.MovitExecutionQuality
import com.movit.core.training.report.MovitPerformanceRating
import com.movit.core.training.report.MovitPerformanceSummary
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitStateBreakdown
import com.movit.core.training.report.MovitTrackingQualityLevel
import com.movit.resources.strings.ReportDetailStrings
import kotlinx.coroutines.runBlocking
import com.movit.core.training.report.MovitSessionReportBuilder
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.journal.WorkoutUpload
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingSessionReportCacheTest {
    @AfterTest
    fun tearDown() {
        TrainingSessionReportCache.clearAll()
    }

    @Test
    fun putAndGet_roundTripsPostTrainingReport() {
        val report = samplePostTrainingReport()
        TrainingSessionReportCache.put("upload-42", report)
        assertEquals(report, TrainingSessionReportCache.get("upload-42"))
    }

    @Test
    fun put_evictsOldestWhenOverCapacity() {
        repeat(11) { index ->
            TrainingSessionReportCache.put("upload-$index", samplePostTrainingReport("upload-$index"))
        }
        assertNull(TrainingSessionReportCache.get("upload-0"))
        assertEquals(
            samplePostTrainingReport("upload-10"),
            TrainingSessionReportCache.get("upload-10"),
        )
    }

    @Test
    fun rekeyPostTraining_movesReportToServerId() {
        val report = samplePostTrainingReport("local-upload")
        TrainingSessionReportCache.put("local-upload", report)
        TrainingSessionReportCache.rekeyPostTraining("local-upload", "server-report-42")
        assertNull(TrainingSessionReportCache.get("local-upload"))
        assertEquals("server-report-42", TrainingSessionReportCache.get("server-report-42")?.id)
    }

    @Test
    fun putSession_roundTripsForPlannedWorkoutNavigation() = runBlocking {
        val upload = WorkoutUpload(
            id = "exec-1",
            exerciseId = "squat",
            timestamp = 1L,
            durationMs = 30_000,
            totalReps = 8,
            countedReps = 8,
            invalidReps = 0,
            executionMetrics = com.movit.core.training.journal.WorkoutExecutionMetrics(
                avgRom = 90,
                avgSymmetry = 85,
                avgStability = 80,
                avgTempo = listOf(1000, 300, 800),
                avgVelocity = 40,
                avgFormScore = 80,
                avgAlignmentAccuracy = 88,
                totalTUT = 8000,
                totalVolume = null,
                maxWeight = null,
                est1RM = null,
            ),
        )
        val summary = ExerciseWorkoutSummary(
            exerciseName = "Squat",
            totalReps = 8,
            countedReps = 8,
            invalidatedReps = 0,
            averageScore = 80f,
            countedRatio = 1f,
            durationMs = 30_000L,
        )
        val session = MovitSessionReportBuilder.fromExerciseExecution(
            upload = upload,
            summary = summary,
            exerciseSlug = "squat",
            exerciseName = LocalizedText(en = "Squat", ar = "قرفصاء"),
        )
        TrainingSessionReportCache.putSession("planned-pw-1", session)
        val strings = ReportDetailStrings.load("en")
        val ui = MovitSessionReportUiMapper.mapSessionOverview(session, strings, "planned-pw-1")
        assertEquals("planned-pw-1", ui.id)
        assertEquals(80, ui.formScore)
    }

    @Test
    fun sharedRepository_readsCachedSessionReport() = runBlocking {
        val report = samplePostTrainingReport()
        TrainingSessionReportCache.put("upload-42", report)
        val strings = ReportDetailStrings.load("en")
        val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)
        assertEquals("upload-42", ui.id)
        assertEquals(88, ui.formScore)
    }

    private fun samplePostTrainingReport(uploadId: String = "upload-42"): MovitPostTrainingReport = MovitPostTrainingReport(
        id = uploadId,
        workoutId = uploadId,
        exerciseId = "squat",
        exerciseName = LocalizedText(en = "Squat", ar = "قرفصاء"),
        timestamp = 1L,
        summary = MovitPerformanceSummary(
            totalReps = 8,
            durationMs = 60_000L,
            rating = MovitPerformanceRating.GOOD,
            motivationalMessage = LocalizedText(en = "Good", ar = "جيد"),
            countedReps = 8,
            invalidatedReps = 0,
            averageScore = 88f,
            countedRatio = 1f,
            stateBreakdown = MovitStateBreakdown(normalCount = 8),
            shouldCelebrate = false,
        ),
        executionQuality = MovitExecutionQuality(
            visibilityPauseCount = 0,
            totalInvisibleMs = 0,
            cameraWarningCount = 0,
            overallQuality = MovitTrackingQualityLevel.GOOD,
        ),
    )
}
