package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.dto.MessageLibraryStatsDto
import com.movit.core.network.dto.SyncMetaDto

class MovitSyncMetadataStore(
    private val store: MovitLocalStore,
) {
    fun readEntityCounts(): MovitCacheDriftDetector.EntityCounts =
        MovitCacheDriftDetector.EntityCounts(
            exercises = store.readIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_EXERCISES),
            workouts = store.readIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_WORKOUTS),
            programs = store.readIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_PROGRAMS),
        )

    fun writeEntityCounts(counts: MovitCacheDriftDetector.EntityCounts) {
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_EXERCISES, counts.exercises)
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_WORKOUTS, counts.workouts)
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_PROGRAMS, counts.programs)
    }

    fun readMessageStats(): MovitCacheDriftDetector.MessageStatsSnapshot? {
        val messages = store.readIntCache(
            MovitCacheKeys.SYNC_STORE,
            MovitCacheKeys.SYNC_MSG_COUNT,
            MovitCacheDriftDetector.MessageStatsSnapshot.UNINITIALIZED,
        )
        if (messages == MovitCacheDriftDetector.MessageStatsSnapshot.UNINITIALIZED) return null
        return MovitCacheDriftDetector.MessageStatsSnapshot(
            totalMessages = messages,
            totalWithAudio = store.readIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_AUDIO),
            totalAssignments = store.readIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_ASSIGNMENTS),
            fingerprint = store.readJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_FINGERPRINT).orEmpty(),
        )
    }

    fun writeMessageStats(stats: MessageLibraryStatsDto?) {
        if (stats == null) return
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_COUNT, stats.totalMessages)
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_AUDIO, stats.totalWithAudio)
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_ASSIGNMENTS, stats.totalAssignments)
        store.writeJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_MSG_FINGERPRINT, stats.fingerprint)
    }

    fun writeFromSyncMeta(meta: SyncMetaDto?) {
        if (meta == null) return
        store.writeSyncMetadata(
            scope = MovitCacheKeys.SYNC_STORE,
            version = meta.serverVersion,
            lastSyncAt = null,
        )
        writeMessageStats(meta.messageLibraryStats)
    }

    fun readLastSyncTimestamp(): String? =
        store.readSyncMetadata(MovitCacheKeys.SYNC_STORE)?.lastSyncAt
            ?: store.readJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LAST_TIMESTAMP)

    fun writeLastSyncTimestamp(timestamp: String) {
        if (timestamp.isBlank()) return
        store.writeJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LAST_TIMESTAMP, timestamp)
        val version = store.readSyncMetadata(MovitCacheKeys.SYNC_STORE)?.version
        store.writeSyncMetadata(
            scope = MovitCacheKeys.SYNC_STORE,
            version = version,
            lastSyncAt = timestamp,
        )
    }
}
