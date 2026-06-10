package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
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
    private val localStore: () -> MovitLocalStore,
    private val mobileWrites: MobileWriteSyncRepository,
) {
    fun readCachedEffectivePlan(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): EffectivePlanPayloadDto? =
        MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey(userProgramId, weekNumber, dayNumber),
            EffectivePlanApiResponse.serializer(),
        )?.data

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
            userProgramId = userProgramId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            authorization = auth,
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

        MovitCachePolicy.writeJson(
            localStore(),
            MovitCacheKeys.SESSION_STORE,
            cacheKey,
            response,
            EffectivePlanApiResponse.serializer(),
        )

        return AppResult.Success(payload)
    }

    suspend fun saveDayCustomizations(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        request: UserProgramUpdateRequest,
    ): AppResult<Unit> =
        when (
            val result = mobileWrites.saveDayCustomizations(
                userProgramId = userProgramId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                request = request,
            )
        ) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }

    suspend fun fetchSubstitutionCandidates(
        replacingSlug: String,
    ): AppResult<List<SubstitutionExerciseDto>> {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return AppResult.Failure("Sign in to find swap options.")

        val response = api.fetchSubstitutionExercises(
            slug = replacingSlug,
            authorization = auth,
        ).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Substitution lookup failed.")
        }

        if (!response.success) {
            return AppResult.Failure(response.error ?: "Substitution lookup failed.")
        }

        return AppResult.Success(response.data.orEmpty())
    }
}
