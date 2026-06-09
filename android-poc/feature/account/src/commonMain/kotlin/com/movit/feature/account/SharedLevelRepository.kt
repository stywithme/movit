package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.shared.AppResult

class SharedLevelRepository(
    private val fallback: LevelRepository = FakeLevelRepository(),
) : LevelRepository {

    override suspend fun fetchLevelProfile(): AppResult<LevelProfileUi> {
        if (!MovitData.isInstalled) {
            return fallback.fetchLevelProfile()
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to load your level profile.")
        }
        val language = platform.preferredLanguage()
        return when (val result = MovitData.account.fetchLevelProfile()) {
            is AppResult.Success -> AppResult.Success(LevelApiMapper.map(result.value, language))
            is AppResult.Failure -> fallback.fetchLevelProfile()
        }
    }
}
