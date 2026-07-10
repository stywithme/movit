package com.movit.core.data.local

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.UserProgramExportDto

/**
 * SQLDelight-primary store with lazy migration from legacy platform JSON prefs.
 */
class MigratingMovitLocalStore(
    private val sqlStore: MovitLocalStore,
    private val platform: () -> MovitPlatformBindings,
) : MovitLocalStore {
    override fun readJsonCache(store: String, key: String): String? {
        sqlStore.readJsonCache(store, key)?.let { return it }
        if (!LegacyCatalogReadPolicy.allowsRuntimePlatformFallback(store)) {
            return null
        }
        val legacy = platform().readCache(store, key) ?: return null
        sqlStore.writeJsonCache(store, key, legacy)
        return legacy
    }

    override fun writeJsonCache(store: String, key: String, value: String) {
        sqlStore.writeJsonCache(store, key, value)
    }

    override fun listJsonCacheEntries(store: String): Map<String, String> =
        sqlStore.listJsonCacheEntries(store)

    override fun listJsonCacheEntriesWithTimestamps(store: String): List<JsonCacheEntryMeta> =
        sqlStore.listJsonCacheEntriesWithTimestamps(store)

    override fun removeJsonCache(store: String, key: String) {
        sqlStore.removeJsonCache(store, key)
        platform().removeCache(store, key)
    }

    override fun readSyncMetadata(scope: String): SyncMetadata? = sqlStore.readSyncMetadata(scope)

    override fun writeSyncMetadata(scope: String, version: String?, lastSyncAt: String?) {
        sqlStore.writeSyncMetadata(scope, version, lastSyncAt)
    }

    override fun removeSyncMetadata(scope: String) = sqlStore.removeSyncMetadata(scope)

    override suspend fun insertOutbox(entry: OutboxEntry) = sqlStore.insertOutbox(entry)

    override suspend fun getOutboxById(id: String) = sqlStore.getOutboxById(id)

    override suspend fun listPendingOutbox() = sqlStore.listPendingOutbox()

    override suspend fun listAllOutbox() = sqlStore.listAllOutbox()

    override suspend fun recoverInFlightOutbox() = sqlStore.recoverInFlightOutbox()

    override suspend fun updateOutboxStatus(
        id: String,
        status: OutboxStatus,
        attempts: Int,
        nextAttemptAtEpochMs: Long?,
    ) = sqlStore.updateOutboxStatus(id, status, attempts, nextAttemptAtEpochMs)

    override suspend fun deleteOutbox(id: String) = sqlStore.deleteOutbox(id)

    override suspend fun purgeSucceededOutboxOlderThan(cutoffEpochMs: Long): Int =
        sqlStore.purgeSucceededOutboxOlderThan(cutoffEpochMs)

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long =
        sqlStore.countOutboxByStatus(status)

    override suspend fun countGuestOutbox(): Long = sqlStore.countGuestOutbox()

    override suspend fun deleteOutboxOwnedByOtherUsers(keepUserId: String) =
        sqlStore.deleteOutboxOwnedByOtherUsers(keepUserId)

    override suspend fun deleteAllGuestOutbox() = sqlStore.deleteAllGuestOutbox()

    override suspend fun deleteGuestOutboxOlderThan(cutoffEpochMs: Long): Int =
        sqlStore.deleteGuestOutboxOlderThan(cutoffEpochMs)

    override suspend fun attributeGuestOutboxToUser(userId: String) =
        sqlStore.attributeGuestOutboxToUser(userId)

    override suspend fun clearReadCaches() = sqlStore.clearReadCaches()

    override suspend fun clearDurableWrites() = sqlStore.clearDurableWrites()

    override suspend fun clearWorkoutRunStore() = sqlStore.clearWorkoutRunStore()

    override suspend fun clearAllUserData() = sqlStore.clearAllUserData()

    override fun upsertSessionJournal(
        sessionId: String,
        exerciseId: String,
        payloadJson: String,
        status: String,
        updatedAtEpochMs: Long,
    ) = sqlStore.upsertSessionJournal(sessionId, exerciseId, payloadJson, status, updatedAtEpochMs)

    override fun selectSessionJournal(sessionId: String): SessionJournalRow? =
        sqlStore.selectSessionJournal(sessionId)

    override fun listActiveSessionJournals(): List<SessionJournalRow> =
        sqlStore.listActiveSessionJournals()

    override fun deleteSessionJournal(sessionId: String) = sqlStore.deleteSessionJournal(sessionId)

    override fun <T> transaction(block: () -> T): T = sqlStore.transaction(block)

    fun migrateKnownCachesFromPlatform() {
        val bindings = platform()
        KNOWN_STATIC_KEYS.forEach { (store, key) ->
            migrateEntry(bindings, store, key)
        }
        KNOWN_DYNAMIC_STORES.forEach { store ->
            bindings.readAllCacheEntries(store).forEach { (key, value) ->
                migrateEntry(bindings, store, key, prefetchedValue = value)
            }
        }
        LEGACY_PREF_STORE_MIGRATIONS.forEach { (legacyStore, targetStore) ->
            bindings.readAllCacheEntries(legacyStore).forEach { (key, value) ->
                migrateEntry(bindings, targetStore, key, prefetchedValue = value)
            }
        }
        migrateActiveUserProgramId(bindings)
        CanonicalCacheKeyMigrator(sqlStore, platform).migrateIfNeeded()
        backfillOutboxOwnerFromSession(bindings)
        markLegacyCutoverComplete()
    }

    private fun backfillOutboxOwnerFromSession(bindings: MovitPlatformBindings) {
        val ownerUserId = bindings.userId()?.takeIf { it.isNotBlank() } ?: return
        val sqlDelight = sqlStore as? SqlDelightMovitLocalStore ?: return
        sqlDelight.backfillOutboxOwnerUserId(ownerUserId)
    }

    private fun markLegacyCutoverComplete() {
        sqlStore.writeJsonCache(
            MovitCacheKeys.SYNC_STORE,
            MovitCacheKeys.LEGACY_CUTOVER_V1,
            "true",
        )
    }

    private fun migrateEntry(
        bindings: MovitPlatformBindings,
        store: String,
        key: String,
        prefetchedValue: String? = null,
    ) {
        if (sqlStore.readJsonCache(store, key) != null) return
        val legacy = prefetchedValue ?: bindings.readCache(store, key) ?: return
        sqlStore.writeJsonCache(store, key, legacy)
    }

    private fun migrateActiveUserProgramId(bindings: MovitPlatformBindings) {
        val store = MovitCacheKeys.PROGRAM_STORE
        val key = MovitCacheKeys.ACTIVE_USER_PROGRAM_ID
        if (sqlStore.readJsonCache(store, key) != null) return
        bindings.readCache(store, key)?.takeIf { it.isNotBlank() }?.let { activeId ->
            sqlStore.writeJsonCache(store, key, activeId)
            return
        }
        val legacyProgramsJson = bindings.readCache(
            MovitCacheKeys.LEGACY_USER_PROGRAM_STORE,
            MovitCacheKeys.LEGACY_USER_PROGRAMS_KEY,
        ) ?: return
        val activeId = runCatching {
            MovitJson.decodeFromString<List<UserProgramExportDto>>(legacyProgramsJson)
                .firstOrNull { it.isActive && it.id.isNotBlank() }
                ?.id
        }.getOrNull() ?: return
        sqlStore.writeJsonCache(store, key, activeId)
    }

    private companion object {
        val KNOWN_STATIC_KEYS = listOf(
            MovitCacheKeys.EXPLORE_STORE to MovitCacheKeys.EXPLORE_DATA,
            MovitCacheKeys.EXPLORE_STORE to MovitCacheKeys.EXPLORE_LAST_SYNC,
            MovitCacheKeys.HOME_STORE to MovitCacheKeys.HOME_DATA,
            MovitCacheKeys.REPORTS_STORE to MovitCacheKeys.REPORTS_DASHBOARD,
            MovitCacheKeys.PROGRAM_STORE to MovitCacheKeys.ACTIVE_USER_PROGRAM_ID,
            MovitCacheKeys.AUDIO_STORE to MovitCacheKeys.AUDIO_BASE_URL,
            MovitCacheKeys.AUDIO_STORE to MovitCacheKeys.AUDIO_MANIFEST_JSON,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_LAST_TIMESTAMP,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_SERVER_VERSION,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_LOCAL_EXERCISES,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_LOCAL_WORKOUTS,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_LOCAL_PROGRAMS,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_MSG_COUNT,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_MSG_AUDIO,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_MSG_ASSIGNMENTS,
            MovitCacheKeys.SYNC_STORE to MovitCacheKeys.SYNC_MSG_FINGERPRINT,
        )

        val KNOWN_DYNAMIC_STORES = listOf(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.PROGRAM_STORE,
            MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        )

        val LEGACY_PREF_STORE_MIGRATIONS = listOf(
            MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE to MovitCacheKeys.PREFERENCES_STORE,
            MovitCacheKeys.LEGACY_DAY_CUSTOMIZATION_STORE to MovitCacheKeys.DAY_CUSTOMIZATION_STORE,
        )
    }
}
