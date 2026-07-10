package com.movit.core.data

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionExpiryPreservesUnconfirmedReportsTest {

    private val localStore = InMemoryMovitLocalStore()

    @AfterTest
    fun tearDown() {
        MovitData.onSessionExpired = null
    }

    @Test
    fun sessionExpiry_preservesUnconfirmedReportsAndJournal() {
        runBlocking {
            MovitData.install(
                platform = FakeMovitPlatformBindings(),
                localStoreFactory = MovitLocalStoreFactory { localStore },
            )
            val reportKey = MovitCacheKeys.postTrainingReportKey("report-pending")
            val frameIndexKey = MovitCacheKeys.reportSessionExerciseKey("report-pending")
            localStore.writeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                reportKey,
                """{"id":"report-pending","workoutId":"report-pending"}""",
            )
            localStore.writeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                frameIndexKey,
                """{"sessionExerciseKey":"session-ex-1"}""",
            )
            localStore.writeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.exerciseSetReportsIndexKey("session-ex-1"),
                """{"reportsBySet":{"1":"report-pending"}}""",
            )
            localStore.upsertSessionJournal(
                sessionId = "sess-1",
                exerciseId = "ex-1",
                payloadJson = """{"reps":3}""",
                status = "active",
            )
            localStore.writeJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA, """{"home":true}""")
            localStore.writeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.REPORTS_DASHBOARD,
                """{"dashboard":true}""",
            )

            MovitData.clearReadCaches()

            assertNull(localStore.readJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA))
            assertNull(localStore.readJsonCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.REPORTS_DASHBOARD))
            assertNotNull(localStore.readJsonCache(MovitCacheKeys.REPORTS_STORE, reportKey))
            assertNotNull(localStore.readJsonCache(MovitCacheKeys.REPORTS_STORE, frameIndexKey))
            assertTrue(localStore.listActiveSessionJournals().any { it.sessionId == "sess-1" })
            assertEquals(1, localStore.listActiveSessionJournals().size)
            Unit
        }
    }
}
