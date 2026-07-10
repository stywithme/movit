package com.movit.feature.account

import com.movit.core.data.repository.LogoutOutboxPreparation
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
    suspend fun prepareLogout(flushTimeoutMs: Long? = null): LogoutOutboxPreparation
    suspend fun logout(discardPendingOutbox: Boolean = false): AppResult<Unit>
    suspend fun deleteAccount(discardPendingOutbox: Boolean = false): AppResult<Unit>
    suspend fun updateSettings(update: ProfileSettingsUpdate): AppResult<ProfileUi>
    suspend fun setThemeMode(mode: String): AppResult<ProfileUi>
}
