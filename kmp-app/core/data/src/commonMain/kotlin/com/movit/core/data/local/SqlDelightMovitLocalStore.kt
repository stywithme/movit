package com.movit.core.data.local

import com.movit.core.data.db.MovitDatabase
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.MovitCacheKeys
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

    override fun <T> transaction(block: () -> T): T = database.transactionWithResult {
        block()
    }

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

    override fun listJsonCacheEntries(store: String): Map<String, String> =
        jsonQueries.selectAllByStore(store).executeAsList()
            .associate { row -> row.cache_key to row.json_payload }

    override fun listJsonCacheEntriesWithTimestamps(store: String): List<JsonCacheEntryMeta> =
        jsonQueries.selectAllByStoreWithTimestamps(store).executeAsList()
            .map { row ->
                JsonCacheEntryMeta(
                    key = row.cache_key,
                    payload = row.json_payload,
                    updatedAtEpochMs = row.updated_at_epoch_ms,
                )
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
                owner_user_id = entry.ownerUserId,
                next_attempt_at_epoch_ms = entry.nextAttemptAtEpochMs,
            )
        }
    }

    /** P0.2 upgrade backfill: attribute pre-migration outbox rows to the current session user. */
    fun backfillOutboxOwnerUserId(ownerUserId: String) {
        outboxQueries.backfillOwnerUserIdWhereNull(ownerUserId)
    }

    override suspend fun getOutboxById(id: String): OutboxEntry? = withContext(Dispatchers.IO) {
        outboxQueries.selectById(id).executeAsOneOrNull()?.toOutboxEntry()
    }

    override suspend fun listPendingOutbox(): List<OutboxEntry> = withContext(Dispatchers.IO) {
        outboxQueries.selectPending().executeAsList().map { it.toOutboxEntry() }
    }

    override suspend fun listAllOutbox(): List<OutboxEntry> = withContext(Dispatchers.IO) {
        outboxQueries.selectAll().executeAsList().map { it.toOutboxEntry() }
    }

    override suspend fun recoverInFlightOutbox() {
        withContext(Dispatchers.IO) {
            outboxQueries.recoverInFlight()
        }
    }

    override suspend fun updateOutboxStatus(
        id: String,
        status: OutboxStatus,
        attempts: Int,
        nextAttemptAtEpochMs: Long?,
    ) {
        withContext(Dispatchers.IO) {
            outboxQueries.updateStatus(
                status = status.storageValue,
                attempts = attempts.toLong(),
                last_error = null,
                next_attempt_at_epoch_ms = nextAttemptAtEpochMs,
                id = id,
            )
        }
    }

    override suspend fun deleteOutbox(id: String) {
        withContext(Dispatchers.IO) {
            outboxQueries.deleteById(id)
        }
    }

    override suspend fun purgeSucceededOutboxOlderThan(cutoffEpochMs: Long): Int = withContext(Dispatchers.IO) {
        outboxQueries.deleteSucceededOlderThan(cutoffEpochMs)
        // SQLDelight delete queries do not return affected row count in common API.
        0
    }

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long = withContext(Dispatchers.IO) {
        outboxQueries.selectByStatus(status.storageValue).executeAsList().size.toLong()
    }

    override suspend fun countGuestOutbox(): Long = withContext(Dispatchers.IO) {
        outboxQueries.countGuest().executeAsOne()
    }

    override suspend fun deleteOutboxOwnedByOtherUsers(keepUserId: String) {
        withContext(Dispatchers.IO) {
            outboxQueries.deleteOwnedByOtherUsers(keepUserId)
        }
    }

    override suspend fun deleteAllGuestOutbox() {
        withContext(Dispatchers.IO) {
            outboxQueries.deleteAllGuest()
        }
    }

    override suspend fun deleteGuestOutboxOlderThan(cutoffEpochMs: Long): Int = withContext(Dispatchers.IO) {
        outboxQueries.deleteGuestOlderThan(cutoffEpochMs)
        0
    }

    override suspend fun attributeGuestOutboxToUser(userId: String) {
        withContext(Dispatchers.IO) {
            outboxQueries.attributeGuestToUser(userId)
        }
    }

    override suspend fun clearReadCaches() {
        withContext(Dispatchers.IO) {
            val preserved = mutableMapOf<Pair<String, String>, String>()
            jsonQueries.selectAllStoreKeys().executeAsList().forEach { row ->
                val keep =
                    MovitClearScopeKeys.isDurableJsonStore(row.store) ||
                        (
                            row.store == MovitCacheKeys.REPORTS_STORE &&
                                MovitClearScopeKeys.isDurableReportsKey(row.cache_key)
                            )
                if (keep) {
                    val payload = jsonQueries.selectByStoreAndKey(row.store, row.cache_key).executeAsOneOrNull()
                    if (payload != null) {
                        preserved[row.store to row.cache_key] = payload
                    }
                }
            }
            jsonQueries.deleteAll()
            syncQueries.deleteAll()
            preserved.forEach { (storeKey, payload) ->
                val (store, key) = storeKey
                jsonQueries.upsert(
                    store = store,
                    cache_key = key,
                    json_payload = payload,
                    updated_at_epoch_ms = MovitClock.nowEpochMs(),
                )
            }
        }
    }

    override suspend fun clearWorkoutRunStore() {
        withContext(Dispatchers.IO) {
            jsonQueries.selectAllStoreKeys().executeAsList()
                .filter { it.store == MovitCacheKeys.WORKOUT_RUN_STORE }
                .forEach { row ->
                    jsonQueries.deleteByStoreAndKey(row.store, row.cache_key)
                }
        }
    }

    override suspend fun clearDurableWrites() {
        withContext(Dispatchers.IO) {
            outboxQueries.deleteAll()
            journalQueries.deleteAllJournals()
            clearWorkoutRunStore()
            jsonQueries.selectAllStoreKeys().executeAsList()
                .filter {
                    (it.store == MovitCacheKeys.REPORTS_STORE &&
                        MovitClearScopeKeys.isDurableReportsKey(it.cache_key)) ||
                        it.store == MovitCacheKeys.AUTH_LIFECYCLE_STORE
                }
                .forEach { row ->
                    jsonQueries.deleteByStoreAndKey(row.store, row.cache_key)
                }
        }
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
            ownerUserId = owner_user_id,
            nextAttemptAtEpochMs = next_attempt_at_epoch_ms,
        )
}

private val OutboxStatus.storageValue: String
    get() = name.lowercase()

private fun outboxStatusFromStorage(value: String): OutboxStatus =
    OutboxStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OutboxStatus.PENDING
