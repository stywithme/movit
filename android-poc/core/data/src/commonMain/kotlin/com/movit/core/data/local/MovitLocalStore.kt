package com.movit.core.data.local

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxStatus

data class SyncMetadata(
    val scope: String,
    val version: String?,
    val lastSyncAt: String?,
    val updatedAtEpochMs: Long,
)

/**
 * Durable local persistence: JSON entity cache, sync metadata (WS-4), outbox queue (WS-2).
 */
interface MovitLocalStore {
    fun readJsonCache(store: String, key: String): String?

    fun writeJsonCache(store: String, key: String, value: String)

    fun removeJsonCache(store: String, key: String)

    fun readString(store: String, key: String): String? = readJsonCache(store, key)

    fun writeString(store: String, key: String, value: String) = writeJsonCache(store, key, value)

    fun remove(store: String, key: String) = removeJsonCache(store, key)

    fun readInt(store: String, key: String, default: Int = 0): Int =
        readJsonCache(store, key)?.toIntOrNull() ?: default

    fun writeInt(store: String, key: String, value: Int) = writeJsonCache(store, key, value.toString())

    fun readIntCache(store: String, key: String, default: Int = 0): Int = readInt(store, key, default)

    fun writeIntCache(store: String, key: String, value: Int) = writeInt(store, key, value)

    fun readSyncMetadata(scope: String): SyncMetadata?

    fun writeSyncMetadata(scope: String, version: String?, lastSyncAt: String?)

    fun removeSyncMetadata(scope: String)

    suspend fun insertOutbox(entry: OutboxEntry)

    suspend fun getOutboxById(id: String): OutboxEntry?

    suspend fun listPendingOutbox(): List<OutboxEntry>

    /** Resets stale [OutboxStatus.IN_FLIGHT] rows after a crash mid-dispatch. */
    suspend fun recoverInFlightOutbox()

    suspend fun updateOutboxStatus(id: String, status: OutboxStatus, attempts: Int)

    suspend fun deleteOutbox(id: String)

    /** Removes completed outbox rows older than [cutoffEpochMs] (F7 retention). */
    suspend fun purgeSucceededOutboxOlderThan(cutoffEpochMs: Long): Int

    suspend fun countOutboxByStatus(status: OutboxStatus): Long

    suspend fun clearAllUserData()

    fun upsertSessionJournal(
        sessionId: String,
        exerciseId: String,
        payloadJson: String,
        status: String,
        updatedAtEpochMs: Long = com.movit.core.network.MovitClock.nowEpochMs(),
    )

    fun selectSessionJournal(sessionId: String): SessionJournalRow?

    fun listActiveSessionJournals(): List<SessionJournalRow>

    fun deleteSessionJournal(sessionId: String)
}
