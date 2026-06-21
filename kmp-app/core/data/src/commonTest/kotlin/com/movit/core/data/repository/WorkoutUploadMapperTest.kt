package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.report.MovitSessionReportBuilder
import com.movit.core.training.session.ExerciseWorkoutSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutUploadMapperTest {
    @Test
    fun toUploadRequest_scalesInternalMetricsToDto() {
        val upload = sampleUpload()
        val mapped = WorkoutUploadMapper.toUploadRequest(upload)
        val rep = mapped.repMetrics.first()
        assertEquals(8.5f, rep.score)
        assertEquals(9.5f, rep.metrics.rom)
        assertEquals(8.6f, rep.metrics.formScore)
        assertEquals(9.2f, mapped.executionMetrics.avgRom)
    }

    @Test
    fun movitSessionReport_encodesLegacyWorkoutReportKeys() {
        val upload = sampleUpload()
        val report = MovitSessionReportBuilder.fromExerciseExecution(
            upload = upload,
            summary = ExerciseWorkoutSummary(
                exerciseName = "Squat",
                totalReps = 10,
                countedReps = 9,
                invalidatedReps = 1,
                averageScore = 85f,
                countedRatio = 0.9f,
                durationMs = 84_000L,
            ),
            exerciseSlug = "bodyweight-squat",
            exerciseName = LocalizedText(en = "Squat"),
        )
        val json = MovitJson.encodeToString(
            com.movit.core.training.report.MovitSessionReport.serializer(),
            report,
        )
        assertTrue(json.contains("totalExercises"))
        assertTrue(json.contains("exerciseReports"))
        assertTrue(json.contains("executionIds"))
    }

    private fun sampleUpload(): WorkoutUpload = WorkoutUpload(
        id = "exec-001",
        exerciseId = "bodyweight-squat",
        timestamp = 1_700_000_000_000L,
        durationMs = 84_000,
        totalReps = 10,
        countedReps = 9,
        invalidReps = 1,
        repMetrics = listOf(
            RepMetricsData(
                num = 1,
                durationMs = 2800,
                worstState = 1,
                score = 85,
                metrics = RepMetrics(
                    rom = 95,
                    symmetry = 82,
                    stability = 78,
                    tempo = listOf(1200, 400, 900),
                    velocity = 45,
                    formScore = 86,
                    alignmentAccuracy = 90,
                ),
            ),
        ),
        executionMetrics = WorkoutExecutionMetrics(
            avgRom = 92,
            avgSymmetry = 80,
            avgStability = 75,
            avgTempo = listOf(1100, 350, 850),
            avgVelocity = 42,
            avgFormScore = 84,
            avgAlignmentAccuracy = 88,
            totalTUT = 8400,
            totalVolume = null,
            maxWeight = null,
            est1RM = null,
        ),
    )
}
