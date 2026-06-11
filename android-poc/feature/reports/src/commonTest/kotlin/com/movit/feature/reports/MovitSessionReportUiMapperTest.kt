package com.movit.feature.reports

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.report.MovitPerformanceRating
import com.movit.core.training.report.MovitPerformanceSummary
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitSessionReportBuilder
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

class MovitSessionReportUiMapperTest {

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
