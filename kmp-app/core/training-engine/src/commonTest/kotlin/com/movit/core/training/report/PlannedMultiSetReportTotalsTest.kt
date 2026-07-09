package com.movit.core.training.report

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.session.ExerciseWorkoutSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class PlannedMultiSetReportTotalsTest {

    @Test
    fun threeSets_sameExercise_totalsThreeOfThree() {
        val slug = "bodyweight-squat"
        val name = LocalizedText(en = "Squat")
        val totalSets = 3

        var report = MovitSessionReportBuilder.fromExerciseExecution(
            upload = upload("exec-set-1"),
            summary = summary(reps = 10, score = 80f),
            exerciseSlug = slug,
            exerciseName = name,
            setsCompleted = 1,
            totalSets = totalSets,
        )
        assertEquals(1, report.totalSetsCompleted)
        assertEquals(3, report.totalSetsPlanned)
        assertEquals(1, report.exerciseReports.single().setsCompleted)

        report = MovitSessionReportBuilder.mergeExercise(
            existing = report,
            upload = upload("exec-set-2"),
            summary = summary(reps = 10, score = 90f),
            exerciseSlug = slug,
            exerciseName = name,
            setsCompleted = 2,
            totalSets = totalSets,
        )
        assertEquals(2, report.totalSetsCompleted)
        assertEquals(3, report.totalSetsPlanned)
        assertEquals(1, report.exerciseReports.size)
        assertEquals(2, report.exerciseReports.single().setsCompleted)

        report = MovitSessionReportBuilder.mergeExercise(
            existing = report,
            upload = upload("exec-set-3"),
            summary = summary(reps = 10, score = 70f),
            exerciseSlug = slug,
            exerciseName = name,
            setsCompleted = 3,
            totalSets = totalSets,
        )
        assertEquals(3, report.totalSetsCompleted)
        assertEquals(3, report.totalSetsPlanned)
        assertEquals(30, report.totalReps)
        assertEquals(1, report.exerciseReports.size)
        assertEquals(3, report.exerciseReports.single().setsCompleted)
        assertEquals(3, report.exerciseReports.single().totalSets)
    }

    private fun upload(id: String): WorkoutUpload = WorkoutUpload(
        id = id,
        exerciseId = "bodyweight-squat",
        timestamp = 1_700_000_000_000L,
        durationMs = 60_000,
        totalReps = 10,
        countedReps = 10,
        invalidReps = 0,
        repMetrics = emptyList(),
        executionMetrics = WorkoutExecutionMetrics(
            avgRom = 0,
            avgSymmetry = null,
            avgStability = null,
            avgTempo = emptyList(),
            avgVelocity = 0,
            avgFormScore = 0,
            avgAlignmentAccuracy = null,
            totalTUT = 0,
            totalVolume = null,
            maxWeight = null,
            est1RM = null,
        ),
    )

    private fun summary(reps: Int, score: Float): ExerciseWorkoutSummary = ExerciseWorkoutSummary(
        exerciseName = "Squat",
        totalReps = reps,
        countedReps = reps,
        invalidatedReps = 0,
        averageScore = score,
        countedRatio = 1f,
        durationMs = 60_000L,
    )
}
