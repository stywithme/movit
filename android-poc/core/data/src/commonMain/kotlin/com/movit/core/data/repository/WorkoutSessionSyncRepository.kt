package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.SubstitutionExerciseDto
import com.movit.core.network.dto.SubstitutionExercisesApiResponse
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.shared.AppResult

class WorkoutSessionSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
) {
    fun readCachedEffectivePlan(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): EffectivePlanPayloadDto? {
        val raw = platform().readCache(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey(userProgramId, weekNumber, dayNumber),
        ) ?: return null
        return runCatching {
            MovitJson.decodeFromString<EffectivePlanApiResponse>(raw).data
        }.getOrNull()
    }

    suspend fun syncEffectivePlan(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): AppResult<EffectivePlanPayloadDto> {
        val bindings = platform()
        val cacheKey = MovitCacheKeys.effectivePlanKey(userProgramId, weekNumber, dayNumber)
        val cached = readCachedEffectivePlan(userProgramId, weekNumber, dayNumber)
        val auth = bindings.authHeader()
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Sign in to load this workout session.")

        val response = api.fetchEffectivePlan(
            authorization = auth,
            userProgramId = userProgramId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Workout session sync failed.")
        }

        if (!response.success) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Workout session sync failed.")
        }

        val payload = response.data
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Effective plan response was empty.")

        bindings.writeCache(
            MovitCacheKeys.SESSION_STORE,
            cacheKey,
            MovitJson.encodeToString(EffectivePlanApiResponse.serializer(), response),
        )

        return AppResult.Success(payload)
    }

    suspend fun saveDayCustomizations(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        request: UserProgramUpdateRequest,
    ): AppResult<Unit> {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return AppResult.Failure("Sign in to save workout changes.")

        return api.updateUserProgramCustomizations(
            authorization = auth,
            userProgramId = userProgramId,
            request = request,
        ).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { error -> AppResult.Failure(error.message ?: "Failed to save workout changes.") },
        )
    }

    suspend fun fetchSubstitutionCandidates(
        replacingSlug: String,
    ): AppResult<List<SubstitutionExerciseDto>> {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return AppResult.Failure("Sign in to find swap options.")

        val response = api.fetchSubstitutionExercises(
            authorization = auth,
            slug = replacingSlug,
        ).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Substitution lookup failed.")
        }

        if (!response.success) {
            return AppResult.Failure(response.error ?: "Substitution lookup failed.")
        }

        return AppResult.Success(response.data.orEmpty())
    }
}
