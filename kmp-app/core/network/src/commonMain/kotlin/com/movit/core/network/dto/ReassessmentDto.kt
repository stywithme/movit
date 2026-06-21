package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReassessmentListApiResponse(
    val success: Boolean = false,
    val data: List<ReassessmentDto>? = null,
    val error: String? = null,
)

@Serializable
data class ReassessmentDto(
    val id: String = "",
    val userId: String = "",
    val reason: String = "",
    val scheduledDate: String = "",
    val status: String = "",
    val assessmentId: String? = null,
    val notes: String? = null,
    val createdAt: String = "",
)
