package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.HomeDataDto
import com.movit.shared.AppResult

class HomeSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
) {
    fun readCached(): HomeDataDto? {
        val raw = platform().readCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA)
            ?: return null
        return runCatching { MovitJson.decodeFromString<HomeDataDto>(raw) }.getOrNull()
    }

    suspend fun sync(): AppResult<HomeDataDto> {
        val bindings = platform()
        val cached = readCached()
        val auth = bindings.authHeader()
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Sign in to load your home dashboard.")

        val response = api.fetchHome().getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Home sync failed.")
        }

        if (!response.success) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Home sync failed.")
        }

        val incoming = response.data
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Home response was empty.")

        bindings.writeCache(
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            MovitJson.encodeToString(HomeDataDto.serializer(), incoming),
        )

        return AppResult.Success(incoming)
    }
}
