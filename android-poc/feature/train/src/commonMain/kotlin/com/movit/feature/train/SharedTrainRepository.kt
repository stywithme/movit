package com.movit.feature.train

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.resources.strings.TrainStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SharedTrainRepository : TrainRepository {

    override fun observeDashboard(): Flow<CacheState<TrainDashboardUi>> {
        if (!MovitData.isInstalled) {
            return flowOf(CacheState.Error(DATA_LAYER_NOT_INSTALLED))
        }

        return staleWhileRevalidate(
            screenId = "train",
            readCached = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = TrainStrings.load(language)
                val exploreCache = MovitData.explore.readCached()
                MovitData.home.readCached()?.let { data ->
                    TrainApiMapper.map(data, language, strings, exploreCache)
                }
            },
            syncFresh = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = TrainStrings.load(language)
                val exploreCache = MovitData.explore.readCached()
                when (val result = MovitData.home.sync()) {
                    is AppResult.Success -> AppResult.Success(
                        TrainApiMapper.map(result.value, language, strings, exploreCache),
                    )
                    is AppResult.Failure -> result
                }
            },
        )
    }

    override suspend fun getTrainDashboard(): AppResult<TrainDashboardUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = TrainStrings.load(language)
        val exploreCache = MovitData.explore.readCached()

        return when (val result = MovitData.home.sync()) {
            is AppResult.Success -> AppResult.Success(
                TrainApiMapper.map(result.value, language, strings, exploreCache),
            )
            is AppResult.Failure -> {
                val cached = MovitData.home.readCached()
                if (cached != null) {
                    AppResult.Success(
                        TrainApiMapper.map(cached, language, strings, exploreCache),
                    )
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}
