package com.movit.feature.account

import com.movit.core.data.repository.LogoutOutboxPreparation
import com.movit.shared.AppResult

object FakeProfilePreviewData {
    val signedIn = ProfileUi(
        name = "Mahmoud Hassan",
        email = "mahmoud@email.com",
        avatarUrl = null,
        isPro = false,
        subscriptionLabel = "Free",
        subscriptionRenewal = null,
        languageCode = "en",
        themeMode = "system",
        audioCuesEnabled = true,
        hapticEnabled = true,
        trainingProfileSummary = "Strength · 3 days · Home",
    )

    val proMember = signedIn.copy(
        isPro = true,
        subscriptionLabel = "Movit Pro",
        subscriptionRenewal = "Renews Jun 15, 2026 · Monthly",
    )
}

class FakeProfileRepository(
    private val signedIn: Boolean = true,
    private var profile: ProfileUi = FakeProfilePreviewData.signedIn,
    private val shouldFail: Boolean = false,
    private var pendingLogoutCount: Long = 0L,
) : ProfileRepository {

    override suspend fun loadProfile(): AppResult<ProfileUi> {
        if (shouldFail) return AppResult.Failure("Unable to load profile.")
        return if (signedIn) {
            AppResult.Success(profile)
        } else {
            AppResult.Failure("Sign in to load your profile.")
        }
    }

    override suspend fun prepareLogout(flushTimeoutMs: Long?): LogoutOutboxPreparation =
        LogoutOutboxPreparation(pendingLogoutCount, flushAttempted = pendingLogoutCount > 0)

    override suspend fun logout(discardPendingOutbox: Boolean): AppResult<Unit> {
        if (!discardPendingOutbox && pendingLogoutCount > 0) {
            return AppResult.Failure("pending_outbox:$pendingLogoutCount")
        }
        return if (signedIn) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure("Already signed out.")
        }
    }

    override suspend fun deleteAccount(discardPendingOutbox: Boolean): AppResult<Unit> {
        if (!discardPendingOutbox && pendingLogoutCount > 0) {
            return AppResult.Failure("pending_outbox:$pendingLogoutCount")
        }
        return if (signedIn) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure("Sign in to delete your account.")
        }
    }

    override suspend fun updateSettings(update: ProfileSettingsUpdate): AppResult<ProfileUi> {
        if (!signedIn) return AppResult.Failure("Sign in to update settings.")
        profile = profile.copy(
            languageCode = update.preferredLanguage ?: profile.languageCode,
            audioCuesEnabled = update.voiceFeedback ?: profile.audioCuesEnabled,
            hapticEnabled = update.notifications ?: profile.hapticEnabled,
        )
        return AppResult.Success(profile)
    }

    override suspend fun setThemeMode(mode: String): AppResult<ProfileUi> {
        if (!signedIn) return AppResult.Failure("Sign in to update settings.")
        profile = profile.copy(themeMode = mode)
        return AppResult.Success(profile)
    }
}
