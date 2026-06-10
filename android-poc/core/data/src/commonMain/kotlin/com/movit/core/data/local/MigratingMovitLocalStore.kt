package com.movit.core.data.local

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys

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

    override suspend fun countOutboxByStatus(status: OutboxStatus): Long =
        sqlStore.countOutboxByStatus(status)

    fun migrateKnownCachesFromPlatform() {
        val bindings = platform()
        KNOWN_STATIC_KEYS.forEach { (store, key) ->
            if (sqlStore.readJsonCache(store, key) == null) {
                bindings.readCache(store, key)?.let { legacy ->
                    sqlStore.writeJsonCache(store, key, legacy)
                }
            }
        }
    }

    private companion object {
        val KNOWN_STATIC_KEYS = listOf(
            MovitCacheKeys.EXPLORE_STORE to MovitCacheKeys.EXPLORE_DATA,
            MovitCacheKeys.EXPLORE_STORE to MovitCacheKeys.EXPLORE_LAST_SYNC,
            MovitCacheKeys.HOME_STORE to MovitCacheKeys.HOME_DATA,
            MovitCacheKeys.REPORTS_STORE to MovitCacheKeys.REPORTS_DASHBOARD,
        )
    }
}
