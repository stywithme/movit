package com.movit.core.data.repository

import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsPatchWeightedAverageTest {

    @Test
    fun patchExerciseMetricsFromUpload_computesSetWeightedAverageFormScore() {
        val platform = FakeMovitPlatformBindings()
        val localStore = testLocalStore(platform)
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

        repo.patchExerciseMetricsFromUpload(sampleUpload(formScore = 80f, countedReps = 10))
        var metrics = repo.readCachedExerciseMetrics("squat")
        assertEquals(80f, metrics?.summary?.averageFormScore)
        assertEquals(1, metrics?.summary?.setsCompleted)
        assertEquals(10, metrics?.summary?.totalReps)

        repo.patchExerciseMetricsFromUpload(sampleUpload(formScore = 60f, countedReps = 8))
        metrics = repo.readCachedExerciseMetrics("squat")
        assertEquals(70f, metrics?.summary?.averageFormScore)
        assertEquals(2, metrics?.summary?.setsCompleted)
        assertEquals(18, metrics?.summary?.totalReps)
    }

    @Test
    fun computeWeightedAverageFormScore_firstSetUsesIncomingScore() {
        assertEquals(85f, computeWeightedAverageFormScore(null, 0, 85f))
    }

    @Test
    fun computeWeightedAverageFormScore_twoSetsAveragesBySetCount() {
        assertEquals(75f, computeWeightedAverageFormScore(80f, 1, 70f))
    }

    private fun sampleUpload(formScore: Float, countedReps: Int): WorkoutExecutionUploadRequestDto =
        WorkoutExecutionUploadRequestDto(
            id = "exec-${formScore.toInt()}-$countedReps",
            exerciseId = "squat",
            timestamp = 1L,
            durationMs = 30_000,
            totalReps = countedReps,
            countedReps = countedReps,
            invalidReps = 0,
            executionMetrics = ExecutionMetricsDto(
                avgRom = 90f,
                avgStability = 80f,
                avgFormScore = formScore,
                avgAlignmentAccuracy = 85f,
                totalTUT = 8_000,
            ),
        )
}
