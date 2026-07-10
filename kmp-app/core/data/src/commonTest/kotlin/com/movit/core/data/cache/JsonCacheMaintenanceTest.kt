package com.movit.core.data.cache

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.sync.WeekOfflinePackPrefetcher
import com.movit.core.network.MovitJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonCacheMaintenanceTest {

    private val now = 1_000_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun postTraining_oldUnprotected_removesCompanionIndexes() {
        val store = InMemoryMovitLocalStore()
        val reportId = "r-old"
        store.writeJsonCacheAt(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey(reportId),
            """{"id":"$reportId"}""",
            now - 61 * dayMs,
        )
        store.writeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.sessionReportKey(reportId),
            """{"id":"$reportId"}""",
        )
        store.writeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportSessionExerciseKey(reportId),
            """{"sessionExerciseKey":"session-ex-1"}""",
        )
        store.writeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.exerciseSetReportsIndexKey("session-ex-1"),
            """{"reportsBySet":{"1":"$reportId","2":"r-keep"}}""",
        )
        val maintenance = JsonCacheMaintenance(store) { now }
        assertEquals(1, maintenance.purgePostTrainingReports(emptySet()))
        assertNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.postTrainingReportKey(reportId)))
        assertNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.sessionReportKey(reportId)))
        assertNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.reportSessionExerciseKey(reportId)))
        val remainingIndex = store.readJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.exerciseSetReportsIndexKey("session-ex-1"),
        )
        assertNotNull(remainingIndex)
        assertTrue(remainingIndex.contains("r-keep"))
        assertTrue(!remainingIndex.contains(reportId))
    }

    @Test
    fun postTraining_oldUnprotected_isRemoved() {
        val store = InMemoryMovitLocalStore()
        store.writeJsonCacheAt(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey("r-old"),
            """{"id":"r-old"}""",
            now - 61 * dayMs,
        )
        val maintenance = JsonCacheMaintenance(store) { now }
        assertEquals(1, maintenance.purgePostTrainingReports(emptySet()))
        assertNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.postTrainingReportKey("r-old")))
    }

    @Test
    fun postTraining_protectedByPendingOutbox_neverDeleted() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            store.writeJsonCacheAt(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.postTrainingReportKey("r-pending"),
                """{"id":"r-pending"}""",
                now - 120 * dayMs,
            )
            store.insertOutbox(
                OutboxEntry(
                    id = "r-pending",
                    type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                    payload = "{}",
                    createdAt = now - 120 * dayMs,
                    attempts = 1,
                    status = OutboxStatus.PENDING,
                ),
            )
            val maintenance = JsonCacheMaintenance(store) { now }
            val protected = maintenance.protectedPostTrainingReportIds()
            assertEquals(setOf("r-pending"), protected)
            assertEquals(0, maintenance.purgePostTrainingReports(protected))
            assertNotNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.postTrainingReportKey("r-pending")))
        }
    }

    @Test
    fun postTraining_succeededPurgedFromOutbox_absenceMeansConfirmed_canDelete() {
        val store = InMemoryMovitLocalStore()
        // No outbox row (SUCCEEDED already purged after 7d) + age > 60d → delete.
        store.writeJsonCacheAt(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey("r-done"),
            """{"id":"r-done"}""",
            now - 70 * dayMs,
        )
        val maintenance = JsonCacheMaintenance(store) { now }
        assertEquals(1, maintenance.purgePostTrainingReports(emptySet()))
    }

    @Test
    fun plannedReports_keepsNewest90() {
        val store = InMemoryMovitLocalStore()
        val ids = (1..100).map { "pw-$it" }
        ids.forEachIndexed { index, id ->
            store.writeJsonCacheAt(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.plannedWorkoutReportKey(id),
                """{"id":"$id"}""",
                now - (100 - index) * dayMs,
            )
        }
        store.writeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.PLANNED_WORKOUT_REPORTS_INDEX,
            MovitJson.encodeToString(ListSerializer(String.serializer()), ids),
        )
        val maintenance = JsonCacheMaintenance(store) { now }
        val removed = maintenance.purgePlannedWorkoutReports()
        assertEquals(10, removed)
        assertNotNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.plannedWorkoutReportKey("pw-100")))
        assertNull(store.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.plannedWorkoutReportKey("pw-1")))
    }

    @Test
    fun metrics_lruKeeps50() {
        val store = InMemoryMovitLocalStore()
        repeat(60) { i ->
            store.writeJsonCacheAt(
                MovitCacheKeys.REPORTS_STORE,
                "reports_metrics_scope_p_$i",
                "{}",
                now - (60 - i) * 1000L,
            )
        }
        val maintenance = JsonCacheMaintenance(store) { now }
        assertEquals(10, maintenance.purgeMetricsLru())
        val remaining = store.listJsonCacheEntries(MovitCacheKeys.REPORTS_STORE)
            .keys.count { it.startsWith("reports_metrics_") }
        assertEquals(50, remaining)
    }

    @Test
    fun effectivePlans_ttl21Days() {
        val store = InMemoryMovitLocalStore()
        store.writeJsonCacheAt(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
            "{}",
            now - 22 * dayMs,
        )
        store.writeJsonCacheAt(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey("up-1", 1, 2),
            "{}",
            now - 5 * dayMs,
        )
        val maintenance = JsonCacheMaintenance(store) { now }
        assertEquals(1, maintenance.purgeStaleEffectivePlans())
        assertNull(store.readJsonCache(MovitCacheKeys.SESSION_STORE, MovitCacheKeys.effectivePlanKey("up-1", 1, 1)))
        assertNotNull(store.readJsonCache(MovitCacheKeys.SESSION_STORE, MovitCacheKeys.effectivePlanKey("up-1", 1, 2)))
    }

    @Test
    fun effectivePlans_outsideActiveWeekWindow_removed() {
        val store = InMemoryMovitLocalStore()
        store.writeJsonCacheAt(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
            "{}",
            now - 2 * dayMs, // fresh by TTL but week 1 vs active 5
        )
        store.writeJsonCacheAt(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey("up-1", 5, 1),
            "{}",
            now - 2 * dayMs,
        )
        store.writeJsonCacheAt(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey("up-1", 6, 1),
            "{}",
            now - 2 * dayMs,
        )
        val maintenance = JsonCacheMaintenance(store) { now }
        assertEquals(1, maintenance.purgeStaleEffectivePlans(activeWeekNumber = 5))
        assertNull(store.readJsonCache(MovitCacheKeys.SESSION_STORE, MovitCacheKeys.effectivePlanKey("up-1", 1, 1)))
        assertNotNull(store.readJsonCache(MovitCacheKeys.SESSION_STORE, MovitCacheKeys.effectivePlanKey("up-1", 5, 1)))
        assertNotNull(store.readJsonCache(MovitCacheKeys.SESSION_STORE, MovitCacheKeys.effectivePlanKey("up-1", 6, 1)))
    }

    @Test
    fun effectivePlans_offsideWeekProtectedByOfflinePack_kept() {
        val store = InMemoryMovitLocalStore()
        val platform = FakeMovitPlatformBindings()
        platform.writeCache(
            WeekOfflinePackPrefetcher.OFFLINE_STORE,
            WeekOfflinePackPrefetcher.offlineReadyKey("prog-1", weekNumber = 1),
            "1",
        )
        store.writeJsonCacheAt(
            MovitCacheKeys.SESSION_STORE,
            MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
            "{}",
            now - 22 * dayMs, // expired by TTL
        )
        val maintenance = JsonCacheMaintenance(store, platform = { platform }) { now }
        assertEquals(0, maintenance.purgeStaleEffectivePlans(activeWeekNumber = 5))
        assertNotNull(store.readJsonCache(MovitCacheKeys.SESSION_STORE, MovitCacheKeys.effectivePlanKey("up-1", 1, 1)))
    }
}
