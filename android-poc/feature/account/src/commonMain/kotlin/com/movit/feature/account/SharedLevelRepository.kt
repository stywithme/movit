package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.resources.strings.LevelStrings
import com.movit.shared.AppResult

class SharedLevelRepository : LevelRepository {

    override suspend fun fetchLevelProfile(): AppResult<LevelProfileUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to load your level profile.")
        }
        val language = platform.preferredLanguage()
        return when (val result = MovitData.account.fetchLevelProfile()) {
            is AppResult.Success -> {
                val plan = when (val planResult = MovitData.account.fetchActivePlan()) {
                    is AppResult.Success -> planResult.value
                    is AppResult.Failure -> null
                }
                val reassessments = when (val reassessmentResult = MovitData.account.fetchUpcomingReassessments()) {
                    is AppResult.Success -> reassessmentResult.value
                    is AppResult.Failure -> emptyList()
                }
                val strings = LevelStrings.load(language)
                AppResult.Success(LevelApiMapper.map(result.value, plan, reassessments, strings))
            }
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}
