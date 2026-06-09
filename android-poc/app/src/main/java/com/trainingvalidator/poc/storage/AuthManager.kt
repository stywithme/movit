package com.trainingvalidator.poc.storage

import android.content.Context
import com.movit.core.data.platform.AndroidSecureSessionStore
import com.movit.core.data.platform.LegacyAuthTokenKeys
import com.movit.core.data.platform.SecureAuthTokens
import com.trainingvalidator.poc.network.AuthData
import com.trainingvalidator.poc.network.UserPublic

/**
 * AuthManager
 *
 * Handles auth-related SharedPreferences storage.
 */
object AuthManager {

    private const val PREFS_NAME = "app_prefs"

    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_AVATAR_URL = "avatar_url"

    private const val KEY_LANGUAGE = "language"
    private const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    private const val KEY_TOTAL_WORKOUTS = "total_workouts"
    private const val KEY_TOTAL_MINUTES = "total_minutes"

    private const val KEY_IS_PRO = "is_pro"
    private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"

    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedSecureStore: AndroidSecureSessionStore? = null

    private fun secureStore(context: Context): AndroidSecureSessionStore {
        val appContext = context.applicationContext
        return cachedSecureStore ?: synchronized(this) {
            cachedSecureStore ?: AndroidSecureSessionStore(appContext).also { cachedSecureStore = it }
        }
    }

