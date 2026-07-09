package com.movit.core.data.repository

import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.RepMetricsDataDto
import com.movit.core.network.dto.RepMetricsDto
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import kotlinx.serialization.json.JsonElement

object WorkoutUploadMapper {
    fun toUploadRequest(
        upload: WorkoutUpload,
        context: String? = null,
        workoutGroupId: String? = null,
        workoutTemplateId: String? = null,
        legacyReport: JsonElement? = null,
    ): WorkoutExecutionUploadRequestDto = WorkoutExecutionUploadRequestDto(
        id = upload.id,
        exerciseId = upload.exerciseId,
        timestamp = upload.timestamp,
        durationMs = upload.durationMs,
        totalReps = upload.totalReps,
        countedReps = upload.countedReps,
        invalidReps = upload.invalidReps,
        weightKg = upload.weightKg,
        weightUnit = upload.weightUnit,
        repMetrics = upload.repMetrics.map { it.toDto() },
        executionMetrics = upload.executionMetrics.toDto(),
        context = context,
        workoutGroupId = workoutGroupId,
        workoutTemplateId = workoutTemplateId,
        legacyReport = legacyReport,
    )

    private fun RepMetricsData.toDto(): RepMetricsDataDto = RepMetricsDataDto(
        num = num,
        durationMs = durationMs,
        worstState = worstState.toInt(),
        score = score / 10f,
        weightKg = weightKg,
        side = side,
        metrics = metrics.toDto(),
    )

    private fun RepMetrics.toDto(): RepMetricsDto = RepMetricsDto(
        rom = rom / 10f,
        symmetry = symmetry?.let { it / 10f },
        stability = stability?.let { it / 10f },
        tempo = tempo.toList(),
        velocity = velocity?.toFloat(),
        formScore = formScore / 10f,
        alignmentAccuracy = alignmentAccuracy?.let { it / 10f },
    )

    private fun WorkoutExecutionMetrics.toDto(): ExecutionMetricsDto = ExecutionMetricsDto(
        avgRom = avgRom / 10f,
        avgSymmetry = avgSymmetry?.let { it / 10f },
        avgStability = avgStability?.let { it / 10f },
        avgTempo = avgTempo,
        avgVelocity = avgVelocity?.toFloat(),
        avgFormScore = avgFormScore / 10f,
        avgAlignmentAccuracy = avgAlignmentAccuracy?.let { it / 10f },
        totalTUT = totalTUT,
        totalVolume = totalVolume,
        maxWeight = maxWeight,
        est1RM = est1RM,
        formConsistency = formConsistency?.let { it / 10f },
        fatigueIndex = fatigueIndex?.toFloat(),
    )
}
