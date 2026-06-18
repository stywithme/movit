package com.movit.core.data.local

import com.movit.core.data.repository.MovitCacheKeys

/**
 * Guards production reads after legacy cutover (A9).
 *
 * [MigratingMovitLocalStore] copies legacy platform JSON into SQLDelight once via
 * [MigratingMovitLocalStore.migrateKnownCachesFromPlatform]. Runtime reads must not
 * fall back to stale SharedPreferences / NSUserDefaults catalog snapshots.
 */
object LegacyCatalogReadPolicy {
    /**
     * Store namespaces that must only be served from SQLDelight after install migration.
     * Includes KMP catalog caches and legacy [com.movit.storage] SharedPreferences names.
     */
    val kmpPrimaryStores: Set<String> = setOf(
        MovitCacheKeys.EXPLORE_STORE,
        MovitCacheKeys.HOME_STORE,
        MovitCacheKeys.REPORTS_STORE,
        MovitCacheKeys.SESSION_STORE,
        MovitCacheKeys.SESSION_JOURNAL_STORE,
        MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE,
        MovitCacheKeys.PREFERENCES_STORE,
        MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
        MovitCacheKeys.PROGRAM_STORE,
        MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
        MovitCacheKeys.CATALOG_INDEX_STORE,
        MovitCacheKeys.WORKOUT_TEMPLATE_STORE,
        MovitCacheKeys.USER_PROGRAM_ENROLLMENT_STORE,
        MovitCacheKeys.SYNC_STORE,
        MovitCacheKeys.AUDIO_STORE,
        MovitCacheKeys.SYSTEM_MESSAGE_STORE,
        MovitCacheKeys.MESSAGE_LIBRARY_STORE,
        MovitCacheKeys.EXERCISE_CONFIG_STORE,
        MovitCacheKeys.TRAINING_PREFERENCES_STORE,
        "program_workout_report_store",
    )

    fun allowsRuntimePlatformFallback(store: String): Boolean =
        store !in kmpPrimaryStores
}
