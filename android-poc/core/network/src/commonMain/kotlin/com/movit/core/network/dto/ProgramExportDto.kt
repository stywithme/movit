package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProgramExportApiResponse(
    val success: Boolean = false,
    val data: ProgramExportDto? = null,
    val error: String? = null,
)

@Serializable
data class ProgramExportDto(
    val id: String = "",
    val slug: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val description: LocalizedNameDto? = null,
    val coverImageUrl: String? = null,
    val durationWeeks: Int = 0,
    val levelMin: ProgramLevelDto? = null,
    val levelMax: ProgramLevelDto? = null,
    val weeklyWorkoutTarget: Int? = null,
    val weeks: List<ProgramExportWeekDto> = emptyList(),
)

@Serializable
data class ProgramLevelDto(
    val id: String? = null,
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class ProgramExportWeekDto(
    val weekNumber: Int = 0,
    val target: LocalizedNameDto? = null,
    val days: List<ProgramExportDayDto> = emptyList(),
)

@Serializable
data class ProgramExportDayDto(
    val dayNumber: Int = 0,
    val isRestDay: Boolean = false,
    val plannedWorkouts: List<ProgramExportPlannedWorkoutDto> = emptyList(),
)

@Serializable
data class ProgramExportPlannedWorkoutDto(
    val id: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val estimatedDurationMin: Int? = null,
)
