package com.movit.core.data.local

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.network.MovitClock

/** Lightweight [MovitLocalStore] for unit tests — no SQL driver required. */
class InMemoryMovitLocalStore : MovitLocalStore {
    private val jsonCache = mutableMapOf<String, String>()
    private val outbox = linkedMapOf<String, OutboxEntry>()
    private val syncMetadata = mutableMapOf<String, SyncMetadata>()
    private val sessionJournals = mutableMapOf<String, SessionJournalRow>()

    override fun readJsonCache(store: String, key: String): String? =
        jsonCache[cacheKey(store, key)]

    override fun writeJsonCache(store: String, key: String, value: String) {
        jsonCache[cacheKey(store, key)] = value
    }

    override fun removeJsonCache(store: String, key: String) {
        jsonCache.remove(cacheKey(store, key))
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

    override suspend fun recoverInFlightOutbox() {
        outbox.entries.forEach { (id, entry) ->
            if (entry.status == OutboxStatus.IN_FLIGHT) {
                outbox[id] = entry.copy(status = OutboxStatus.PENDING)
            }
        }
    }

    override suspend fun updateOutboxStatus(id: String, status: OutboxStatus, attempts: Int) {
        val existing = outbox[id] ?: return
        outbox[id] = existing.copy(status = status, attempts = attempts)
    }

    override suspend fun deleteOutbox(id: String) {
        outbox.remove(id)
    }

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long =
        outbox.values.count { it.status == status }.toLong()

    override suspend fun clearAllUserData() {
        jsonCache.clear()
        syncMetadata.clear()
        outbox.clear()
        sessionJournals.clear()
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

    private fun cacheKey(store: String, key: String): String = "$store::$key"
}
