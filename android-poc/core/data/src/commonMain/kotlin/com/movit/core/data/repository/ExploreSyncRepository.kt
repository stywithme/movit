package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.shared.AppResult

class ExploreSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
) {
    fun readCached(): ExploreDataDto? {
        val raw = platform().readCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA)
            ?: return null
        return runCatching { MovitJson.decodeFromString<ExploreDataDto>(raw) }.getOrNull()
    }

    suspend fun sync(limit: Int = 50): AppResult<ExploreDataDto> {
        val bindings = platform()
        val cached = readCached()
        val updatedAfter = bindings.readCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_LAST_SYNC)

        val response = api.fetchExplore(
            authorization = bindings.authHeader(),
            updatedAfter = updatedAfter,
            limit = limit,
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Explore sync failed.")
        }

        if (!response.success) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Explore sync failed.")
        }

        val incoming = response.data
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Explore response was empty.")

        val isFullSync = response.meta?.isFullSync == true || updatedAfter == null
        val merged = mergeExploreData(cached, incoming, isFullSync)

        bindings.writeCache(
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            MovitJson.encodeToString(ExploreDataDto.serializer(), merged),
        )
        if (response.timestamp.isNotBlank()) {
            bindings.writeCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_LAST_SYNC,
                response.timestamp,
            )
        }

        return AppResult.Success(merged)
    }

}
