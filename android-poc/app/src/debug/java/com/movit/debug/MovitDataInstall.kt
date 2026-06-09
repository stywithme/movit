package com.movit.debug

import android.content.Context
import com.movit.core.data.MovitData
import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.MovitPlatformBindings
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.ProgramRepository

object MovitDataInstall {

    fun install(context: Context) {
        val appContext = context.applicationContext
        MovitData.install(
            object : MovitPlatformBindings {
                override fun apiBaseUrl(): String = ApiConfig.getEffectiveBaseUrl()

                override fun authHeader(): String? = AuthManager.getAuthHeader(appContext)

                override fun preferredLanguage(): String =
                    appContext.resources.configuration.locales[0]?.language ?: "en"

                override fun userDisplayName(fallback: String): String =
                    AuthManager.getUserName(appContext, fallback)

                override fun readCache(store: String, key: String): String? =
                    appContext.getSharedPreferences(store, Context.MODE_PRIVATE).getString(key, null)

                override fun writeCache(store: String, key: String, value: String) {
                    appContext.getSharedPreferences(store, Context.MODE_PRIVATE)
                        .edit()
                        .putString(key, value)
                        .apply()
                }

                override fun removeCache(store: String, key: String) {
                    appContext.getSharedPreferences(store, Context.MODE_PRIVATE)
                        .edit()
                        .remove(key)
                        .apply()
                }

                override fun isProUser(): Boolean = AuthManager.isProUser(appContext)

                override fun activeUserProgramId(): String? =
                    ProgramRepository.getInstance(appContext)
                        .getActiveUserProgramExport()
                        ?.takeIf { it.isActive }
                        ?.id

                override fun userEmail(): String? =
                    AuthManager.getUserEmail(appContext).takeIf { it.isNotBlank() }

                override fun userAvatarUrl(): String? = AuthManager.getAvatarUrl(appContext)

                override fun refreshToken(): String? = AuthManager.getRefreshToken(appContext)

                override fun isOnboardingCompleted(): Boolean =
                    AuthManager.isOnboardingCompleted(appContext)

                override fun totalWorkouts(): Int = AuthManager.getTotalWorkouts(appContext)

                override fun totalMinutes(): Int = AuthManager.getTotalMinutes(appContext)

                override fun subscriptionExpiry(): String? =
                    AuthManager.getSubscriptionExpiryIso(appContext)

                override fun voiceFeedbackEnabled(): Boolean =
                    AuthManager.getVoiceFeedbackEnabled(appContext)

                override fun notificationsEnabled(): Boolean =
                    AuthManager.getNotificationsEnabled(appContext)

                override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
                    AuthManager.saveAuthData(
                        appContext,
                        com.trainingvalidator.poc.network.AuthData(
                            user = com.trainingvalidator.poc.network.UserPublic(
                                id = snapshot.userId,
                                email = snapshot.email,
                                name = snapshot.name,
                                avatarUrl = snapshot.avatarUrl,
                                provider = "email",
                                preferredLanguage = snapshot.preferredLanguage,
                                voiceFeedback = snapshot.voiceFeedback,
                                notifications = snapshot.notifications,
                                isPro = snapshot.isPro,
                                subscriptionExpiry = snapshot.subscriptionExpiry,
                                totalWorkoutExecutions = snapshot.totalWorkouts,
                                totalMinutes = snapshot.totalMinutes,
                                emailVerified = true,
                                createdAt = "",
                            ),
                            tokens = com.trainingvalidator.poc.network.AuthTokens(
                                accessToken = snapshot.accessToken,
                                refreshToken = snapshot.refreshToken,
                                expiresIn = snapshot.expiresInSeconds,
                            ),
                        ),
                    )
                }

                override fun clearAuthSession() {
                    AuthManager.clearAuthData(appContext)
                }

                override fun setOnboardingCompleted(completed: Boolean) {
                    AuthManager.setOnboardingCompleted(appContext, completed)
                }

                override fun updateUserSettings(
                    preferredLanguage: String?,
                    voiceFeedback: Boolean?,
                    notifications: Boolean?,
                ) {
                    AuthManager.updateSettings(
                        appContext,
                        preferredLanguage = preferredLanguage,
                        voiceFeedback = voiceFeedback,
                        notifications = notifications,
                    )
                }
            },
        )
    }
}
