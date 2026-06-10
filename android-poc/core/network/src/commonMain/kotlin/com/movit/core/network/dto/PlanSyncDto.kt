package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EnrollProgramRequestDto(
    val programId: String,
)

@Serializable
data class MobileSyncApiResponse(
    val success: Boolean = false,
    val data: MobileSyncDataDto? = null,
    val error: String? = null,
)

@Serializable
data class MobileSyncDataDto(
    val userPrograms: List<UserProgramExportDto> = emptyList(),
)

@Serializable
data class UserProgramExportDto(
    val id: String = "",
    val programId: String? = null,
    val isActive: Boolean = false,
)
