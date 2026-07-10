package com.movit.core.data

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.outbox.GuestOutboxAttributionGate
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxReplayAcquisition
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.outbox.WorkoutExecutionUploadOutboxPayload
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestUploadAttributedAfterLoginTest {

    private val localStore = InMemoryMovitLocalStore()

    @AfterTest
    fun tearDown() {
        MovitData.onSessionExpired = null
    }

    @Test
    fun guestUpload_requiresUx7GateBeforeAttribution() = runBlocking {
        // Offline so accept's immediate replayPending does not need a live API.
        val platform = object : FakeMovitPlatformBindings(storedUserId = null, auth = "Bearer t") {
            override fun isNetworkAvailable(): Boolean = false
        }
        MovitData.install(
            platform = platform,
            localStoreFactory = MovitLocalStoreFactory { localStore },
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "guest-exec",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = """{"id":"guest-exec"}""",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = null,
            ),
        )

        platform.setUserId("user-new")
        val prompt = MovitData.onAuthenticatedSession("user-new")
        assertNotNull(prompt)
        assertEquals(1, prompt.guestRowCount)
        // Silent attribution must not happen — owner stays null until accept.
        assertNull(localStore.getOutboxById("guest-exec")?.ownerUserId)
        assertTrue(!MovitData.guestOutboxGate.isGuestReplayAccepted("user-new"))

        MovitData.acceptGuestOutboxAttribution("user-new")
        assertEquals("user-new", localStore.getOutboxById("guest-exec")?.ownerUserId)
        assertTrue(MovitData.guestOutboxGate.isGuestReplayAccepted("user-new"))
        assertNull(MovitData.pendingGuestOutboxPrompt())
    }

    @Test
    fun acceptThenReplay_uploadsAttributedGuestRow() = runBlocking {
        var uploadCalls = 0
        val platform = object : FakeMovitPlatformBindings(storedUserId = "user-new") {
            override fun isNetworkAvailable(): Boolean = true
        }
        val gate = GuestOutboxAttributionGate(localStore)
        val engine = MockEngine {
            uploadCalls++
            respond(
                content = """{"success":true,"data":{"id":"exec-1"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val queue = OfflineWriteQueue(
            localStore = localStore,
            api = testMobileApi(engine, platform),
            platform = { platform },
            guestGate = gate,
        )
        val request = WorkoutExecutionUploadRequestDto(
            id = "guest-exec-replay",
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
        localStore.insertOutbox(
            OutboxEntry(
                id = "guest-exec-replay",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = MovitJson.encodeToString(WorkoutExecutionUploadOutboxPayload(request)),
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = null,
            ),
        )
        // Same sequence MovitData.acceptGuestOutboxAttribution performs.
        gate.accept("user-new")
        queue.replayPending(OutboxReplayAcquisition.Wait)
        assertEquals(1, uploadCalls)
        assertEquals(OutboxStatus.SUCCEEDED, localStore.getOutboxById("guest-exec-replay")?.status)
    }

    @Test
    fun replayPending_withAuthButNullUserId_skipsWithoutUpload() = runBlocking {
        var uploadCalls = 0
        val platform = object : FakeMovitPlatformBindings(storedUserId = null, auth = "Bearer legacy") {
            override fun isNetworkAvailable(): Boolean = true
        }
        val engine = MockEngine {
            uploadCalls++
            respond("""{"success":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val queue = OfflineWriteQueue(
            localStore = localStore,
            api = testMobileApi(engine, platform),
            platform = { platform },
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "owned",
                type = OutboxOperationType.PLAN_COMPLETE,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = "someone",
            ),
        )
        val result = queue.replayPending(OutboxReplayAcquisition.Wait)
        assertEquals(0, result.attempted)
        assertEquals(0, uploadCalls)
    }

    @Test
    fun guestUpload_discardRemovesRows() = runBlocking {
        MovitData.install(
            platform = FakeMovitPlatformBindings(storedUserId = "user-new"),
            localStoreFactory = MovitLocalStoreFactory { localStore },
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "guest-exec-2",
                type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
                payload = "{}",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
                ownerUserId = null,
            ),
        )
        assertNotNull(MovitData.onAuthenticatedSession("user-new"))
        MovitData.discardGuestOutboxAttribution()
        assertNull(localStore.getOutboxById("guest-exec-2"))
        assertNull(MovitData.pendingGuestOutboxPrompt())
    }
}
