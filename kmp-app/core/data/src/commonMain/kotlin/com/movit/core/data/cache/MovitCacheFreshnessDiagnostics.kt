package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.MovitCacheKeys

/**
 * Minimal KMP diagnostics proving SQLDelight is the active offline source after legacy cutover.
 */
data class MovitCacheFreshnessReport(
    val lastSyncAt: String?,
    val serverVersion: String?,
    val entityCounts: MovitCacheDriftDetector.EntityCounts,
    val pendingOutboxCount: Long,
    val hasExploreCache: Boolean,
    val hasHomeCache: Boolean,
    val legacyCutoverComplete: Boolean,
) {
    fun toLogLine(): String = buildString {
        append("KMP cache freshness")
        append(" | lastSync=").append(lastSyncAt ?: "none")
        append(" | serverVersion=").append(serverVersion ?: "none")
        append(" | exercises=").append(entityCounts.exercises)
        append(" | workouts=").append(entityCounts.workouts)
        append(" | programs=").append(entityCounts.programs)
        append(" | outboxPending=").append(pendingOutboxCount)
        append(" | explore=").append(if (hasExploreCache) "yes" else "no")
        append(" | home=").append(if (hasHomeCache) "yes" else "no")
        append(" | cutover=").append(if (legacyCutoverComplete) "v1" else "pending")
    }
}

class MovitCacheFreshnessDiagnostics(
    private val localStore: MovitLocalStore,
    private val metadataStore: MovitSyncMetadataStore,
) {
    suspend fun snapshot(): MovitCacheFreshnessReport {
        val syncMeta = localStore.readSyncMetadata(MovitCacheKeys.SYNC_STORE)
        return MovitCacheFreshnessReport(
            lastSyncAt = metadataStore.readLastSyncTimestamp(),
            serverVersion = syncMeta?.version,
            entityCounts = metadataStore.readEntityCounts(),
            pendingOutboxCount = localStore.countOutboxByStatus(OutboxStatus.PENDING),
            hasExploreCache = localStore.readJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_DATA,
            ) != null,
            hasHomeCache = localStore.readJsonCache(
                MovitCacheKeys.HOME_STORE,
                MovitCacheKeys.HOME_DATA,
            ) != null,
            legacyCutoverComplete = localStore.readJsonCache(
                MovitCacheKeys.SYNC_STORE,
                MovitCacheKeys.LEGACY_CUTOVER_V1,
            ) == "true",
        )
    }
}
