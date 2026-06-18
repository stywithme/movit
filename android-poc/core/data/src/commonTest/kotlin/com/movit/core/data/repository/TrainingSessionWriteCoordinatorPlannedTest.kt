package com.movit.core.data.repository

import com.movit.core.data.journal.SessionJournalStore
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
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

    @Test
    fun plannedComplete_afterExecutionUpload_preservesOutboxReplayOrder() = runBlocking {
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

        val upload = WorkoutUpload(
            id = "exec-planned-order-1",
            exerciseId = "squat",
            timestamp = 1L,
            durationMs = 1_000,
            totalReps = 5,
            countedReps = 5,
            invalidReps = 0,
            weightKg = null,
            weightUnit = "kg",
            repMetrics = emptyList(),
            executionMetrics = WorkoutExecutionMetrics(
                avgRom = 0,
                avgSymmetry = null,
                avgStability = 0,
                avgTempo = emptyList(),
                avgVelocity = null,
                avgFormScore = 0,
                avgAlignmentAccuracy = 0,
                totalTUT = 0,
                totalVolume = null,
                maxWeight = null,
                est1RM = null,
                formConsistency = null,
                fatigueIndex = null,
                velocityLoss = null,
                tempoConsistency = null,
            ),
        )
        coordinator.uploadWorkoutExecution(upload)
        coordinator.completePlannedWorkout(
            workoutId = "pw-1",
            request = PlannedWorkoutCompleteRequestDto(),
            programId = "prog-1",
            weekNumber = 1,
            dayNumber = 1,
        )

        val pending = localStore.listPendingOutbox()
        assertEquals(2, pending.size)
        assertEquals(OutboxOperationType.WORKOUT_EXECUTION_UPLOAD, pending.first().type)
        assertEquals(OutboxOperationType.PLANNED_WORKOUT_COMPLETE, pending.last().type)
        assertEquals("exec-planned-order-1", pending.first().id)
    }
}
