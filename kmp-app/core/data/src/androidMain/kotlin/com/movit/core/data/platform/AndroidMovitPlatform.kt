package com.movit.core.data.platform

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.movit.core.data.MovitData
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitApiConfig

/**
 * Production Android implementation of [MovitPlatformBindings].
 * Replaces legacy AuthManager, UserDataCleaner, ApiConfig, and AppThemeManager on the Movit shell path.
 */
class AndroidMovitPlatform(
    context: Context,
    private val secureSession: SecureSessionStore = AndroidSecureSessionStore(context),
) : MovitPlatformBindings {

    private val appContext = context.applicationContext

    private val profilePrefs by lazy {
        appContext.getSharedPreferences(MovitAuthProfileKeys.ANDROID_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val themePrefs by lazy {
        appContext.getSharedPreferences(MovitThemeModeStorage.ANDROID_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun apiBaseUrl(): String = MovitApiConfig.getEffectiveBaseUrl()

    override fun authHeader(): String? {
        val token = secureSession.readAccessToken() ?: return null
        return "Bearer $token"
    }

    override fun preferredLanguage(): String =
        profilePrefs.getString(MovitAuthProfileKeys.KEY_LANGUAGE, "en") ?: "en"

    override fun userDisplayName(fallback: String): String =
        profilePrefs.getString(MovitAuthProfileKeys.KEY_USER_NAME, fallback) ?: fallback

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

    override fun isProUser(): Boolean =
        profilePrefs.getBoolean(MovitAuthProfileKeys.KEY_IS_PRO, false)

    override fun activeUserProgramId(): String? {
        if (MovitData.isInstalled) {
            MovitData.plan.readCachedActiveUserProgramId()?.let { return it }
        }
        return readCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
            ?.takeIf { it.isNotBlank() }
    }

    override fun setActiveUserProgramId(userProgramId: String?) {
        if (MovitData.isInstalled) {
            val store = MovitData.localStore
            if (userProgramId.isNullOrBlank()) {
                store.remove(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
            } else {
                store.writeJsonCache(
                    MovitCacheKeys.PROGRAM_STORE,
                    MovitCacheKeys.ACTIVE_USER_PROGRAM_ID,
                    userProgramId,
                )
            }
        }
        if (userProgramId.isNullOrBlank()) {
            removeCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
        } else {
            removeCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
        }
    }

    override fun exerciseImageUrl(slug: String): String? =
        if (MovitData.isInstalled) {
            MovitData.explore.exerciseImageUrl(slug)
        } else {
            null
        }

    override fun userEmail(): String? =
        profilePrefs.getString(MovitAuthProfileKeys.KEY_USER_EMAIL, null)?.takeIf { it.isNotBlank() }

    override fun userAvatarUrl(): String? =
        profilePrefs.getString(MovitAuthProfileKeys.KEY_AVATAR_URL, null)?.takeIf { it.isNotBlank() }

    override fun refreshToken(): String? = secureSession.readRefreshToken()

    override fun tokenExpiresAtEpochMs(): Long = secureSession.readExpiresAtEpochMs()

    override fun updateAuthTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochMs = expiresAtEpochMs,
            ),
        )
    }

    override fun isOnboardingCompleted(): Boolean =
        profilePrefs.getBoolean(MovitAuthProfileKeys.KEY_ONBOARDING_COMPLETED, false)

    override fun totalWorkouts(): Int =
        profilePrefs.getInt(MovitAuthProfileKeys.KEY_TOTAL_WORKOUTS, 0)

    override fun totalMinutes(): Int =
        profilePrefs.getInt(MovitAuthProfileKeys.KEY_TOTAL_MINUTES, 0)

    override fun subscriptionExpiry(): String? =
        profilePrefs.getString(MovitAuthProfileKeys.KEY_SUBSCRIPTION_EXPIRY, null)

    override fun voiceFeedbackEnabled(): Boolean =
        profilePrefs.getBoolean(MovitAuthProfileKeys.KEY_VOICE_FEEDBACK, true)

    override fun notificationsEnabled(): Boolean =
        profilePrefs.getBoolean(MovitAuthProfileKeys.KEY_NOTIFICATIONS, true)

    override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
        val authData = snapshot.toAuthDataDto()
        val expiresAt = System.currentTimeMillis() + authData.tokens.expiresIn * 1000L
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = authData.tokens.accessToken,
                refreshToken = authData.tokens.refreshToken,
                expiresAtEpochMs = expiresAt,
            ),
        )
        profilePrefs.edit()
            .putBoolean(MovitAuthProfileKeys.KEY_IS_LOGGED_IN, true)
            .putString(MovitAuthProfileKeys.KEY_USER_ID, authData.user.id)
            .putString(MovitAuthProfileKeys.KEY_USER_NAME, authData.user.name)
            .putString(MovitAuthProfileKeys.KEY_USER_EMAIL, authData.user.email)
            .putString(MovitAuthProfileKeys.KEY_AVATAR_URL, authData.user.avatarUrl)
            .putString(MovitAuthProfileKeys.KEY_LANGUAGE, authData.user.preferredLanguage)
            .putBoolean(MovitAuthProfileKeys.KEY_VOICE_FEEDBACK, authData.user.voiceFeedback)
            .putBoolean(MovitAuthProfileKeys.KEY_NOTIFICATIONS, authData.user.notifications)
            .putInt(MovitAuthProfileKeys.KEY_TOTAL_WORKOUTS, authData.user.totalWorkoutExecutions)
            .putInt(MovitAuthProfileKeys.KEY_TOTAL_MINUTES, authData.user.totalMinutes)
            .putBoolean(MovitAuthProfileKeys.KEY_IS_PRO, authData.user.isPro)
            .putString(MovitAuthProfileKeys.KEY_SUBSCRIPTION_EXPIRY, authData.user.subscriptionExpiry)
            .apply()
    }

    override fun clearAuthSession() {
        secureSession.clearTokens()
        profilePrefs.edit()
            .putBoolean(MovitAuthProfileKeys.KEY_IS_LOGGED_IN, false)
            .remove(LegacyAuthTokenKeys.ACCESS_TOKEN)
            .remove(LegacyAuthTokenKeys.REFRESH_TOKEN)
            .remove(LegacyAuthTokenKeys.TOKEN_EXPIRES_AT)
            .remove(MovitAuthProfileKeys.KEY_USER_ID)
            .remove(MovitAuthProfileKeys.KEY_USER_NAME)
            .remove(MovitAuthProfileKeys.KEY_USER_EMAIL)
            .remove(MovitAuthProfileKeys.KEY_AVATAR_URL)
            .remove(MovitAuthProfileKeys.KEY_TOTAL_WORKOUTS)
            .remove(MovitAuthProfileKeys.KEY_TOTAL_MINUTES)
            .remove(MovitAuthProfileKeys.KEY_IS_PRO)
            .remove(MovitAuthProfileKeys.KEY_SUBSCRIPTION_EXPIRY)
            .remove(MovitAuthProfileKeys.KEY_ONBOARDING_COMPLETED)
            .apply()
    }

    override fun clearLegacyUserCaches() {
        MovitLegacyUserCacheStores.sharedPreferenceNames.forEach { storeName ->
            appContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        profilePrefs.edit()
            .putBoolean(MovitAuthProfileKeys.KEY_ONBOARDING_COMPLETED, completed)
            .apply()
    }

    override fun updateUserSettings(
        preferredLanguage: String?,
        voiceFeedback: Boolean?,
        notifications: Boolean?,
    ) {
        val editor = profilePrefs.edit()
        if (preferredLanguage != null) editor.putString(MovitAuthProfileKeys.KEY_LANGUAGE, preferredLanguage)
        if (voiceFeedback != null) editor.putBoolean(MovitAuthProfileKeys.KEY_VOICE_FEEDBACK, voiceFeedback)
        if (notifications != null) editor.putBoolean(MovitAuthProfileKeys.KEY_NOTIFICATIONS, notifications)
        editor.apply()
    }

    override fun themeMode(): String {
        val stored = themePrefs.getString(MovitThemeModeStorage.KEY_THEME_MODE, MovitThemeModeStorage.SYSTEM)
        return stored?.let { MovitThemeModeStorage.normalize(it) } ?: MovitThemeModeStorage.SYSTEM
    }

    override fun setThemeMode(mode: String) {
        val normalized = MovitThemeModeStorage.normalize(mode)
        themePrefs.edit()
            .putString(MovitThemeModeStorage.KEY_THEME_MODE, normalized)
            .apply()
        AppCompatDelegate.setDefaultNightMode(
            when (normalized) {
                MovitThemeModeStorage.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                MovitThemeModeStorage.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

    override fun applyPreferredLanguage(languageCode: String) {
        val localeTag = if (languageCode.lowercase() == "ar") "ar" else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
    }

    override fun isNetworkAvailable(): Boolean = isMovitNetworkAvailable(appContext)
}
