package com.movit.feature.home

import com.movit.core.data.MovitData
import com.movit.resources.strings.HomeStrings
import com.movit.shared.AppResult

class SharedHomeRepository(
    private val fallback: HomeRepository = FakeHomeRepository(),
) : HomeRepository {

    override suspend fun getHomeDashboard(): AppResult<HomeDashboardUi> {
        if (!MovitData.isInstalled) {
            return fallback.getHomeDashboard()
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = HomeStrings.load(language)
        val userDisplayName = platform.userDisplayName()

        return when (val result = MovitData.home.sync()) {
            is AppResult.Success -> AppResult.Success(
                HomeApiMapper.map(
                    data = result.value,
                    language = language,
                    userDisplayName = userDisplayName,
                    strings = strings,
                ),
            )
            is AppResult.Failure -> {
                val cached = MovitData.home.readCached()
                if (cached != null) {
                    AppResult.Success(
                        HomeApiMapper.map(
                            data = cached,
                            language = language,
                            userDisplayName = userDisplayName,
                            strings = strings,
                        ),
                    )
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }
}
