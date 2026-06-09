package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.resources.strings.LevelStrings
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
            is AppResult.Failure -> fallback.fetchLevelProfile()
        }
    }
}