    fun saveAuthData(context: Context, data: AuthData) {
        val expiresAt = System.currentTimeMillis() + data.tokens.expiresIn * 1000L
        secureStore(context).saveTokens(
            SecureAuthTokens(
                accessToken = data.tokens.accessToken,
                refreshToken = data.tokens.refreshToken,
                expiresAtEpochMs = expiresAt,
            ),
        )
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_ID, data.user.id)
        editor.putString(KEY_USER_NAME, data.user.name)
        editor.putString(KEY_USER_EMAIL, data.user.email)
        editor.putString(KEY_AVATAR_URL, data.user.avatarUrl)
        editor.putString(KEY_LANGUAGE, data.user.preferredLanguage)
        editor.putBoolean(KEY_VOICE_FEEDBACK, data.user.voiceFeedback)
        editor.putBoolean(KEY_NOTIFICATIONS, data.user.notifications)
        editor.putInt(KEY_TOTAL_WORKOUTS, data.user.totalWorkoutExecutions)
        editor.putInt(KEY_TOTAL_MINUTES, data.user.totalMinutes)
        editor.putBoolean(KEY_IS_PRO, data.user.isPro)
        editor.putString(KEY_SUBSCRIPTION_EXPIRY, data.user.subscriptionExpiry)
        editor.apply()
    }

    fun updateUser(context: Context, user: UserPublic) {
        val editor = prefs(context).edit()
        editor.putString(KEY_USER_ID, user.id)
        editor.putString(KEY_USER_NAME, user.name)
        editor.putString(KEY_USER_EMAIL, user.email)
        editor.putString(KEY_AVATAR_URL, user.avatarUrl)
        editor.putString(KEY_LANGUAGE, user.preferredLanguage)
        editor.putBoolean(KEY_VOICE_FEEDBACK, user.voiceFeedback)
        editor.putBoolean(KEY_NOTIFICATIONS, user.notifications)
        editor.putInt(KEY_TOTAL_WORKOUTS, user.totalWorkoutExecutions)
        editor.putInt(KEY_TOTAL_MINUTES, user.totalMinutes)
        editor.putBoolean(KEY_IS_PRO, user.isPro)
        editor.putString(KEY_SUBSCRIPTION_EXPIRY, user.subscriptionExpiry)
        editor.apply()
    }

    fun updateSettings(
        context: Context,
        preferredLanguage: String? = null,
        voiceFeedback: Boolean? = null,
        notifications: Boolean? = null
    ) {
        val editor = prefs(context).edit()
        if (preferredLanguage != null) editor.putString(KEY_LANGUAGE, preferredLanguage)
        if (voiceFeedback != null) editor.putBoolean(KEY_VOICE_FEEDBACK, voiceFeedback)
        if (notifications != null) editor.putBoolean(KEY_NOTIFICATIONS, notifications)
        editor.apply()
    }

    fun getAccessToken(context: Context): String? = secureStore(context).readAccessToken()

    fun getRefreshToken(context: Context): String? = secureStore(context).readRefreshToken()

    fun getAuthHeader(context: Context): String? {
        val token = getAccessToken(context) ?: return null
        return "Bearer $token"
    }

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserName(context: Context, fallback: String = "Athlete"): String =
        prefs(context).getString(KEY_USER_NAME, fallback) ?: fallback

    fun getUserEmail(context: Context, fallback: String = ""): String =
        prefs(context).getString(KEY_USER_EMAIL, fallback) ?: fallback

    fun getAvatarUrl(context: Context): String? =
        prefs(context).getString(KEY_AVATAR_URL, null)?.takeIf { it.isNotBlank() }

    fun updateAvatarUrl(context: Context, url: String?) {
        val editor = prefs(context).edit()
        if (url.isNullOrBlank()) {
            editor.remove(KEY_AVATAR_URL)
        } else {
            editor.putString(KEY_AVATAR_URL, url)
        }
        editor.apply()
    }

    /**
     * Locale code persisted with the user profile (e.g. after login / Profile save).
     * Splash applies this to [androidx.appcompat.app.AppCompatDelegate] so it aligns with
     * [com.trainingvalidator.poc.ui.utils.currentLanguage]. Prefer resolving UI language from
     * [Context] via [com.trainingvalidator.poc.ui.utils.feedbackLanguageCode] in new code.
     */
    fun getLanguage(context: Context, fallback: String = "en"): String =
        prefs(context).getString(KEY_LANGUAGE, fallback) ?: fallback

    fun getVoiceFeedbackEnabled(context: Context, fallback: Boolean = true): Boolean =
        prefs(context).getBoolean(KEY_VOICE_FEEDBACK, fallback)

    fun getNotificationsEnabled(context: Context, fallback: Boolean = true): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS, fallback)

    fun getTotalWorkouts(context: Context): Int =
        prefs(context).getInt(KEY_TOTAL_WORKOUTS, 0)

    fun getTotalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_TOTAL_MINUTES, 0)

    fun isProUser(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_PRO, false)

    fun getSubscriptionExpiryIso(context: Context): String? =
        prefs(context).getString(KEY_SUBSCRIPTION_EXPIRY, null)

    /**
     * Whether the user finished the profile onboarding (age/sex/metrics/goal/…).
     * Source of truth is the backend TrainingProfile; this cache avoids re-prompting.
     */
    fun isOnboardingCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun clearAuthData(context: Context) {
        secureStore(context).clearTokens()
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, false)
        editor.remove(LegacyAuthTokenKeys.ACCESS_TOKEN)
        editor.remove(LegacyAuthTokenKeys.REFRESH_TOKEN)
        editor.remove(LegacyAuthTokenKeys.TOKEN_EXPIRES_AT)
        editor.remove(KEY_USER_ID)
        editor.remove(KEY_USER_NAME)
        editor.remove(KEY_USER_EMAIL)
        editor.remove(KEY_AVATAR_URL)
        editor.remove(KEY_TOTAL_WORKOUTS)
        editor.remove(KEY_TOTAL_MINUTES)
        editor.remove(KEY_IS_PRO)
        editor.remove(KEY_SUBSCRIPTION_EXPIRY)
        editor.remove(KEY_ONBOARDING_COMPLETED)
        editor.apply()
    }
    
    // ==================== Token Refresh ====================
    
    private const val REFRESH_THRESHOLD_MS = 20 * 60 * 60 * 1000L  // 20 hours
    
    /**
     * Get token expiration timestamp
     */
    fun getTokenExpiresAt(context: Context): Long =
        secureStore(context).readExpiresAtEpochMs()
    
    /**
     * Check if token should be refreshed (after 20 hours)
     */
    fun shouldRefreshToken(context: Context): Boolean {
        val expiresAt = getTokenExpiresAt(context)
        if (expiresAt == 0L) return false
        
        // Token valid for 24h, refresh after 20h
        val tokenAge = System.currentTimeMillis() - (expiresAt - 24 * 60 * 60 * 1000L)
        return tokenAge >= REFRESH_THRESHOLD_MS
    }
    
    /**
     * Check if token is expired or about to expire (within 1 hour)
     */
    fun isTokenExpired(context: Context): Boolean {
        val expiresAt = getTokenExpiresAt(context)
        if (expiresAt == 0L) return true
        
        // Consider expired if less than 1 hour remaining
        return System.currentTimeMillis() >= (expiresAt - 60 * 60 * 1000L)
    }
    
    /**
     * Save new tokens after refresh
     */
    fun saveNewTokens(context: Context, accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        secureStore(context).saveTokens(
            SecureAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochMs = System.currentTimeMillis() + expiresInSeconds * 1000L,
            ),
        )
    }
}
