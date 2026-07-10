package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P0.1: null stability/alignment must stay null in the upload JSON
 * (never coerced to 0.0 — that would pollute backend averages).
 */
class WorkoutUploadMapperNullableMetricsTest {
    @Test
    fun toUploadRequest_keepsNullStabilityAndAlignmentInJson() {
        val mapped = WorkoutUploadMapper.toUploadRequest(sampleUploadWithNullStability())
        assertNull(mapped.repMetrics.first().metrics.stability)
        assertNull(mapped.repMetrics.first().metrics.alignmentAccuracy)
        assertNull(mapped.executionMetrics.avgStability)
        assertNull(mapped.executionMetrics.avgAlignmentAccuracy)

        val json = MovitJson.encodeToString(
            WorkoutExecutionUploadRequestDto.serializer(),
            mapped,
        )
        assertFalse(json.contains("\"stability\":0"), json)
        assertFalse(json.contains("\"alignmentAccuracy\":0"), json)
        assertFalse(json.contains("\"avgStability\":0"), json)
        assertFalse(json.contains("\"avgAlignmentAccuracy\":0"), json)
        // kotlinx.serialization omits nulls by default for optional fields —
        // either omitted or explicit null is fine; zeros are not.
        assertTrue(
            !json.contains("\"stability\":") || json.contains("\"stability\":null"),
            json,
        )
        assertEquals(9.5f, mapped.repMetrics.first().metrics.rom)
    }

    private fun sampleUploadWithNullStability(): WorkoutUpload = WorkoutUpload(
        id = "exec-null-metrics",
        exerciseId = "bodyweight-squat",
        timestamp = 1_700_000_000_000L,
        durationMs = 3_000,
        totalReps = 1,
        countedReps = 1,
        invalidReps = 0,
        repMetrics = listOf(
            RepMetricsData(
                num = 1,
                durationMs = 2_000,
                worstState = 0,
                score = 85,
                metrics = RepMetrics(
                    rom = 95,
                    symmetry = null,
                    stability = null,
                    tempo = listOf(1000, 0, 1000),
                    velocity = null,
                    formScore = 86,
                    alignmentAccuracy = null,
                ),
            ),
        ),
        executionMetrics = WorkoutExecutionMetrics(
            avgRom = 95,
            avgSymmetry = null,
            avgStability = null,
            avgTempo = listOf(1000, 0, 1000),
            avgVelocity = null,
            avgFormScore = 86,
            avgAlignmentAccuracy = null,
            totalTUT = 2000,
            totalVolume = null,
            maxWeight = null,
            est1RM = null,
        ),
    )
}
