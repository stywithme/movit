package com.movit.core.data.outbox

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineWriteQueueConcurrencyTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val planOkBody = """{"success":true}"""

    @Test
    fun parallelReplay_trySkip_doesNotDoubleDispatch() = runBlocking {
        val apiCalls = AtomicInteger(0)
        val engine = MockEngine {
            apiCalls.incrementAndGet()
            delay(80)
            respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        seedPending(localStore, "op-parallel-replay", platform.userId())
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        coroutineScope {
            val a = async { queue.replayPending(OutboxReplayAcquisition.TrySkipIfBusy) }
            val b = async { queue.replayPending(OutboxReplayAcquisition.TrySkipIfBusy) }
            a.await()
            b.await()
        }

        assertEquals(1, apiCalls.get())
        assertEquals(0L, queue.pendingCount())
    }

    @Test
    fun enqueueDuringReplay_doesNotResurrectSucceededRow() = runBlocking {
        val apiCalls = AtomicInteger(0)
        val engine = MockEngine {
            apiCalls.incrementAndGet()
            delay(100)
            respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        seedPending(localStore, "op-no-resurrect", platform.userId())
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        coroutineScope {
            val replay = async { queue.replayPending(OutboxReplayAcquisition.Wait) }
            delay(20)
            // Same operationId while first replay holds the mutex — must not resurrect SUCCEEDED.
            queue.enqueuePlanComplete("op-no-resurrect")
            replay.await()
        }

        assertEquals(1, apiCalls.get())
        assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("op-no-resurrect")?.status)

        queue.replayPending(OutboxReplayAcquisition.Wait)
        assertEquals(1, apiCalls.get())
    }

    @Test
    fun failedPermanent_mayBeReplacedOnEnqueue() = runBlocking {
        val apiCalls = AtomicInteger(0)
        val engine = MockEngine {
            apiCalls.incrementAndGet()
            respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-replace-failed",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = 1L,
                attempts = 3,
                status = OutboxStatus.FAILED_PERMANENT,
                ownerUserId = platform.userId(),
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        queue.enqueuePlanComplete("op-replace-failed")

        assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("op-replace-failed")?.status)
        assertEquals(1, apiCalls.get())
        assertTrue(localStore.getOutboxById("op-replace-failed")!!.attempts >= 0)
    }

    private suspend fun seedPending(
        localStore: InMemoryMovitLocalStore,
        id: String,
        ownerUserId: String?,
    ) {
        localStore.insertOutbox(
            OutboxEntry(
                id = id,
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = ownerUserId,
            ),
        )
    }
}
