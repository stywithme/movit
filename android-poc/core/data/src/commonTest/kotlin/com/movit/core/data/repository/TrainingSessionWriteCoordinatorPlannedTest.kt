package com.movit.core.data.repository

import com.movit.core.data.journal.SessionJournalStore
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingSessionWriteCoordinatorPlannedTest {
    @Test
    fun startPlannedWorkout_offline_enqueuesStartOperation() = runBlocking {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val engine = MockEngine { respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders) }
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = false
        }
        val localStore = testLocalStore(platform)
        val coordinator = TrainingSessionWriteCoordinator(
            mobileWrites = testMobileWriteRepository(engine, platform, localStore),
            reportsSync = ReportsSyncRepository(
                api = testMobileApi(engine, platform),
                platform = { platform },
                localStore = { localStore },
            ),
            journalStore = SessionJournalStore(localStore),
        )

        val result = coordinator.startPlannedWorkout(
            workoutId = "pw-1",
            programId = "prog-1",
            weekNumber = 1,
            dayNumber = 2,
            startedAt = 1_000L,
        )

        assertTrue(result is AppResult.Success, "expected offline enqueue success, got $result")
        val operationId = (result as AppResult.Success).value
        val outbox = localStore.getOutboxById(operationId)
        assertEquals(OutboxOperationType.PLANNED_WORKOUT_START, outbox?.type)
    }
}
