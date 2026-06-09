package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ActivePlanApiResponse(
    val success: Boolean = false,
    val data: ActivePlanDto? = null,
    val error: String? = null,
)

@Serializable
data class ActivePlanDto(
    val id: String = "",
    val userId: String = "",
    val status: String = "",
    val programs: List<ActivePlanProgramDto> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ActivePlanProgramDto(
    val id: String = "",
    val sortOrder: Int = 0,
    val status: String = "",
    val scheduledStartDate: String? = null,
    val actualStartDate: String? = null,
    val completedAt: String? = null,
    val program: PlanProgramInfoDto? = null,
    val progress: PlanProgressDto = PlanProgressDto(),
)

@Serializable
data class PlanProgramInfoDto(
    val id: String = "",
    val name: Map<String, String> = emptyMap(),
    val slug: String = "",
    val type: String = "",
    val durationWeeks: Int = 0,
    val levelMinId: String? = null,
    val levelMaxId: String? = null,
    val levelMin: PlanLevelDto? = null,
    val levelMax: PlanLevelDto? = null,
    val coverImageUrl: String? = null,
)

@Serializable
data class PlanLevelDto(
    val id: String? = null,
    val number: Int = 0,
    val code: String = "",
    val name: Map<String, String>? = null,
)

@Serializable
data class PlanProgressDto(
    val completedDays: Int = 0,
    val totalDays: Int = 0,
    val currentWeek: Int = 1,
    val currentDay: Int = 1,
)
