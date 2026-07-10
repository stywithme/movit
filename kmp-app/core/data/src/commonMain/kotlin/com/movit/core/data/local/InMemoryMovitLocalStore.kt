package com.movit.core.data.local

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitClock

/** Lightweight [MovitLocalStore] for unit tests — no SQL driver required. */
class InMemoryMovitLocalStore : MovitLocalStore {
    private val jsonCache = mutableMapOf<String, String>()
    private val jsonUpdatedAt = mutableMapOf<String, Long>()
    private val outbox = linkedMapOf<String, OutboxEntry>()
    private val syncMetadata = mutableMapOf<String, SyncMetadata>()
    private val sessionJournals = mutableMapOf<String, SessionJournalRow>()

    override fun readJsonCache(store: String, key: String): String? =
        jsonCache[cacheKey(store, key)]

    override fun writeJsonCache(store: String, key: String, value: String) {
        val ck = cacheKey(store, key)
        jsonCache[ck] = value
        jsonUpdatedAt[ck] = MovitClock.nowEpochMs()
    }

    /** Test helper: write with an explicit timestamp (for GC / LRU policies). */
    fun writeJsonCacheAt(store: String, key: String, value: String, updatedAtEpochMs: Long) {
        val ck = cacheKey(store, key)
        jsonCache[ck] = value
        jsonUpdatedAt[ck] = updatedAtEpochMs
    }

    override fun removeJsonCache(store: String, key: String) {
        val ck = cacheKey(store, key)
        jsonCache.remove(ck)
        jsonUpdatedAt.remove(ck)
    }

