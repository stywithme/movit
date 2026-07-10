package com.movit.core.data.outbox

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.data.sync.MovitSyncTelemetry
import com.movit.core.network.MovitClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineWriteQueueRetryExhaustionTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun http4xx_marksFailedPermanentImmediately() = runBlocking {
        val engine = MockEngine { respond("bad", HttpStatusCode.BadRequest, jsonHeaders) }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-4xx",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        val result = queue.replayPending(OutboxReplayAcquisition.Wait)

        assertEquals(1, result.failed)
        assertEquals(OutboxStatus.FAILED_PERMANENT, localStore.getOutboxById("op-4xx")?.status)
        assertEquals(1, MovitSyncTelemetry(localStore).readCounters().outboxFailedPermanent)
    }

    @Test
    fun networkRetry_hasNoAttemptCeiling() = runBlocking {
        val engine = MockEngine { respond("down", HttpStatusCode.ServiceUnavailable, jsonHeaders) }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-5xx-uncapped",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 40,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
                nextAttemptAtEpochMs = null,
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        queue.replayPending(OutboxReplayAcquisition.Wait)

        val entry = localStore.getOutboxById("op-5xx-uncapped")!!
        assertEquals(OutboxStatus.PENDING, entry.status)
        assertEquals(41, entry.attempts)
        assertTrue(entry.nextAttemptAtEpochMs != null && entry.nextAttemptAtEpochMs!! > MovitClock.nowEpochMs())
        assertEquals(0, MovitSyncTelemetry(localStore).readCounters().outboxRetryExhausted)
        assertFalse(
            OutboxConflictPolicy.shouldMarkPermanent(41, OutboxDispatchOutcome.RETRYABLE_NETWORK),
        )
    }

    @Test
    fun unexpectedRetry_exhaustsAtFifty() = runBlocking {
        // Invalid JSON → SerializationException → RETRYABLE_UNEXPECTED
        val engine = MockEngine { respond("not-json{{{", HttpStatusCode.OK, jsonHeaders) }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-unexpected-cap",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = OutboxConflictPolicy.UNEXPECTED_MAX_ATTEMPTS - 1,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )
        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })

        queue.replayPending(OutboxReplayAcquisition.Wait)

        val entry = localStore.getOutboxById("op-unexpected-cap")!!
        assertEquals(OutboxStatus.FAILED_PERMANENT, entry.status)
        assertEquals(OutboxConflictPolicy.UNEXPECTED_MAX_ATTEMPTS, entry.attempts)
        assertNull(entry.nextAttemptAtEpochMs)
        assertEquals(1, MovitSyncTelemetry(localStore).readCounters().outboxRetryExhausted)
    }
}
