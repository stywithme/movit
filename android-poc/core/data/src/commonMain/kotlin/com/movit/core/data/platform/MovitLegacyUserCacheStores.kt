package com.movit.core.data.platform

import com.movit.core.data.repository.MovitCacheKeys

/**
 * SharedPreferences namespaces cleared on logout via [MovitPlatformBindings.clearLegacyUserCaches].
 * Includes legacy Android names and KMP cache stores still backed by platform prefs.
 */
object MovitLegacyUserCacheStores {
    val sharedPreferenceNames: List<String> = listOf(
        MovitCacheKeys.HOME_STORE,
        MovitCacheKeys.EXPLORE_STORE,
        MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        "day_customization_store",
        MovitCacheKeys.PROGRAM_STORE,
        MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
        "program_workout_report_store",
        MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
        MovitCacheKeys.PREFERENCES_STORE,
        MovitCacheKeys.REPORTS_STORE,
        MovitCacheKeys.SESSION_STORE,
        MovitCacheKeys.SESSION_JOURNAL_STORE,
    )
}
