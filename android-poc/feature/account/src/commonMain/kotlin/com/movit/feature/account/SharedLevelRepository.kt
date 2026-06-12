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
        val strings = LevelStrings.load(language)
        return when (val result = MovitData.account.fetchLevelProfile()) {
            is AppResult.Success -> {
                val dto = result.value
                if (dto.overallLevel <= 0 && dto.bodyScore <= 0.0 && dto.assessmentId.isBlank()) {
                    return AppResult.Failure(LevelProfileLoadErrors.noProfile(strings.noProfileMessage()))
                }
                val plan = when (val planResult = MovitData.account.fetchActivePlan()) {
                    is AppResult.Success -> planResult.value
                    is AppResult.Failure -> null
                }
                val reassessments = when (val reassessmentResult = MovitData.account.fetchUpcomingReassessments()) {
                    is AppResult.Success -> reassessmentResult.value
                    is AppResult.Failure -> emptyList()
                }
                AppResult.Success(LevelApiMapper.map(dto, plan, reassessments, strings))
            }
            is AppResult.Failure -> AppResult.Failure(strings.fetchError())
        }
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}

internal object LevelProfileLoadErrors {
    const val NO_PROFILE_MARKER = "NO_PROFILE:"

    fun noProfile(message: String): String = "$NO_PROFILE_MARKER$message"

    fun isNoProfile(message: String?): Boolean = message?.startsWith(NO_PROFILE_MARKER) == true

    fun displayMessage(message: String?): String = message?.removePrefix(NO_PROFILE_MARKER).orEmpty()
}
