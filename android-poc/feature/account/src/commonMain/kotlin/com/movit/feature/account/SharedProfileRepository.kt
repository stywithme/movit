package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.shared.AppResult

class SharedProfileRepository(
    private val fallback: ProfileRepository = FakeProfileRepository(),
) : ProfileRepository {

    override suspend fun loadProfile(): AppResult<ProfileUi> {
        if (!MovitData.isInstalled) {
            return fallback.loadProfile()
        }
        val platform = MovitData.requirePlatform()
        if (platform.authHeader() == null) {
            return AppResult.Failure("Sign in to load your profile.")
        }
        return when (val result = MovitData.account.fetchProfile()) {
            is AppResult.Success -> AppResult.Success(
                ProfileApiMapper.map(
                    user = result.value,
                    themeMode = platform.themeMode(),
                ),
            )
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun logout(): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return fallback.logout()
        }
        return MovitData.account.logout()
    }

    override suspend fun updateSettings(update: ProfileSettingsUpdate): AppResult<ProfileUi> {
        if (!MovitData.isInstalled) {
            return fallback.updateSettings(update)
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
            return fallback.setThemeMode(mode)
        }
        val platform = MovitData.requirePlatform()
        platform.setThemeMode(mode)
        return loadProfile()
    }
}
