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
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxBackoffSchedulingTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun nextAttemptDelay_stepsAndCaps() {
        val fixed = Random(0)
        assertEquals(0L, OutboxConflictPolicy.nextAttemptDelayMs(0, fixed))
        val d1 = OutboxConflictPolicy.nextAttemptDelayMs(1, fixed)
        assertTrue(d1 in 30_000L until 36_000L)
        val d2 = OutboxConflictPolicy.nextAttemptDelayMs(2, fixed)
        assertTrue(d2 in 120_000L until 144_000L)
        val d3 = OutboxConflictPolicy.nextAttemptDelayMs(3, fixed)
        assertTrue(d3 in 600_000L until 720_000L)
        val d4 = OutboxConflictPolicy.nextAttemptDelayMs(4, fixed)
        assertTrue(d4 in 1_800_000L until 2_160_000L)
        val d10 = OutboxConflictPolicy.nextAttemptDelayMs(10, fixed)
        assertTrue(d10 in 1_800_000L until 2_160_000L)
    }

    @Test
    fun replay_skipsUntilNextAttemptAt() = runBlocking {
        val apiCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val engine = MockEngine {
            apiCalls.incrementAndGet()
            respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        val future = MovitClock.nowEpochMs() + 60_000L
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-backoff-wait",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 1,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
                nextAttemptAtEpochMs = future,
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        val skipped = queue.replayPending(OutboxReplayAcquisition.Wait)
        assertEquals(0, skipped.attempted)
        assertEquals(1, skipped.skipped)
        assertEquals(0, apiCalls.get())
        assertEquals(OutboxStatus.PENDING, localStore.getOutboxById("op-backoff-wait")?.status)
    }

    @Test
    fun networkFailure_schedulesNextAttemptAt() = runBlocking {
        val engine = MockEngine { respond("down", HttpStatusCode.BadGateway, jsonHeaders) }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        val before = MovitClock.nowEpochMs()
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-schedule",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = before,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        queue.replayPending(OutboxReplayAcquisition.Wait)

        val entry = localStore.getOutboxById("op-schedule")!!
        assertEquals(OutboxStatus.PENDING, entry.status)
        assertEquals(1, entry.attempts)
        val nextAt = entry.nextAttemptAtEpochMs
        assertTrue(nextAt != null)
        assertTrue(nextAt!! >= before + 30_000L)
        assertTrue(nextAt <= before + 36_000L + 5_000L) // jitter + clock slack
    }

    @Test
    fun success_clearsNextAttemptAt() = runBlocking {
        val engine = MockEngine {
            respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-clear-backoff",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 2,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
                nextAttemptAtEpochMs = MovitClock.nowEpochMs() - 1,
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        queue.replayPending(OutboxReplayAcquisition.Wait)

        val entry = localStore.getOutboxById("op-clear-backoff")!!
        assertEquals(OutboxStatus.SUCCEEDED, entry.status)
        assertNull(entry.nextAttemptAtEpochMs)
    }
}
