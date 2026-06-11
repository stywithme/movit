package com.movit.core.data.outbox

import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutStartRequestDto
import com.movit.core.network.dto.ProgressionMarkSeenRequest
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import com.movit.core.network.dto.UserProgramOverrideCreateRequest
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import kotlinx.serialization.Serializable

@Serializable
data class PlannedWorkoutStartOutboxPayload(
    val workoutId: String,
    val request: PlannedWorkoutStartRequestDto,
)

@Serializable
data class PlannedWorkoutCompleteOutboxPayload(
    val workoutId: String,
    val request: PlannedWorkoutCompleteRequestDto,
)

/** Local override row for a program day (ported from legacy DayCustomizationStore). */
@Serializable
data class DayCustomizationCacheDto(
    val userProgramId: String = "",
    val weekNumber: Int = 0,
    val dayNumber: Int = 0,
    val plannedWorkouts: List<EffectivePlannedWorkoutDto> = emptyList(),
    val lastModifiedAt: Long = 0L,
    val isUserModified: Boolean = false,
)

@Serializable
data class SaveDayCustomizationsOutboxPayload(
    val userProgramId: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val request: UserProgramUpdateRequest,
)

@Serializable
data class ExercisePreferenceUpsertOutboxPayload(
    val exerciseId: String,
    val request: UserExercisePreferenceUpsertRequest,
)

@Serializable
data class ExercisePreferenceDeleteOutboxPayload(
    val exerciseId: String,
)

@Serializable
data class UserProgramOverrideCreateOutboxPayload(
    val userProgramId: String,
    val request: UserProgramOverrideCreateRequest,
)

@Serializable
data class UserProgramOverrideDeleteOutboxPayload(
    val userProgramId: String,
    val overrideId: String,
)

@Serializable
data class ProgressionMarkSeenOutboxPayload(
    val request: ProgressionMarkSeenRequest,
)

@Serializable
data class WorkoutExecutionUploadOutboxPayload(
    val request: WorkoutExecutionUploadRequestDto,
)
