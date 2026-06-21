package com.movit.core.data.platform

import com.movit.core.data.repository.MovitCacheKeys

/**
 * SharedPreferences namespaces cleared on logout via [MovitPlatformBindings.clearLegacyUserCaches].
 * Includes legacy Android names and KMP cache stores still backed by platform prefs.
 */
object MovitLegacyUserCacheStores {
    /**
     * SharedPreferences / NSUserDefaults namespaces cleared on logout.
     * Mirrors [com.movit.core.data.local.LegacyCatalogReadPolicy.kmpPrimaryStores] plus
     * legacy [com.movit.storage] names still referenced on upgraded Android installs.
     */
    val sharedPreferenceNames: List<String> = listOf(
        MovitCacheKeys.HOME_STORE,
        MovitCacheKeys.EXPLORE_STORE,
        MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE,
        MovitCacheKeys.PROGRAM_STORE,
        MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
        "program_workout_report_store",
        MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
        MovitCacheKeys.PREFERENCES_STORE,
        MovitCacheKeys.REPORTS_STORE,
        MovitCacheKeys.SESSION_STORE,
        MovitCacheKeys.SESSION_JOURNAL_STORE,
        MovitCacheKeys.CATALOG_INDEX_STORE,
        MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
        MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
        MovitCacheKeys.EXERCISE_CONFIG_STORE,
        MovitCacheKeys.MESSAGE_LIBRARY_STORE,
        MovitCacheKeys.SYSTEM_MESSAGE_STORE,
        MovitCacheKeys.TRAINING_PREFERENCES_STORE,
        MovitCacheKeys.AUDIO_STORE,
        MovitCacheKeys.SYNC_STORE,
    )
}
