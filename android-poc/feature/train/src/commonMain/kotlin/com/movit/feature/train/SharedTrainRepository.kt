package com.movit.feature.train

import com.movit.core.data.MovitData
import com.movit.resources.strings.TrainStrings
import com.movit.shared.AppResult

class SharedTrainRepository(
    private val fallback: TrainRepository = FakeTrainRepository(),
) : TrainRepository {

    override suspend fun getTrainDashboard(): AppResult<TrainDashboardUi> {
        if (!MovitData.isInstalled) {
            return fallback.getTrainDashboard()
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = TrainStrings.load(language)
        val exploreCache = MovitData.explore.readCached()

        return when (val result = MovitData.home.sync()) {
            is AppResult.Success -> AppResult.Success(
                TrainApiMapper.map(
                    data = result.value,
                    language = language,
                    strings = strings,
                    explore = exploreCache,
                ),
            )
            is AppResult.Failure -> {
                val cached = MovitData.home.readCached()
                if (cached != null) {
                    AppResult.Success(
                        TrainApiMapper.map(
                            data = cached,
                            language = language,
                            strings = strings,
                            explore = exploreCache,
                        ),
                    )
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }
}
