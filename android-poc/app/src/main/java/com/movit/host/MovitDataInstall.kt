package com.movit.host

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.movit.core.data.MovitData
import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.data.outbox.registerOutboxConnectivityReplay
import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.isMovitNetworkAvailable
import com.movit.core.data.repository.MovitCacheKeys
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.UserDataCleaner
import com.trainingvalidator.poc.ui.theme.AppThemeManager

object MovitDataInstall {

    fun install(context: Context) {
        val appContext = context.applicationContext
        MovitAndroidRuntime.applicationContext = appContext
        MovitData.install(
            platform = object : MovitPlatformBindings {
                override fun apiBaseUrl(): String = ApiConfig.getEffectiveBaseUrl()

                override fun authHeader(): String? = AuthManager.getAuthHeader(appContext)

                override fun preferredLanguage(): String =
                    AuthManager.getLanguage(appContext)

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

                override fun readAllCacheEntries(store: String): Map<String, String> {
                    val prefs = appContext.getSharedPreferences(store, Context.MODE_PRIVATE)
                    return prefs.all
                        .mapNotNull { (key, value) ->
                            (value as? String)?.let { key to it }
                        }
                        .toMap()
                }

                override fun isProUser(): Boolean = AuthManager.isProUser(appContext)

                override fun activeUserProgramId(): String? =
                    readCache(
                        MovitCacheKeys.PROGRAM_STORE,
                        MovitCacheKeys.ACTIVE_USER_PROGRAM_ID,
                    )?.takeIf { it.isNotBlank() }

                override fun setActiveUserProgramId(userProgramId: String?) {
                    if (userProgramId.isNullOrBlank()) {
                        removeCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
                    } else {
                        writeCache(
                            MovitCacheKeys.PROGRAM_STORE,
                            MovitCacheKeys.ACTIVE_USER_PROGRAM_ID,
                            userProgramId,
                        )
                    }
                }

                override fun exerciseImageUrl(slug: String): String? =
                    if (MovitData.isInstalled) {
                        MovitData.explore.exerciseImageUrl(slug)
                    } else {
                        null
                    }

                override fun userEmail(): String? =
                    AuthManager.getUserEmail(appContext).takeIf { it.isNotBlank() }

                override fun userAvatarUrl(): String? = AuthManager.getAvatarUrl(appContext)

                override fun refreshToken(): String? = AuthManager.getRefreshToken(appContext)

                override fun tokenExpiresAtEpochMs(): Long =
                    AuthManager.getTokenExpiresAt(appContext)

                override fun updateAuthTokens(
                    accessToken: String,
                    refreshToken: String,
                    expiresAtEpochMs: Long,
                ) {
                    val expiresInSeconds = ((expiresAtEpochMs - System.currentTimeMillis()) / 1000L)
                        .coerceAtLeast(0L)
                    AuthManager.saveNewTokens(appContext, accessToken, refreshToken, expiresInSeconds)
                }

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
                    UserDataCleaner.clearAll(appContext)
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

                override fun themeMode(): String = AppThemeManager.getMode(appContext)

                override fun setThemeMode(mode: String) {
                    AppThemeManager.setMode(appContext, mode)
                }

                override fun applyPreferredLanguage(languageCode: String) {
                    val localeTag = if (languageCode.lowercase() == "ar") "ar" else "en"
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(localeTag),
                    )
                }

                override fun isNetworkAvailable(): Boolean = isMovitNetworkAvailable(appContext)
            },
        )
        registerOutboxConnectivityReplay(appContext)
    }
}
