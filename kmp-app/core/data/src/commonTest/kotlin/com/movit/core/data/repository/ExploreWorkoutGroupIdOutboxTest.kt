package com.movit.core.data.repository

import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.outbox.WorkoutExecutionUploadOutboxPayload
import com.movit.core.network.MovitJson
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ExploreWorkoutGroupIdOutboxTest {

    @Test
    fun exploreWorkout_threeExecutions_shareWorkoutGroupIdInOutbox() = runBlocking {
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
            journalStore = com.movit.core.data.journal.SessionJournalStore(localStore),
        )

        val workoutGroupId = "wkg-explore-test-1"
        val workoutTemplateId = "template-squat-circuit"
        val uploads = listOf("exec-explore-1", "exec-explore-2", "exec-explore-3").map { id ->
            sampleUpload(id)
        }

        uploads.forEach { upload ->
            coordinator.uploadWorkoutExecution(
                upload = upload,
                context = "explore_workout",
                workoutGroupId = workoutGroupId,
                workoutTemplateId = workoutTemplateId,
            )
        }

        val pending = localStore.listPendingOutbox()
        assertEquals(3, pending.size)
        val groupIds = pending.map { entry ->
            val payload = MovitJson.decodeFromString<WorkoutExecutionUploadOutboxPayload>(entry.payload)
            payload.request.workoutGroupId
        }
        assertEquals(listOf(workoutGroupId, workoutGroupId, workoutGroupId), groupIds)
        assertTrue(pending.all { it.type == OutboxOperationType.WORKOUT_EXECUTION_UPLOAD })
        assertTrue(pending.all { it.status == OutboxStatus.PENDING })
    }

    private fun sampleUpload(id: String) = WorkoutUpload(
        id = id,
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
}
