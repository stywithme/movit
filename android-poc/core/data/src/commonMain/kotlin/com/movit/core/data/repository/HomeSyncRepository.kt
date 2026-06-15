package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.shared.AppResult

class HomeSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val localStore: () -> MovitLocalStore,
) {
    fun readCached(): HomeDataDto? =
        MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            HomeDataDto.serializer(),
        )

    suspend fun sync(): AppResult<HomeDataDto> {
        val bindings = platform()
        val cached = readCached()

        return MovitCachePolicy.syncWithFallback(
            cached = cached,
            authRequired = true,
            hasAuth = bindings.authHeader() != null,
            noAuthMessage = "Sign in to load your home dashboard.",
            fetch = {
                api.fetchHome(bindings.authHeader()).map { response ->
                    if (!response.success || response.data == null) {
                        error(response.error ?: "Home sync failed.")
                    }
                    HomeTrainModeHydrator.hydrateIfNeeded(
                        home = response.data!!,
                        api = api,
                        authorization = bindings.authHeader(),
                    )
                }
            },
            isSuccess = { true },
            errorMessage = { "Home sync failed." },
            persist = { incoming ->
                MovitCachePolicy.writeJson(
                    localStore(),
                    MovitCacheKeys.HOME_STORE,
                    MovitCacheKeys.HOME_DATA,
                    incoming,
                    HomeDataDto.serializer(),
                )
            },
            failureMessage = { it.message ?: "Home sync failed." },
        )
    }
}
