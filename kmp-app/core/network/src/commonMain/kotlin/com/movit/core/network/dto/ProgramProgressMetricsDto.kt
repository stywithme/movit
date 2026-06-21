package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProgramProgressMetricsApiResponse(
    val success: Boolean = false,
    val data: ProgramProgressMetricsPayloadDto? = null,
    val error: String? = null,
)

@Serializable
data class ProgramProgressMetricsPayloadDto(
    val userProgramId: String = "",
    val programId: String? = null,
    val weeks: List<WeekProgressPointDto> = emptyList(),
)

@Serializable
data class WeekProgressPointDto(
    val weekNumber: Int = 0,
    val totalVolumeLoad: Double = 0.0,
    val avgFormScore: Double? = null,
    val plannedWorkoutCount: Int = 0,
    val avgRpe: Double? = null,
    val volumeChangePercent: Double? = null,
)
