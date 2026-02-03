package com.trainingvalidator.poc.storage

import android.content.Context
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
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"

    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_AVATAR_URL = "avatar_url"

    private const val KEY_LANGUAGE = "language"
    private const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    private const val KEY_TOTAL_WORKOUTS = "total_workouts"
    private const val KEY_TOTAL_MINUTES = "total_minutes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAuthData(context: Context, data: AuthData) {
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_ACCESS_TOKEN, data.tokens.accessToken)
        editor.putString(KEY_REFRESH_TOKEN, data.tokens.refreshToken)
        editor.putLong(KEY_TOKEN_EXPIRES_AT, System.currentTimeMillis() + data.tokens.expiresIn * 1000L)
        editor.putString(KEY_USER_ID, data.user.id)
        editor.putString(KEY_USER_NAME, data.user.name)
        editor.putString(KEY_USER_EMAIL, data.user.email)
        editor.putString(KEY_AVATAR_URL, data.user.avatarUrl)
        editor.putString(KEY_LANGUAGE, data.user.preferredLanguage)
        editor.putBoolean(KEY_VOICE_FEEDBACK, data.user.voiceFeedback)
        editor.putBoolean(KEY_NOTIFICATIONS, data.user.notifications)
        editor.putInt(KEY_TOTAL_WORKOUTS, data.user.totalWorkouts)
        editor.putInt(KEY_TOTAL_MINUTES, data.user.totalMinutes)
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
        editor.putInt(KEY_TOTAL_WORKOUTS, user.totalWorkouts)
        editor.putInt(KEY_TOTAL_MINUTES, user.totalMinutes)
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

    fun getAccessToken(context: Context): String? = prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(context: Context): String? = prefs(context).getString(KEY_REFRESH_TOKEN, null)

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

    fun clearAuthData(context: Context) {
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, false)
        editor.remove(KEY_ACCESS_TOKEN)
        editor.remove(KEY_REFRESH_TOKEN)
        editor.remove(KEY_TOKEN_EXPIRES_AT)
        editor.remove(KEY_USER_ID)
        editor.remove(KEY_USER_NAME)
        editor.remove(KEY_USER_EMAIL)
        editor.remove(KEY_AVATAR_URL)
        editor.remove(KEY_TOTAL_WORKOUTS)
        editor.remove(KEY_TOTAL_MINUTES)
        editor.apply()
    }
    
    // ==================== Token Refresh ====================
    
    private const val REFRESH_THRESHOLD_MS = 20 * 60 * 60 * 1000L  // 20 hours
    
    /**
     * Get token expiration timestamp
     */
    fun getTokenExpiresAt(context: Context): Long =
        prefs(context).getLong(KEY_TOKEN_EXPIRES_AT, 0)
    
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
        val editor = prefs(context).edit()
        editor.putString(KEY_ACCESS_TOKEN, accessToken)
        editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        editor.putLong(KEY_TOKEN_EXPIRES_AT, System.currentTimeMillis() + expiresInSeconds * 1000L)
        editor.apply()
    }
}
