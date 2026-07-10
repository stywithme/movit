package com.movit.core.data.repository

import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.outbox.PlannedWorkoutCompleteOutboxPayload
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutReportExportDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportsSyncRepositoryHydrateTest {

    @Test
    fun hydrateFromSync_skipsPendingPlannedWorkoutComplete() {
        val localStore = testLocalStore()
        val repo = testReportsRepo(localStore)
        repo.upsertLocalReportForTest(
            PlannedWorkoutReportExportDto(
                id = "local-op",
                plannedWorkoutId = "pw-1",
                completedAt = "2026-07-01T10:00:00.000Z",
                report = buildJsonObject { put("detail", JsonPrimitive("rich-local")) },
            ),
        )

        repo.hydrateFromSync(
            exports = listOf(
                PlannedWorkoutReportExportDto(
                    id = "server-id",
                    plannedWorkoutId = "pw-1",
                    completedAt = "2026-07-02T10:00:00.000Z",
                    report = null,
                ),
            ),
            pendingPlannedWorkoutIds = setOf("pw-1"),
        )

        val cached = repo.readCachedPlannedWorkoutReport("pw-1")
        assertNotNull(cached)
        assertEquals("pw-1", cached.id)
        assertTrue(ReportsSyncRepository.hasRichReport(cached.report))
    }

    @Test
    fun hydrateFromSync_keepsRichLocalReportWhenServerSummary() {
        val localStore = testLocalStore()
        val repo = testReportsRepo(localStore)
        val richReport = buildJsonObject {
            put("frames", JsonPrimitive(12))
            put("notes", JsonPrimitive("offline detail"))
        }
        repo.upsertLocalReportForTest(
            PlannedWorkoutReportExportDto(
                id = "local-op",
                plannedWorkoutId = "pw-2",
                completedAt = "2026-07-01T10:00:00.000Z",
                totalReps = 20,
                report = richReport,
            ),
        )

        repo.hydrateFromSync(
            exports = listOf(
                PlannedWorkoutReportExportDto(
                    id = "server-real-id",
                    plannedWorkoutId = "pw-2",
                    completedAt = "2026-07-02T12:00:00.000Z",
                    totalReps = 25,
                    avgFormScore = 0.91,
                    report = null,
                ),
            ),
        )

        val cached = repo.readCachedPlannedWorkoutReport("pw-2")
        assertNotNull(cached)
        assertEquals("server-real-id", cached.id)
        assertEquals("2026-07-02T12:00:00.000Z", cached.completedAt)
        assertEquals(25, cached.totalReps)
        assertEquals(richReport, cached.report)
    }

    @Test
    fun pendingPlannedWorkoutIdsFromOutbox_readsCompleteAndReportTypes() = kotlinx.coroutines.runBlocking {
        val localStore = testLocalStore()
        val payload = MovitJson.encodeToString(
            PlannedWorkoutCompleteOutboxPayload(
                workoutId = "pw-3",
                request = PlannedWorkoutCompleteRequestDto(),
            ),
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "op-complete",
                type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                payload = payload,
                createdAt = 1L,
                attempts = 0,
                status = OutboxStatus.IN_FLIGHT,
            ),
        )

        val ids = ReportsSyncRepository.pendingPlannedWorkoutIdsFromOutbox(localStore)

        assertEquals(setOf("pw-3"), ids)
    }

    @Test
    fun recordPendingPlannedWorkoutCompletion_writesIsoCompletedAt() {
        val localStore = testLocalStore()
        val repo = testReportsRepo(localStore)
        val epochMs = 1_704_067_200_000L

        repo.recordPendingPlannedWorkoutCompletion(
            workoutId = "pw-iso",
            request = PlannedWorkoutCompleteRequestDto(completedAt = epochMs, totalReps = 8),
            programId = "prog-1",
        )

        val cached = repo.readCachedPlannedWorkoutReport("pw-iso")
        assertNotNull(cached)
        assertTrue(cached.completedAt.contains('T'))
        assertTrue(cached.completedAt.endsWith('Z'))
        assertNull(cached.completedAt.toLongOrNull())
    }

    private fun testReportsRepo(localStore: com.movit.core.data.local.MovitLocalStore): ReportsSyncRepository {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val engine = MockEngine {
            respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val platform = FakeMovitPlatformBindings()
        return ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })
    }

    private fun ReportsSyncRepository.upsertLocalReportForTest(report: PlannedWorkoutReportExportDto) {
        recordPendingPlannedWorkoutCompletion(
            workoutId = report.plannedWorkoutId.ifBlank { report.id },
            request = PlannedWorkoutCompleteRequestDto(
                completedAt = DayCustomizationLocalStore.parseIsoToEpochMs(report.completedAt),
                totalReps = report.totalReps,
                report = report.report,
            ),
            programId = report.programId,
        )
    }
}
