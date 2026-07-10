package com.movit.core.data.outbox

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
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

class OutboxDependencyOrderingTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val planOkBody = """{"success":true}"""
    private val executionOkBody = """{"success":true,"data":{"id":"exec-1"}}"""

    @Test
    fun complete_deferredWhileSameGroupExecutionPending() = runBlocking {
        val callOrder = mutableListOf<String>()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("workout-executions") -> {
                    callOrder += "execution"
                    respond(executionOkBody, HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath.contains("/complete") -> {
                    callOrder += "complete"
                    respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        val base = MovitClock.nowEpochMs()
        val groupId = "grp-dep-1"

        localStore.insertOutbox(
            OutboxEntry(
                id = "exec-dep-1",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = MovitJson.encodeToString(
                    WorkoutExecutionUploadOutboxPayload(sampleExecution("exec-dep-1", groupId)),
                ),
                createdAt = base,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-complete-dep",
                type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                payload = MovitJson.encodeToString(
                    PlannedWorkoutCompleteOutboxPayload(
                        workoutId = "pw-1",
                        request = PlannedWorkoutCompleteRequestDto(),
                        workoutGroupId = groupId,
                    ),
                ),
                createdAt = base + 1,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )

        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })
        queue.replayPending(OutboxReplayAcquisition.Wait)

        assertEquals(listOf("execution", "complete"), callOrder)
        assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("op-complete-dep")?.status)
    }

    @Test
    fun complete_staysPendingWhenExecutionFailsNetwork() = runBlocking {
        val completeCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("workout-executions") ->
                    respond("down", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                request.url.encodedPath.contains("/complete") -> {
                    completeCalls.incrementAndGet()
                    respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        val base = MovitClock.nowEpochMs()
        val groupId = "grp-dep-block"

        localStore.insertOutbox(
            OutboxEntry(
                id = "exec-block",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = MovitJson.encodeToString(
                    WorkoutExecutionUploadOutboxPayload(sampleExecution("exec-block", groupId)),
                ),
                createdAt = base,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-complete-block",
                type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                payload = MovitJson.encodeToString(
                    PlannedWorkoutCompleteOutboxPayload(
                        workoutId = "pw-1",
                        request = PlannedWorkoutCompleteRequestDto(),
                        workoutGroupId = groupId,
                    ),
                ),
                createdAt = base + 1,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )

        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })
        queue.replayPending(OutboxReplayAcquisition.Wait)

        assertEquals(0, completeCalls.get())
        assertEquals(OutboxStatus.PENDING, localStore.getOutboxById("op-complete-block")?.status)
        assertEquals(OutboxStatus.PENDING, localStore.getOutboxById("exec-block")?.status)
    }

    @Test
    fun legacyNullGroup_defersOlderPendingExecution() = runBlocking {
        val completeCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("workout-executions") ->
                    respond("down", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                request.url.encodedPath.contains("/complete") -> {
                    completeCalls.incrementAndGet()
                    respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        val base = MovitClock.nowEpochMs()

        localStore.insertOutbox(
            OutboxEntry(
                id = "exec-legacy",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = MovitJson.encodeToString(
                    WorkoutExecutionUploadOutboxPayload(sampleExecution("exec-legacy", null)),
                ),
                createdAt = base,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-complete-legacy",
                type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                payload = MovitJson.encodeToString(
                    PlannedWorkoutCompleteOutboxPayload(
                        workoutId = "pw-1",
                        request = PlannedWorkoutCompleteRequestDto(),
                        workoutGroupId = null,
                    ),
                ),
                createdAt = base + 10,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )

        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })
        queue.replayPending(OutboxReplayAcquisition.Wait)

        assertEquals(0, completeCalls.get())
        assertEquals(OutboxStatus.PENDING, localStore.getOutboxById("op-complete-legacy")?.status)
    }

    @Test
    fun complete_proceedsWithWarningWhenExecutionFailedPermanent() = runBlocking {
        val callOrder = mutableListOf<String>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/complete")) {
                callOrder += "complete"
                respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(planOkBody, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = true
        }
        val localStore = InMemoryMovitLocalStore()
        val groupId = "grp-warn"
        val base = MovitClock.nowEpochMs()
        localStore.insertOutbox(
            OutboxEntry(
                id = "exec-dead",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = MovitJson.encodeToString(
                    WorkoutExecutionUploadOutboxPayload(sampleExecution("exec-dead", groupId)),
                ),
                createdAt = base,
                attempts = 3,
                status = OutboxStatus.FAILED_PERMANENT,
                ownerUserId = platform.userId(),
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-complete-warn",
                type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                payload = MovitJson.encodeToString(
                    PlannedWorkoutCompleteOutboxPayload(
                        workoutId = "pw-1",
                        request = PlannedWorkoutCompleteRequestDto(),
                        workoutGroupId = groupId,
                    ),
                ),
                createdAt = base + 1,
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = platform.userId(),
            ),
        )

        val queue = OfflineWriteQueue(localStore, testMobileApi(engine, platform), { platform })
        queue.replayPending(OutboxReplayAcquisition.Wait)

        assertEquals(listOf("complete"), callOrder)
        assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("op-complete-warn")?.status)
        assertEquals(
            DeferDecision.ProceedWithWarning,
            OutboxDependencyGate.shouldDeferComplete(
                OutboxEntry(
                    id = "probe",
                    type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                    payload = MovitJson.encodeToString(
                        PlannedWorkoutCompleteOutboxPayload(
                            workoutId = "pw-1",
                            request = PlannedWorkoutCompleteRequestDto(),
                            workoutGroupId = groupId,
                        ),
                    ),
                    createdAt = base + 1,
                    attempts = 0,
                    status = OutboxStatus.PENDING,
                ),
                localStore.listAllOutbox(),
            ),
        )
    }

    private fun sampleExecution(
        id: String,
        workoutGroupId: String?,
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
        workoutGroupId = workoutGroupId,
    )
}
