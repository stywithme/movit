package com.movit.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserExercisePreferenceUpsertRequest(
    val customReps: Int? = null,
    val customDurationSec: Int? = null,
    val customWeightKg: Float? = null,
)

@Serializable
data class ProgressionMarkSeenRequest(
    val ids: List<String>,
)

@Serializable
data class ProgressionMarkSeenResponse(
    val success: Boolean = false,
    val error: String? = null,
)

@Serializable
data class PlanMutationResponse(
    val success: Boolean = false,
    val error: String? = null,
    val data: ActivePlanDto? = null,
    val completion: ProgramCompletionDecisionDto? = null,
)

@Serializable
data class ProgramCompletionDecisionDto(
    val nextAction: String = "",
    val nextProgramId: String? = null,
    val reassessmentTemplateId: String? = null,
)

@Serializable
data class UserProgramOverrideCreateRequest(
    val weekNumber: Int,
    val dayNumber: Int,
    val plannedWorkoutItemId: String? = null,
    val workoutTemplateExerciseId: String? = null,
    val overrideType: String,
    val reasonCode: String? = null,
    val data: JsonElement? = null,
)

@Serializable
data class UserProgramOverrideCreateResponse(
    val success: Boolean = false,
    val error: String? = null,
)
