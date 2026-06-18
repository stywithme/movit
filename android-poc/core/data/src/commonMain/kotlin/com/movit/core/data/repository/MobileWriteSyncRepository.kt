package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.ProgramCustomizationKeys
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutStartRequestDto
import com.movit.core.network.dto.ProgressionMarkSeenRequest
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import com.movit.core.network.dto.UserProgramOverrideCreateRequest
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.shared.AppResult

/**
 * Offline-safe mobile writes: optimistic local cache update, then durable outbox enqueue.
 */
class MobileWriteSyncRepository(
    private val platform: () -> MovitPlatformBindings,
    private val localStore: () -> MovitLocalStore,
    private val offlineWrites: OfflineWriteQueue,
) {
    suspend fun startPlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutStartRequestDto,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to start this workout.")

        val id = if (operationId != null) {
            offlineWrites.enqueuePlannedWorkoutStart(workoutId, request, operationId)
        } else {
            offlineWrites.enqueuePlannedWorkoutStart(workoutId, request)
        }
        return AppResult.Success(id)
    }

    suspend fun completePlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to complete this workout.")

        patchHomeTrainMode { mode ->
            mode.copy(
                todayWorkout = mode.todayWorkout?.copy(isCompleted = true),
            )
        }

        val id = if (operationId != null) {
            offlineWrites.enqueuePlannedWorkoutComplete(workoutId, request, operationId)
        } else {
            offlineWrites.enqueuePlannedWorkoutComplete(workoutId, request)
        }
        return AppResult.Success(id)
    }

    suspend fun reportPlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to submit this workout report.")

        patchHomeTrainMode { mode ->
            mode.copy(
                todayWorkout = mode.todayWorkout?.copy(isCompleted = true),
            )
        }

        val id = if (operationId != null) {
            offlineWrites.enqueuePlannedWorkoutReport(workoutId, request, operationId)
        } else {
            offlineWrites.enqueuePlannedWorkoutReport(workoutId, request)
        }
        return AppResult.Success(id)
    }

    suspend fun completePlan(operationId: String? = null): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to complete your program.")

        patchHomeTrainMode { mode ->
            mode.copy(status = "completed", isPaused = false)
        }

        val id = operationId?.let { offlineWrites.enqueuePlanComplete(it) }
            ?: offlineWrites.enqueuePlanComplete()
        return AppResult.Success(id)
    }

    suspend fun upsertExercisePreference(
        exerciseId: String,
        request: UserExercisePreferenceUpsertRequest,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to save exercise preferences.")

        cacheExercisePreference(exerciseId, request)

        val id = if (operationId != null) {
            offlineWrites.enqueueExercisePreferenceUpsert(exerciseId, request, operationId)
        } else {
            offlineWrites.enqueueExercisePreferenceUpsert(exerciseId, request)
        }
        return AppResult.Success(id)
    }

    suspend fun deleteExercisePreference(
        exerciseId: String,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to remove exercise preferences.")

        localStore().remove(
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.exercisePreferenceKey(
                ExerciseIdResolver(localStore()).resolveCanonicalExerciseId(exerciseId),
            ),
        )

        val id = if (operationId != null) {
            offlineWrites.enqueueExercisePreferenceDelete(exerciseId, operationId)
        } else {
            offlineWrites.enqueueExercisePreferenceDelete(exerciseId)
        }
        return AppResult.Success(id)
    }

    suspend fun createUserProgramOverride(
        userProgramId: String,
        request: UserProgramOverrideCreateRequest,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to save program overrides.")

        val id = if (operationId != null) {
            offlineWrites.enqueueUserProgramOverrideCreate(userProgramId, request, operationId)
        } else {
            offlineWrites.enqueueUserProgramOverrideCreate(userProgramId, request)
        }
        return AppResult.Success(id)
    }

    suspend fun deleteUserProgramOverride(
        userProgramId: String,
        overrideId: String,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to remove program overrides.")

        val id = if (operationId != null) {
            offlineWrites.enqueueUserProgramOverrideDelete(userProgramId, overrideId, operationId)
        } else {
            offlineWrites.enqueueUserProgramOverrideDelete(userProgramId, overrideId)
        }
        return AppResult.Success(id)
    }

    suspend fun saveDayCustomizations(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        request: UserProgramUpdateRequest,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to save workout changes.")

        applyCustomizationsToEffectivePlanCache(userProgramId, weekNumber, dayNumber, request)

        val id = if (operationId != null) {
            offlineWrites.enqueueSaveDayCustomizations(
                userProgramId, weekNumber, dayNumber, request, operationId,
            )
        } else {
            offlineWrites.enqueueSaveDayCustomizations(
                userProgramId, weekNumber, dayNumber, request,
            )
        }
        return AppResult.Success(id)
    }

    /**
     * Offline-safe upload for a single exercise execution (camera / training metrics).
     * Uses [WorkoutExecutionUploadRequestDto.id] as the stable outbox idempotency key.
     */
    suspend fun uploadWorkoutExecution(
        request: WorkoutExecutionUploadRequestDto,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to upload workout data.")

        val id = operationId ?: request.id
        offlineWrites.enqueueWorkoutExecutionUpload(request, operationId = id)
        return AppResult.Success(id)
    }

    suspend fun markProgressionSeen(
        request: ProgressionMarkSeenRequest,
        operationId: String? = null,
    ): AppResult<String> {
        if (!hasAuth()) return AppResult.Failure("Sign in to acknowledge progression updates.")

        val id = if (operationId != null) {
            offlineWrites.enqueueProgressionMarkSeen(request, operationId)
        } else {
            offlineWrites.enqueueProgressionMarkSeen(request)
        }
        return AppResult.Success(id)
    }

    private fun hasAuth(): Boolean = platform().authHeader() != null

    private fun patchHomeTrainMode(transform: (TrainModeDto) -> TrainModeDto) {
        val store = localStore()
        val cached = MovitCachePolicy.readJson(
            store,
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            HomeDataDto.serializer(),
        ) ?: return
        val trainMode = cached.trainMode ?: return
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            cached.copy(trainMode = transform(trainMode)),
            HomeDataDto.serializer(),
        )
    }

    private fun cacheExercisePreference(
        exerciseId: String,
        request: UserExercisePreferenceUpsertRequest,
    ) {
        val canonicalId = ExerciseIdResolver(localStore()).resolveCanonicalExerciseId(exerciseId)
        ExercisePreferenceLocalStore(localStore()).upsert(canonicalId, request)
    }

    private fun applyCustomizationsToEffectivePlanCache(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        request: UserProgramUpdateRequest,
    ) {
        val store = localStore()
        val cacheKey = MovitCacheKeys.effectivePlanKey(userProgramId, weekNumber, dayNumber)
        val basePayload = MovitCachePolicy.readJson(
            store,
            MovitCacheKeys.SESSION_STORE,
            cacheKey,
            EffectivePlanApiResponse.serializer(),
        )?.data ?: EffectivePlanPayloadDto(
            userProgramId = userProgramId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
        )

        val dayKey = ProgramCustomizationKeys.dayKey(weekNumber, dayNumber)
        val updatedWorkouts = request.customizations[dayKey] ?: basePayload.plannedWorkouts

        val updated = EffectivePlanApiResponse(
            success = true,
            data = basePayload.copy(
                plannedWorkouts = updatedWorkouts,
            ),
        )

        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.SESSION_STORE,
            cacheKey,
            updated,
            EffectivePlanApiResponse.serializer(),
        )
    }
}
