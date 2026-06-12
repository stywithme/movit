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
        val legacy = platform().readCache(store, key) ?: return null
        sqlStore.writeJsonCache(store, key, legacy)
        return legacy
    }

    override fun writeJsonCache(store: String, key: String, value: String) {
        sqlStore.writeJsonCache(store, key, value)
    }

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

    override suspend fun recoverInFlightOutbox() = sqlStore.recoverInFlightOutbox()

    override suspend fun updateOutboxStatus(id: String, status: OutboxStatus, attempts: Int) =
        sqlStore.updateOutboxStatus(id, status, attempts)

    override suspend fun deleteOutbox(id: String) = sqlStore.deleteOutbox(id)

    override suspend fun purgeSucceededOutboxOlderThan(cutoffEpochMs: Long): Int =
        sqlStore.purgeSucceededOutboxOlderThan(cutoffEpochMs)

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long =
        sqlStore.countOutboxByStatus(status)

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
        bindings.writeCache(store, key, activeId)
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
        )

        val LEGACY_PREF_STORE_MIGRATIONS = listOf(
            MovitCacheKeys.LEGACY_USER_EXERCISE_PREFERENCES_STORE to MovitCacheKeys.PREFERENCES_STORE,
        )
    }
}
