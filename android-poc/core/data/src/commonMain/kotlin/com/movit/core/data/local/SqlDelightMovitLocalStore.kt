package com.movit.core.data.local

import com.movit.core.data.db.MovitDatabase
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.network.MovitClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class SqlDelightMovitLocalStore(
    private val database: MovitDatabase,
) : MovitLocalStore {
    private val jsonQueries = database.jsonCacheEntryQueries
    private val syncQueries = database.syncMetadataQueries
    private val outboxQueries = database.outboxQueries
    private val journalQueries = database.sessionJournalQueries

    override fun readJsonCache(store: String, key: String): String? =
        jsonQueries.selectByStoreAndKey(store, key).executeAsOneOrNull()

    override fun writeJsonCache(store: String, key: String, value: String) {
        jsonQueries.upsert(
            store = store,
            cache_key = key,
            json_payload = value,
            updated_at_epoch_ms = MovitClock.nowEpochMs(),
        )
    }

    override fun removeJsonCache(store: String, key: String) {
        jsonQueries.deleteByStoreAndKey(store, key)
    }

    override fun readSyncMetadata(scope: String): SyncMetadata? =
        syncQueries.selectByScope(scope).executeAsOneOrNull()?.let {
            SyncMetadata(
                scope = it.scope,
                version = it.version,
                lastSyncAt = it.last_sync_at,
                updatedAtEpochMs = it.updated_at_epoch_ms,
            )
        }

    override fun writeSyncMetadata(scope: String, version: String?, lastSyncAt: String?) {
        syncQueries.upsert(
            scope = scope,
            version = version,
            last_sync_at = lastSyncAt,
            updated_at_epoch_ms = MovitClock.nowEpochMs(),
        )
    }

    override fun removeSyncMetadata(scope: String) {
        syncQueries.deleteByScope(scope)
    }

    override suspend fun insertOutbox(entry: OutboxEntry) {
        withContext(Dispatchers.IO) {
            outboxQueries.insert(
                id = entry.id,
                operation_type = entry.type.name,
                payload_json = entry.payload,
                idempotency_key = entry.id,
                created_at_epoch_ms = entry.createdAt,
                attempts = entry.attempts.toLong(),
                status = entry.status.storageValue,
                last_error = null,
            )
        }
    }

    override suspend fun getOutboxById(id: String): OutboxEntry? = withContext(Dispatchers.IO) {
        outboxQueries.selectById(id).executeAsOneOrNull()?.toOutboxEntry()
    }

    override suspend fun listPendingOutbox(): List<OutboxEntry> = withContext(Dispatchers.IO) {
        outboxQueries.selectPending().executeAsList().map { it.toOutboxEntry() }
    }

    override suspend fun recoverInFlightOutbox() {
        withContext(Dispatchers.IO) {
            outboxQueries.recoverInFlight()
        }
    }

    override suspend fun updateOutboxStatus(id: String, status: OutboxStatus, attempts: Int) {
        withContext(Dispatchers.IO) {
            outboxQueries.updateStatus(
                status = status.storageValue,
                attempts = attempts.toLong(),
                last_error = null,
                id = id,
            )
        }
    }

    override suspend fun deleteOutbox(id: String) {
        withContext(Dispatchers.IO) {
            outboxQueries.deleteById(id)
        }
    }

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long = withContext(Dispatchers.IO) {
        outboxQueries.selectByStatus(status.storageValue).executeAsList().size.toLong()
    }

    override suspend fun clearAllUserData() {
        withContext(Dispatchers.IO) {
            jsonQueries.deleteAll()
            syncQueries.deleteAll()
            outboxQueries.deleteAll()
            journalQueries.deleteAllJournals()
        }
    }

    override fun upsertSessionJournal(
        sessionId: String,
        exerciseId: String,
        payloadJson: String,
        status: String,
        updatedAtEpochMs: Long,
    ) {
        journalQueries.upsertJournal(
            session_id = sessionId,
            exercise_id = exerciseId,
            payload_json = payloadJson,
            status = status,
            updated_at_epoch_ms = updatedAtEpochMs,
        )
    }

    override fun selectSessionJournal(sessionId: String): SessionJournalRow? =
        journalQueries.selectBySessionId(sessionId).executeAsOneOrNull()?.toRow()

    override fun listActiveSessionJournals(): List<SessionJournalRow> =
        journalQueries.selectActiveJournals().executeAsList().map { it.toRow() }

    override fun deleteSessionJournal(sessionId: String) {
        journalQueries.deleteBySessionId(sessionId)
    }

    private fun com.movit.core.data.db.Session_journal_entry.toRow(): SessionJournalRow =
        SessionJournalRow(
            sessionId = session_id,
            exerciseId = exercise_id,
            payloadJson = payload_json,
            status = status,
            updatedAtEpochMs = updated_at_epoch_ms,
        )

    private fun com.movit.core.data.db.Outbox_entry.toOutboxEntry(): OutboxEntry =
        OutboxEntry(
            id = id,
            type = OutboxOperationType.valueOf(operation_type),
            payload = payload_json,
            createdAt = created_at_epoch_ms,
            attempts = attempts.toInt(),
            status = outboxStatusFromStorage(status),
        )
}

private val OutboxStatus.storageValue: String
    get() = name.lowercase()

private fun outboxStatusFromStorage(value: String): OutboxStatus =
    OutboxStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OutboxStatus.PENDING
