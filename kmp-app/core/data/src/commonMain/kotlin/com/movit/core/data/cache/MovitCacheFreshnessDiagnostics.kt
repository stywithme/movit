package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.sync.LastSyncCycleRecord
import com.movit.core.data.sync.MovitSyncTelemetry
import com.movit.core.data.sync.SyncErrorCounters

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
    val lastSyncCycle: LastSyncCycleRecord? = null,
    val errorCounters: SyncErrorCounters = SyncErrorCounters(),
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
        lastSyncCycle?.let { cycle ->
            append(" | syncReason=").append(cycle.reason)
            append(" | syncIsFull=").append(cycle.isFull?.toString() ?: "none")
            append(" | syncUpdatedAfter=").append(cycle.updatedAfter ?: "none")
            append(" | syncEscalatedToFull=").append(cycle.escalatedToFull)
            append(" | syncOutcome=").append(cycle.outcome)
        }
        append(" | sync_error_decode=").append(errorCounters.syncErrorDecode)
        append(" | outbox_failed_permanent=").append(errorCounters.outboxFailedPermanent)
        append(" | outbox_retry_exhausted=").append(errorCounters.outboxRetryExhausted)
    }
}

class MovitCacheFreshnessDiagnostics(
    private val localStore: MovitLocalStore,
    private val metadataStore: MovitSyncMetadataStore,
) {
    private val telemetry = MovitSyncTelemetry(localStore)

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
            lastSyncCycle = telemetry.readLastSyncCycle(),
            errorCounters = telemetry.readCounters(),
        )
    }
}
