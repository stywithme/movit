package com.movit.core.data.sync

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import kotlinx.serialization.Serializable

@Serializable
data class LastSyncCycleRecord(
    val reason: String,
    val isFull: Boolean? = null,
    val updatedAfter: String? = null,
    val escalatedToFull: Boolean = false,
    val outcome: String,
)

data class SyncErrorCounters(
    val syncErrorDecode: Int = 0,
    val outboxFailedPermanent: Int = 0,
    val outboxRetryExhausted: Int = 0,
)

/**
 * Lightweight sync/outbox diagnostics persisted in [MovitCacheKeys.SYNC_STORE].
 */
class MovitSyncTelemetry(
    private val store: MovitLocalStore,
) {
    fun recordSyncCycle(record: LastSyncCycleRecord) {
        store.writeJsonCache(
            MovitCacheKeys.SYNC_STORE,
            MovitCacheKeys.SYNC_DIAG_LAST_CYCLE,
            MovitJson.encodeToString(LastSyncCycleRecord.serializer(), record),
        )
    }

    fun readLastSyncCycle(): LastSyncCycleRecord? {
        val raw = store.readJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_DIAG_LAST_CYCLE)
            ?: return null
        return runCatching {
            MovitJson.decodeFromString(LastSyncCycleRecord.serializer(), raw)
        }.getOrNull()
    }

    fun incrementSyncErrorDecode() = increment(MovitCacheKeys.SYNC_DIAG_ERROR_DECODE_COUNT)

    fun incrementOutboxFailedPermanent() = increment(MovitCacheKeys.SYNC_DIAG_OUTBOX_FAILED_PERMANENT)

    fun incrementOutboxRetryExhausted() = increment(MovitCacheKeys.SYNC_DIAG_OUTBOX_RETRY_EXHAUSTED)

    fun incrementCatalogGraphIncomplete() = increment(MovitCacheKeys.SYNC_DIAG_CATALOG_INCOMPLETE)

    fun readCounters(): SyncErrorCounters =
        SyncErrorCounters(
            syncErrorDecode = readCounter(MovitCacheKeys.SYNC_DIAG_ERROR_DECODE_COUNT),
            outboxFailedPermanent = readCounter(MovitCacheKeys.SYNC_DIAG_OUTBOX_FAILED_PERMANENT),
            outboxRetryExhausted = readCounter(MovitCacheKeys.SYNC_DIAG_OUTBOX_RETRY_EXHAUSTED),
        )

    internal fun outboxReplayLogLine(
        operationId: String,
        type: OutboxOperationType,
        outcomeName: String,
    ): String = buildString {
        append("outbox_replay")
        append(" | id=").append(operationId)
        append(" | type=").append(type.name)
        append(" | outcome=").append(outcomeName)
    }

    private fun increment(key: String) {
        store.writeIntCache(
            MovitCacheKeys.SYNC_STORE,
            key,
            readCounter(key) + 1,
        )
    }

    private fun readCounter(key: String): Int =
        store.readIntCache(MovitCacheKeys.SYNC_STORE, key, default = 0)
}
