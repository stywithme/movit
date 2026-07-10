package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.LogoutOutboxPreparation
import com.movit.shared.AppResult

class SharedProfileRepository : ProfileRepository {

    override suspend fun loadProfile(): AppResult<ProfileUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to load your profile.")
        }
        val trainingProfileSummary = when (val trainingProfile = MovitData.account.fetchTrainingProfile()) {
            is AppResult.Success -> TrainingProfileSummaryMapper.format(trainingProfile.value)
            is AppResult.Failure -> TrainingProfileSummaryMapper.EMPTY_SUMMARY_KEY
        }
        return when (val result = MovitData.account.fetchProfile()) {
            is AppResult.Success -> AppResult.Success(
                ProfileApiMapper.map(
                    user = result.value,
                    themeMode = platform.themeMode(),
                    trainingProfileSummary = trainingProfileSummary,
                ),
            )
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun prepareLogout(flushTimeoutMs: Long?): LogoutOutboxPreparation {
        if (!MovitData.isInstalled) {
            return LogoutOutboxPreparation(0, false)
        }
        return MovitData.account.prepareLogout(
            flushTimeoutMs = flushTimeoutMs ?: AccountSyncRepository.LOGOUT_FLUSH_TIMEOUT_MS,
        )
    }

    override suspend fun logout(discardPendingOutbox: Boolean): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return MovitData.account.logout(discardPendingOutbox = discardPendingOutbox)
    }

    override suspend fun deleteAccount(discardPendingOutbox: Boolean): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return MovitData.account.deleteAccount(discardPendingOutbox = discardPendingOutbox)
    }

    override suspend fun updateSettings(update: ProfileSettingsUpdate): AppResult<ProfileUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        return when (
            val result = MovitData.account.updateSettings(
                preferredLanguage = update.preferredLanguage,
                voiceFeedback = update.voiceFeedback,
                notifications = update.notifications,
            )
        ) {
            is AppResult.Success -> {
                update.preferredLanguage?.let(platform::applyPreferredLanguage)
                AppResult.Success(
                    ProfileApiMapper.map(
                        user = result.value,
                        themeMode = platform.themeMode(),
                    ),
                )
            }
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun setThemeMode(mode: String): AppResult<ProfileUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        val platform = MovitData.requirePlatform()
        platform.setThemeMode(mode)
        return loadProfile()
    }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}
