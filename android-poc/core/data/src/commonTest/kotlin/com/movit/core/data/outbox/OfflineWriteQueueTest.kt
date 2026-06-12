package com.movit.core.data.outbox

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.ProgramCustomizationKeys
import com.movit.core.network.dto.ProgressionMarkSeenRequest
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.MovitClock
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
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
    private val executionOkBody = """{"success":true,"data":{"id":"exec-offline-1"}}"""

    @Test
    fun enqueueSaveDayCustomizations_appliesOptimisticLocalCache() {
        runBlocking {
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val localStore = InMemoryMovitLocalStore()
            val queue = OfflineWriteQueue(localStore, testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform)) { platform }

            queue.enqueueSaveDayCustomizations(
                userProgramId = "up-1",
                weekNumber = 1,
                dayNumber = 1,
                request = UserProgramUpdateRequest(
                    customizations = mapOf(
                        ProgramCustomizationKeys.dayKey(1, 1) to listOf(
                            EffectivePlannedWorkoutDto(id = "pw-optimistic"),
                        ),
                    ),
                ),
            )

            val effective = DayCustomizationLocalStore(localStore).getEffectivePlannedWorkouts(
                userProgramId = "up-1",
                weekNumber = 1,
                dayNumber = 1,
                originalWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-server")),
            )
            assertEquals("pw-optimistic", effective.single().id)
            assertEquals(1L, queue.pendingCount())
        }
    }

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
                    createdAt = MovitClock.nowEpochMs(),
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
    fun workoutExecutionUpload_offline_replaysOnceWhenOnline() {
        runBlocking {
            val apiCalls = AtomicInteger(0)
            val engine = MockEngine {
                apiCalls.incrementAndGet()
                respond(executionOkBody, HttpStatusCode.OK, jsonHeaders)
            }
            val platform = object : FakeMovitPlatformBindings() {
                private var online = false
                override fun isNetworkAvailable(): Boolean = online
                fun goOnline() {
                    online = true
                }
            }
            val queue = OfflineWriteQueue(InMemoryMovitLocalStore(), testMobileApi(engine, platform)) { platform }

            queue.enqueueWorkoutExecutionUpload(sampleWorkoutExecutionUpload())

            assertEquals(0, apiCalls.get())
            assertEquals(1L, queue.pendingCount())

            platform.goOnline()
            val replay = queue.replayPending()
            assertEquals(1, replay.succeeded)
            assertEquals(1, apiCalls.get())
            assertEquals(0L, queue.pendingCount())
        }
    }

    @Test
    fun progressionMarkSeen_offline_replaysOnceWhenOnline() {
        runBlocking {
            val apiCalls = AtomicInteger(0)
            val engine = MockEngine {
                apiCalls.incrementAndGet()
                respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
            }
            val platform = object : FakeMovitPlatformBindings() {
                private var online = false
                override fun isNetworkAvailable(): Boolean = online
                fun goOnline() {
                    online = true
                }
            }
            val queue = OfflineWriteQueue(InMemoryMovitLocalStore(), testMobileApi(engine, platform)) { platform }

            queue.enqueueProgressionMarkSeen(ProgressionMarkSeenRequest(ids = listOf("chg-1")))
            assertEquals(0, apiCalls.get())

            platform.goOnline()
            val replay = queue.replayPending()
            assertEquals(1, replay.succeeded)
            assertEquals(1, apiCalls.get())
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

    @Test
    fun replayPending_purgesSucceededRowsOlderThanRetention() {
        runBlocking {
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = true
            }
            val localStore = InMemoryMovitLocalStore()
            val queue = OfflineWriteQueue(
                localStore,
                testMobileApi(MockEngine { respond(planOkBody, HttpStatusCode.OK, jsonHeaders) }, platform),
            ) { platform }

            val staleCreatedAt = MovitClock.nowEpochMs() - (8L * 24 * 60 * 60 * 1000)
            localStore.insertOutbox(
                OutboxEntry(
                    id = "op-stale",
                    type = OutboxOperationType.PLAN_COMPLETE,
                    payload = "{}",
                    createdAt = staleCreatedAt,
                    attempts = 1,
                    status = OutboxStatus.SUCCEEDED,
                ),
            )
            localStore.insertOutbox(
                OutboxEntry(
                    id = "op-fresh",
                    type = OutboxOperationType.PLAN_COMPLETE,
                    payload = "{}",
                    createdAt = MovitClock.nowEpochMs(),
                    attempts = 1,
                    status = OutboxStatus.SUCCEEDED,
                ),
            )

            queue.replayPending()

            assertEquals(null, localStore.getOutboxById("op-stale"))
            assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("op-fresh")?.status)
        }
    }

    private fun sampleWorkoutExecutionUpload(
        id: String = "exec-offline-1",
    ): WorkoutExecutionUploadRequestDto = WorkoutExecutionUploadRequestDto(
        id = id,
        exerciseId = "bodyweight-squat",
        timestamp = 1_700_000_000_000L,
        durationMs = 84_000,
        totalReps = 10,
        countedReps = 9,
        invalidReps = 1,
        executionMetrics = ExecutionMetricsDto(
            avgRom = 9.2f,
            avgStability = 7.5f,
            avgFormScore = 8.4f,
            avgAlignmentAccuracy = 8.8f,
            totalTUT = 8400,
        ),
    )
}
