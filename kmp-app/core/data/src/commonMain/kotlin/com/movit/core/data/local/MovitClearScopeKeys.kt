package com.movit.core.data.local

import com.movit.core.data.repository.MovitCacheKeys

/**
 * PR-7 clear scopes: read caches vs durable offline writes.
 *
 * Session expiry clears [READ] only. Explicit logout / account delete clears both.
 */
object MovitClearScopeKeys {
    /** Keys under [MovitCacheKeys.REPORTS_STORE] that are durable until upload is confirmed. */
    fun isDurableReportsKey(cacheKey: String): Boolean =
        cacheKey.startsWith("post_training_report_") ||
            cacheKey.startsWith("session_report_") ||
            cacheKey.startsWith("exercise_set_reports_") ||
            cacheKey.startsWith("report_session_exercise_")

    /** Entire stores wiped by [MovitLocalStore.clearReadCaches] (except durable report keys). */
    val readCacheStores: Set<String> = setOf(
        MovitCacheKeys.HOME_STORE,
        MovitCacheKeys.EXPLORE_STORE,
        MovitCacheKeys.REPORTS_STORE,
        MovitCacheKeys.SESSION_STORE,
        MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE,
        MovitCacheKeys.PREFERENCES_STORE,
        MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE,
        MovitCacheKeys.PROGRAM_STORE,
        MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
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

/** Guest outbox retention (PR-4). */
const val GUEST_OUTBOX_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000
