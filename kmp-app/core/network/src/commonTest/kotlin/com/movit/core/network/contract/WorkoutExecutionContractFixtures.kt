package com.movit.core.network.contract

import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.ExploreWorkoutUploadRequestDto
import com.movit.core.network.dto.RepMetricsDataDto
import com.movit.core.network.dto.RepMetricsDto
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto

object WorkoutExecutionContractFixtures {

    fun sampleRepMetrics(): RepMetricsDto = RepMetricsDto(
        rom = 9.5f,
        symmetry = 8.2f,
        stability = 7.8f,
        tempo = listOf(1200, 400, 900),
        velocity = 45.0f,
        formScore = 8.6f,
        alignmentAccuracy = 9.0f,
    )

    fun sampleRepMetricsData(num: Int = 1): RepMetricsDataDto = RepMetricsDataDto(
        num = num,
        durationMs = 2800,
        worstState = 1,
        score = 8.5f,
        weightKg = null,
        side = null,
        metrics = sampleRepMetrics(),
    )

    fun sampleExecutionMetrics(): ExecutionMetricsDto = ExecutionMetricsDto(
        avgRom = 9.2f,
        avgSymmetry = 8.0f,
        avgStability = 7.5f,
        avgTempo = listOf(1100, 350, 850),
        avgVelocity = 42.0f,
        avgFormScore = 8.4f,
        avgAlignmentAccuracy = 8.8f,
        totalTUT = 8400,
        totalVolume = null,
        maxWeight = null,
        est1RM = null,
    )

    fun sampleExecutionUpload(
        id: String = "exec-001",
        exerciseId: String = "bodyweight-squat",
        groupId: String = "grp-001",
        templateId: String = "wt-001",
        context: String = "explore_workout",
    ): WorkoutExecutionUploadRequestDto = WorkoutExecutionUploadRequestDto(
        id = id,
        exerciseId = exerciseId,
        timestamp = 1_700_000_000_000L,
        durationMs = 84_000,
        totalReps = 10,
        countedReps = 9,
        invalidReps = 1,
        weightKg = null,
        weightUnit = "kg",
        repMetrics = listOf(sampleRepMetricsData()),
        executionMetrics = sampleExecutionMetrics(),
        context = context,
        workoutGroupId = groupId,
        workoutTemplateId = templateId,
    )

    fun sampleExploreUploadRequest(): ExploreWorkoutUploadRequestDto = ExploreWorkoutUploadRequestDto(
        workoutGroupId = "grp-001",
        workoutTemplateId = "wt-001",
        isCustomized = false,
        context = "explore_workout",
        executions = listOf(sampleExecutionUpload()),
    )
}
