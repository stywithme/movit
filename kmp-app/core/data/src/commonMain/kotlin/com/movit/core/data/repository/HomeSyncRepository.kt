package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.shared.AppResult
import kotlin.concurrent.Volatile

class HomeSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val localStore: () -> MovitLocalStore,
) {
    @Volatile
    private var memoizedHome: HomeDataDto? = null

    fun readCached(): HomeDataDto? {
        memoizedHome?.let { return it }
        return MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            HomeDataDto.serializer(),
        ).also { memoizedHome = it }
    }

    fun invalidateMemoized() {
        memoizedHome = null
    }

    fun readCachedEtag(): String? =
        localStore().readString(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_ETAG)

    suspend fun sync(): AppResult<HomeDataDto> {
        val bindings = platform()
        val cached = readCached()
        val etag = readCachedEtag()

        return MovitCachePolicy.syncWithFallback(
            cached = cached,
            authRequired = true,
            hasAuth = bindings.authHeader() != null,
            noAuthMessage = "Sign in to load your home dashboard.",
            fetch = {
                api.fetchHome(
                    authorization = bindings.authHeader(),
                    ifNoneMatch = etag,
                ).fold(
                    onFailure = { Result.failure(it) },
                    onSuccess = { response ->
                        // P2.4: 304 → keep cache (success with null data).
                        when {
                            response.success && response.data == null && cached != null ->
                                Result.success(cached)
                            !response.success || response.data == null ->
                                Result.failure(
                                    IllegalStateException(response.error ?: "Home sync failed."),
                                )
                            else -> {
                                response.etag?.let { value ->
                                    localStore().writeString(
                                        MovitCacheKeys.HOME_STORE,
                                        MovitCacheKeys.HOME_ETAG,
                                        value,
                                    )
                                }
                                Result.success(
                                    HomeTrainModeHydrator.hydrateIfNeeded(
                                        home = response.data!!,
                                        api = api,
                                        authorization = bindings.authHeader(),
                                    ),
                                )
                            }
                        }
                    },
                )
            },
            isSuccess = { true },
            errorMessage = { "Home sync failed." },
            persist = { incoming ->
                // Avoid rewriting identical cache on 304 path.
                if (incoming !== cached) {
                    MovitCachePolicy.writeJson(
                        localStore(),
                        MovitCacheKeys.HOME_STORE,
                        MovitCacheKeys.HOME_DATA,
                        incoming,
                        HomeDataDto.serializer(),
                    )
                    memoizedHome = incoming
                }
            },
            failureMessage = { it.message ?: "Home sync failed." },
        )
    }
}
