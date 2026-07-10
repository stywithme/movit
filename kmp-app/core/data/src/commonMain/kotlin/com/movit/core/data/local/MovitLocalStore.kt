package com.movit.core.data.local

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxStatus

data class SyncMetadata(
    val scope: String,
    val version: String?,
    val lastSyncAt: String?,
    val updatedAtEpochMs: Long,
)

data class JsonCacheEntryMeta(
    val key: String,
    val payload: String,
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

    /** All entries in a logical cache namespace (used by one-time key canonicalization). */
    fun listJsonCacheEntries(store: String): Map<String, String> = emptyMap()

    /** Entries with [updated_at_epoch_ms] for GC / LRU (P2.5). */
    fun listJsonCacheEntriesWithTimestamps(store: String): List<JsonCacheEntryMeta> =
        listJsonCacheEntries(store).map { (key, payload) ->
            JsonCacheEntryMeta(key = key, payload = payload, updatedAtEpochMs = 0L)
        }

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

    /** All outbox rows (any status) — used for ownership / guest attribution. */
    suspend fun listAllOutbox(): List<OutboxEntry> = listPendingOutbox()

    /** Resets stale [OutboxStatus.IN_FLIGHT] rows after a crash mid-dispatch. */
    suspend fun recoverInFlightOutbox()

    suspend fun updateOutboxStatus(
        id: String,
        status: OutboxStatus,
        attempts: Int,
        nextAttemptAtEpochMs: Long? = null,
    )

    suspend fun deleteOutbox(id: String)

    /** Removes completed outbox rows older than [cutoffEpochMs] (F7 retention). */
    suspend fun purgeSucceededOutboxOlderThan(cutoffEpochMs: Long): Int

    suspend fun countOutboxByStatus(status: OutboxStatus): Long

    suspend fun countGuestOutbox(): Long = 0L

    /** Deletes outbox rows owned by a different signed-in user (keeps guest + [keepUserId]). */
    suspend fun deleteOutboxOwnedByOtherUsers(keepUserId: String) {}

    suspend fun deleteAllGuestOutbox() {}

    suspend fun deleteGuestOutboxOlderThan(cutoffEpochMs: Long): Int = 0

    suspend fun attributeGuestOutboxToUser(userId: String) {}

    /**
     * PR-7 read scope: catalog/home/explore/metrics/delta blobs + sync_metadata + audio manifest.
     * Preserves outbox, session journal, and unconfirmed post-training reports (+ indexes).
     */
    suspend fun clearReadCaches()

    /**
     * PR-7 durable writes: outbox + journal + unconfirmed post-training report blobs.
     * Frame/audio files on disk are cleared via [com.movit.core.data.platform.MovitPlatformBindings.clearUserFiles].
     */
    suspend fun clearDurableWrites()

    /** Full wipe — logout / delete account. Equivalent to read + durable. */
    suspend fun clearAllUserData() {
        clearReadCaches()
        clearDurableWrites()
    }

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

    /**
     * Runs [block] inside a single SQLDelight transaction when backed by SQLite.
     * In-memory / test stores execute [block] directly (no-op passthrough).
     * Use for atomic full-sync apply (records + indexes + aliases).
     */
    fun <T> transaction(block: () -> T): T = block()
}
