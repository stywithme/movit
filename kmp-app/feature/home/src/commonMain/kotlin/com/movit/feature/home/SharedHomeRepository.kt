package com.movit.feature.home

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.resources.strings.HomeStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SharedHomeRepository : HomeRepository {

    override fun observeDashboard(): Flow<CacheState<HomeDashboardUi>> {
        if (!MovitData.isInstalled) {
            return flowOf(CacheState.Error(DATA_LAYER_NOT_INSTALLED))
        }

        return staleWhileRevalidate(
            screenId = "home",
            readCached = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = HomeStrings.load(language)
                val userDisplayName = platform.userDisplayName()
                MovitData.home.readCached()?.let { data ->
                    HomeApiMapper.map(data, language, userDisplayName, strings)
                }
            },
            syncFresh = {
                // Option 1: paint Cached first; defer network so first composition is not contended.
                delay(HOME_NETWORK_DEFER_MS)
                println("[MovitHomeFirstFrame] starting home network refresh")
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = HomeStrings.load(language)
                val userDisplayName = platform.userDisplayName()
                when (val result = MovitData.home.sync()) {
                    is AppResult.Success -> AppResult.Success(
                        HomeApiMapper.map(result.value, language, userDisplayName, strings),
                    )
                    is AppResult.Failure -> result
                }
            },
        )
    }

    override suspend fun getHomeDashboard(): AppResult<HomeDashboardUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = HomeStrings.load(language)
        val userDisplayName = platform.userDisplayName()

        return when (val result = MovitData.home.sync()) {
            is AppResult.Success -> AppResult.Success(
                HomeApiMapper.map(result.value, language, userDisplayName, strings),
            )
            is AppResult.Failure -> {
                val cached = MovitData.home.readCached()
                if (cached != null) {
                    AppResult.Success(
                        HomeApiMapper.map(cached, language, userDisplayName, strings),
                    )
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
        /** Short pause after cache emit so Compose can commit the first Home frame. */
        const val HOME_NETWORK_DEFER_MS = 300L
    }
}
