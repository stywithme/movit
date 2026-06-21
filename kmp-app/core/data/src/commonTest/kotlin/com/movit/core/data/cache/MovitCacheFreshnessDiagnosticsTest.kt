package com.movit.core.data.cache

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovitCacheFreshnessDiagnosticsTest {

    @Test
    fun snapshot_reportsKmpStoreFreshness() = runBlocking {
        val store = InMemoryMovitLocalStore()
        store.writeJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA, """{"programs":[]}""")
        store.writeJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA, """{"trainMode":{}}""")
        store.writeJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LAST_TIMESTAMP, "2026-06-18T12:00:00Z")
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_EXERCISES, 3)
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_WORKOUTS, 2)
        store.writeIntCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.SYNC_LOCAL_PROGRAMS, 1)
        store.writeJsonCache(MovitCacheKeys.SYNC_STORE, MovitCacheKeys.LEGACY_CUTOVER_V1, "true")
        store.writeSyncMetadata(MovitCacheKeys.SYNC_STORE, version = "42", lastSyncAt = "2026-06-18T12:00:00Z")
        store.insertOutbox(
            OutboxEntry(
                id = "freshness-op",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
            ),
        )

        val diagnostics = MovitCacheFreshnessDiagnostics(store, MovitSyncMetadataStore(store))
        val report = diagnostics.snapshot()

        assertEquals("2026-06-18T12:00:00Z", report.lastSyncAt)
        assertEquals("42", report.serverVersion)
        assertEquals(3, report.entityCounts.exercises)
        assertEquals(1L, report.pendingOutboxCount)
        assertTrue(report.hasExploreCache)
        assertTrue(report.hasHomeCache)
        assertTrue(report.legacyCutoverComplete)
        assertTrue(report.toLogLine().contains("cutover=v1"))
    }
}
