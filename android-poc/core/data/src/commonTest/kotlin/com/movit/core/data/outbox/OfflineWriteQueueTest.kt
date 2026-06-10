package com.movit.core.data.outbox

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.dto.UserProgramUpdateRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicInteger

class OfflineWriteQueueTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val planOkBody = """{"success":true}"""

    @Test
    fun offlineWrite_replaysOnceWhenNetworkReturns() {
        runBlocking {
            val apiCalls = AtomicInteger(0)
            val engine = MockEngine {
                apiCalls.incrementAndGet()
                respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
            val platform = object : FakeMovitPlatformBindings() {
                private var online = false
                override fun isNetworkAvailable(): Boolean = online
                fun goOnline() {
                    online = true
                }
            }
            val api = testMobileApi(engine, platform)
            val localStore = InMemoryMovitLocalStore()
            val queue = OfflineWriteQueue(localStore, api) { platform }

            val operationId = "op-test-save-day"
            queue.enqueueSaveDayCustomizations(
                userProgramId = "up-1",
                weekNumber = 1,
                dayNumber = 1,
                request = UserProgramUpdateRequest(),
                operationId = operationId,
            )

            assertEquals(0, apiCalls.get())
            assertEquals(1L, queue.pendingCount())

            platform.goOnline()
            val firstReplay = queue.replayPending()
            assertEquals(1, firstReplay.attempted)
            assertEquals(1, firstReplay.succeeded)
            assertEquals(1, apiCalls.get())
            assertEquals(0L, queue.pendingCount())

            val secondReplay = queue.replayPending()
            assertEquals(0, secondReplay.attempted)
            assertEquals(1, apiCalls.get())
        }
    }

    @Test
    fun duplicateOperationId_isIdempotent() {
        runBlocking {
            val apiCalls = AtomicInteger(0)
            val engine = MockEngine {
                apiCalls.incrementAndGet()
                respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
            val platform = object : FakeMovitPlatformBindings() {
                var online = false
                override fun isNetworkAvailable(): Boolean = online
            }
            val queue = OfflineWriteQueue(InMemoryMovitLocalStore(), testMobileApi(engine, platform)) { platform }

            val operationId = "op-dedupe"
            queue.enqueuePlanComplete(operationId)
            queue.enqueuePlanComplete(operationId)

            platform.online = true
            queue.replayPending()
            assertEquals(1, apiCalls.get())
        }
    }

    @Test
    fun replayPending_whenOffline_doesNotCallApi() {
        runBlocking {
            val apiCalls = AtomicInteger(0)
            val engine = MockEngine {
                apiCalls.incrementAndGet()
                respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val queue = OfflineWriteQueue(InMemoryMovitLocalStore(), testMobileApi(engine, platform)) { platform }

            queue.enqueuePlanComplete("op-offline-gate")
            val result = queue.replayPending()

            assertEquals(0, result.attempted)
            assertEquals(0, apiCalls.get())
            assertEquals(1L, queue.pendingCount())
        }
    }

    @Test
    fun inFlightEntries_recoveredAndReplayedAfterCrash() {
        runBlocking {
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
                    id = "op-stuck-in-flight",
                    type = OutboxOperationType.PLAN_COMPLETE,
                    payload = "{}",
                    createdAt = 1L,
                    attempts = 0,
                    status = OutboxStatus.IN_FLIGHT,
                ),
            )
            val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform)) { platform }

            val result = queue.replayPending()

            assertEquals(1, result.attempted)
            assertEquals(1, result.succeeded)
            assertEquals(1, apiCalls.get())
            assertEquals(0L, queue.pendingCount())
            assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("op-stuck-in-flight")?.status)
        }
    }

    @Test
    fun serverConflict409_marksSucceeded_serverWins() {
        runBlocking {
            val engine = MockEngine { respond("conflict", HttpStatusCode.Conflict) }
            val platform = object : FakeMovitPlatformBindings() {
                var online = false
                override fun isNetworkAvailable(): Boolean = online
            }
            val localStore = InMemoryMovitLocalStore()
            val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform)) { platform }

            queue.enqueuePlanComplete("op-409")
            platform.online = true
            val result = queue.replayPending()

            assertEquals(1, result.succeeded)
            assertEquals(0L, queue.pendingCount())
            val entry = localStore.getOutboxById("op-409")
            assertTrue(entry?.status == OutboxStatus.SUCCEEDED)
        }
    }
}
