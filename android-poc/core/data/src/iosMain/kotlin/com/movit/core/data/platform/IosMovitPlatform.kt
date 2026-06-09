package com.movit.core.data.platform

import platform.Foundation.NSUserDefaults

class IosMovitPlatform(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : MovitPlatformBindings {

    override fun apiBaseUrl(): String =
        defaults.stringForKey(KEY_API_BASE_URL)?.takeIf { it.isNotBlank() } ?: baseUrl

    override fun authHeader(): String? {
        val token = defaults.stringForKey(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    override fun preferredLanguage(): String =
        defaults.stringForKey(KEY_LANGUAGE)?.takeIf { it.isNotBlank() } ?: "en"

    override fun userDisplayName(fallback: String): String =
        defaults.stringForKey(KEY_USER_NAME)?.takeIf { it.isNotBlank() } ?: fallback

    override fun readCache(store: String, key: String): String? =
        defaults.stringForKey(cacheKey(store, key))

    override fun writeCache(store: String, key: String, value: String) {
        defaults.setObject(value, cacheKey(store, key))
    }

    override fun removeCache(store: String, key: String) {
        defaults.removeObjectForKey(cacheKey(store, key))
    }

    override fun isProUser(): Boolean = defaults.boolForKey(KEY_IS_PRO)

    override fun activeUserProgramId(): String? =
        defaults.stringForKey(KEY_ACTIVE_USER_PROGRAM_ID)?.takeIf { it.isNotBlank() }

    override fun userEmail(): String? =
        defaults.stringForKey(KEY_USER_EMAIL)?.takeIf { it.isNotBlank() }

    override fun userAvatarUrl(): String? =
        defaults.stringForKey(KEY_AVATAR_URL)?.takeIf { it.isNotBlank() }

    override fun refreshToken(): String? =
        defaults.stringForKey(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }

    override fun isOnboardingCompleted(): Boolean =
        defaults.boolForKey(KEY_ONBOARDING_COMPLETED)

    override fun totalWorkouts(): Int =
        defaults.integerForKey(KEY_TOTAL_WORKOUTS).toInt()

    override fun totalMinutes(): Int =
        defaults.integerForKey(KEY_TOTAL_MINUTES).toInt()

    override fun subscriptionExpiry(): String? =
        defaults.stringForKey(KEY_SUBSCRIPTION_EXPIRY)?.takeIf { it.isNotBlank() }

    override fun voiceFeedbackEnabled(): Boolean =
        defaults.boolForKey(KEY_VOICE_FEEDBACK)

    override fun notificationsEnabled(): Boolean =
        defaults.boolForKey(KEY_NOTIFICATIONS)

    override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
        defaults.setObject(snapshot.accessToken, KEY_ACCESS_TOKEN)
        defaults.setObject(snapshot.refreshToken, KEY_REFRESH_TOKEN)
        defaults.setObject(snapshot.name, KEY_USER_NAME)
        defaults.setObject(snapshot.email, KEY_USER_EMAIL)
        snapshot.avatarUrl?.let { defaults.setObject(it, KEY_AVATAR_URL) }
        defaults.setObject(snapshot.preferredLanguage, KEY_LANGUAGE)
        defaults.setBool(snapshot.isPro, KEY_IS_PRO)
        defaults.setBool(true, KEY_ONBOARDING_COMPLETED)
        defaults.setInteger(snapshot.totalWorkouts.toLong(), KEY_TOTAL_WORKOUTS)
        defaults.setInteger(snapshot.totalMinutes.toLong(), KEY_TOTAL_MINUTES)
        snapshot.subscriptionExpiry?.let { defaults.setObject(it, KEY_SUBSCRIPTION_EXPIRY) }
        defaults.setBool(snapshot.voiceFeedback, KEY_VOICE_FEEDBACK)
        defaults.setBool(snapshot.notifications, KEY_NOTIFICATIONS)
    }

    override fun clearAuthSession() {
        defaults.removeObjectForKey(KEY_ACCESS_TOKEN)
        defaults.removeObjectForKey(KEY_REFRESH_TOKEN)
        defaults.removeObjectForKey(KEY_USER_EMAIL)
        defaults.removeObjectForKey(KEY_AVATAR_URL)
        defaults.removeObjectForKey(KEY_SUBSCRIPTION_EXPIRY)
        defaults.setBool(false, KEY_IS_PRO)
        defaults.setBool(false, KEY_ONBOARDING_COMPLETED)
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        defaults.setBool(completed, KEY_ONBOARDING_COMPLETED)
    }

    override fun updateUserSettings(
        preferredLanguage: String?,
        voiceFeedback: Boolean?,
        notifications: Boolean?,
    ) {
        preferredLanguage?.let { defaults.setObject(it, KEY_LANGUAGE) }
        voiceFeedback?.let { defaults.setBool(it, KEY_VOICE_FEEDBACK) }
        notifications?.let { defaults.setBool(it, KEY_NOTIFICATIONS) }
    }

    private fun cacheKey(store: String, key: String): String = "movit_cache_${store}_$key"

    companion object {
        const val DEFAULT_BASE_URL = "https://back.mongz.online/"
        private const val KEY_API_BASE_URL = "movit_api_base_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_ACTIVE_USER_PROGRAM_ID = "active_user_program_id"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_TOTAL_WORKOUTS = "total_workouts"
        private const val KEY_TOTAL_MINUTES = "total_minutes"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
