package com.movit.core.data.outbox

/**
 * Android registers a hook from [com.movit.host.LegacyWorkoutSyncDrain] install to clear
 * legacy [com.movit.storage.AnalyticsStorage] pending files on logout.
 * No-op on iOS (greenfield shell; KMP outbox only).
 */
object LegacyAnalyticsPendingCleaner {
    var clearPending: (() -> Unit)? = null

    fun clearIfRegistered() {
        clearPending?.invoke()
    }
}