    override fun listJsonCacheEntries(store: String): Map<String, String> {
        val prefix = cacheKey(store, "")
        return jsonCache
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    override fun listJsonCacheEntriesWithTimestamps(store: String): List<JsonCacheEntryMeta> {
        val prefix = cacheKey(store, "")
        return jsonCache
            .filterKeys { it.startsWith(prefix) }
            .map { (fullKey, payload) ->
                JsonCacheEntryMeta(
                    key = fullKey.removePrefix(prefix),
                    payload = payload,
                    updatedAtEpochMs = jsonUpdatedAt[fullKey] ?: 0L,
                )
            }
    }

    override fun readSyncMetadata(scope: String): SyncMetadata? = syncMetadata[scope]

    override fun writeSyncMetadata(scope: String, version: String?, lastSyncAt: String?) {
        syncMetadata[scope] = SyncMetadata(
            scope = scope,
            version = version,
            lastSyncAt = lastSyncAt,
            updatedAtEpochMs = MovitClock.nowEpochMs(),
        )
    }

    override fun removeSyncMetadata(scope: String) {
        syncMetadata.remove(scope)
    }

    override suspend fun insertOutbox(entry: OutboxEntry) {
        outbox[entry.id] = entry
    }

    override suspend fun getOutboxById(id: String): OutboxEntry? = outbox[id]

    override suspend fun listPendingOutbox(): List<OutboxEntry> =
        outbox.values
            .filter { it.status == OutboxStatus.PENDING }
            .sortedBy { it.createdAt }

    override suspend fun listAllOutbox(): List<OutboxEntry> =
        outbox.values.sortedBy { it.createdAt }

    override suspend fun recoverInFlightOutbox() {
        outbox.entries.forEach { (id, entry) ->
            if (entry.status == OutboxStatus.IN_FLIGHT) {
                outbox[id] = entry.copy(status = OutboxStatus.PENDING)
            }
        }
    }

    override suspend fun updateOutboxStatus(
        id: String,
        status: OutboxStatus,
        attempts: Int,
        nextAttemptAtEpochMs: Long?,
    ) {
        val existing = outbox[id] ?: return
        outbox[id] = existing.copy(
            status = status,
            attempts = attempts,
            nextAttemptAtEpochMs = nextAttemptAtEpochMs,
        )
    }

    override suspend fun deleteOutbox(id: String) {
        outbox.remove(id)
    }

    override suspend fun purgeSucceededOutboxOlderThan(cutoffEpochMs: Long): Int {
        val toRemove = outbox.entries
            .filter { (_, entry) ->
                entry.status == OutboxStatus.SUCCEEDED && entry.createdAt < cutoffEpochMs
            }
            .map { it.key }
        toRemove.forEach { outbox.remove(it) }
        return toRemove.size
    }

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long =
        outbox.values.count { it.status == status }.toLong()

    override suspend fun countGuestOutbox(): Long =
        outbox.values.count { it.ownerUserId == null }.toLong()

    override suspend fun deleteOutboxOwnedByOtherUsers(keepUserId: String) {
        val toRemove = outbox.entries
            .filter { (_, entry) ->
                val owner = entry.ownerUserId
                owner != null && owner != keepUserId
            }
            .map { it.key }
        toRemove.forEach { outbox.remove(it) }
    }

    override suspend fun deleteAllGuestOutbox() {
        val toRemove = outbox.entries.filter { it.value.ownerUserId == null }.map { it.key }
        toRemove.forEach { outbox.remove(it) }
    }

    override suspend fun deleteGuestOutboxOlderThan(cutoffEpochMs: Long): Int {
        val toRemove = outbox.entries
            .filter { (_, entry) -> entry.ownerUserId == null && entry.createdAt < cutoffEpochMs }
            .map { it.key }
        toRemove.forEach { outbox.remove(it) }
        return toRemove.size
    }

    override suspend fun attributeGuestOutboxToUser(userId: String) {
        outbox.entries.forEach { (id, entry) ->
            if (entry.ownerUserId == null) {
                outbox[id] = entry.copy(ownerUserId = userId)
            }
        }
    }

    override suspend fun clearReadCaches() {
        val preserved = jsonCache.filterKeys { fullKey ->
            val sep = fullKey.indexOf("::")
            if (sep < 0) return@filterKeys false
            val store = fullKey.substring(0, sep)
            val key = fullKey.substring(sep + 2)
            store == MovitCacheKeys.AUTH_LIFECYCLE_STORE ||
                (store == MovitCacheKeys.REPORTS_STORE && MovitClearScopeKeys.isDurableReportsKey(key))
        }
        jsonCache.clear()
        jsonCache.putAll(preserved)
        syncMetadata.clear()
    }

    override suspend fun clearDurableWrites() {
        outbox.clear()
        sessionJournals.clear()
        val toRemove = jsonCache.keys.filter { fullKey ->
            val sep = fullKey.indexOf("::")
            if (sep < 0) return@filter false
            val store = fullKey.substring(0, sep)
            val key = fullKey.substring(sep + 2)
            store == MovitCacheKeys.AUTH_LIFECYCLE_STORE ||
                (store == MovitCacheKeys.REPORTS_STORE && MovitClearScopeKeys.isDurableReportsKey(key))
        }
        toRemove.forEach { jsonCache.remove(it) }
    }

    override suspend fun clearAllUserData() {
        clearReadCaches()
        clearDurableWrites()
    }

    override fun upsertSessionJournal(
        sessionId: String,
        exerciseId: String,
        payloadJson: String,
        status: String,
        updatedAtEpochMs: Long,
    ) {
        sessionJournals[sessionId] = SessionJournalRow(
            sessionId = sessionId,
            exerciseId = exerciseId,
            payloadJson = payloadJson,
            status = status,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    override fun selectSessionJournal(sessionId: String): SessionJournalRow? =
        sessionJournals[sessionId]

    override fun listActiveSessionJournals(): List<SessionJournalRow> =
        sessionJournals.values.filter { it.status == "active" }

    override fun deleteSessionJournal(sessionId: String) {
        sessionJournals.remove(sessionId)
    }

    override fun <T> transaction(block: () -> T): T = block()

    private fun cacheKey(store: String, key: String): String = "$store::$key"
}
