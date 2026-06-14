package com.movit.core.data.platform

/** Shared preference keys for non-token auth profile data (Android `app_prefs`; iOS NSUserDefaults). */
object MovitAuthProfileKeys {
    const val ANDROID_PREFS_NAME = LegacyAuthTokenKeys.LEGACY_ANDROID_PREFS_NAME

    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_AVATAR_URL = "avatar_url"
    const val KEY_LANGUAGE = "language"
    const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
    const val KEY_NOTIFICATIONS = "notifications_enabled"
    const val KEY_TOTAL_WORKOUTS = "total_workouts"
    const val KEY_TOTAL_MINUTES = "total_minutes"
    const val KEY_IS_PRO = "is_pro"
    const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"
    const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
}
