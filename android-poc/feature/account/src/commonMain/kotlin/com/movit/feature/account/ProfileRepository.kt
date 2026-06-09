package com.movit.feature.account

import com.movit.shared.AppResult

data class ProfileUi(
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val isPro: Boolean,
    val subscriptionLabel: String,
    val subscriptionRenewal: String?,
    val languageCode: String,
    val themeMode: String,
    val audioCuesEnabled: Boolean,
    val hapticEnabled: Boolean,
    val trainingProfileSummary: String,
)

data class ProfileSettingsUpdate(
    val preferredLanguage: String? = null,
    val voiceFeedback: Boolean? = null,
    val notifications: Boolean? = null,
)

interface ProfileRepository {
    suspend fun loadProfile(): AppResult<ProfileUi>
    suspend fun logout(): AppResult<Unit>
    suspend fun updateSettings(update: ProfileSettingsUpdate): AppResult<ProfileUi>
    suspend fun setThemeMode(mode: String): AppResult<ProfileUi>
}

class FakeProfileRepository(
    private val signedIn: Boolean = true,
    private var profile: ProfileUi = FakeProfilePreviewData.signedIn,
    private val shouldFail: Boolean = false,
) : ProfileRepository {

    override suspend fun loadProfile(): AppResult<ProfileUi> {
        if (shouldFail) return AppResult.Failure("Unable to load profile.")
        return if (signedIn) {
            AppResult.Success(profile)
        } else {
            AppResult.Failure("Sign in to load your profile.")
        }
    }

    override suspend fun logout(): AppResult<Unit> {
        return if (signedIn) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure("Already signed out.")
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
        subscriptionLabel = "WayToFix Pro",
        subscriptionRenewal = "Renews Jun 15, 2026 · Monthly",
    )
}
