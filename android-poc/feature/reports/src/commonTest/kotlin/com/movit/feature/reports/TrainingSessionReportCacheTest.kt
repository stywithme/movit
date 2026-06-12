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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun sharedRepository_readsCachedSessionReport() = runBlocking {
        val report = samplePostTrainingReport()
        TrainingSessionReportCache.put("upload-42", report)
        val strings = ReportDetailStrings.load("en")
        val ui = MovitSessionReportUiMapper.mapPostTraining(report, strings)
        assertEquals("upload-42", ui.id)
        assertEquals(88, ui.formScore)
    }

    private fun samplePostTrainingReport(): MovitPostTrainingReport = MovitPostTrainingReport(
        id = "upload-42",
        workoutId = "upload-42",
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
