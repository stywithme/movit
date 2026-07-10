package com.movit.core.data.repository

import com.movit.core.network.MovitClock
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutReportExportDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsDashboardPatchTest {

    @Test
    fun patchDashboard_countsUniqueTrainingDaysNotDuplicateSameDay() {
        val localStore = testLocalStore()
        val repo = testReportsRepo(localStore)
        val dayOneMs = 1_704_067_200_000L
        val dayTwoMs = dayOneMs + 86_400_000L
        MovitClock.nowEpochMs = { dayTwoMs + 3_600_000L }

        repo.seedReport("pw-a", dayOneMs)
        repo.seedReport("pw-b", dayOneMs + 3_600_000L)
        repo.recordPendingPlannedWorkoutCompletion(
            workoutId = "pw-c",
            request = PlannedWorkoutCompleteRequestDto(completedAt = dayTwoMs, totalReps = 5),
            programId = "prog-1",
        )

        val dashboard = repo.readCachedDashboard()
        assertEquals(2, dashboard?.summary?.daysTrained)
        assertEquals(2, dashboard?.summary?.currentStreak)
    }

    @Test
    fun computeCurrentStreak_returnsZeroWhenLastTrainingOlderThanYesterday() {
        val today = 20_000L
        MovitClock.nowEpochMs = { today * 86_400_000L }
        val days = setOf(today - 3)

        val streak = ReportsSyncRepository.computeCurrentStreakFromUtcDays(days)

        assertEquals(0, streak)
    }

    private fun testReportsRepo(localStore: com.movit.core.data.local.MovitLocalStore): ReportsSyncRepository {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val engine = MockEngine {
            respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val platform = FakeMovitPlatformBindings()
        return ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })
    }

    private fun ReportsSyncRepository.seedReport(workoutId: String, completedAtMs: Long) {
        recordPendingPlannedWorkoutCompletion(
            workoutId = workoutId,
            request = PlannedWorkoutCompleteRequestDto(completedAt = completedAtMs, totalReps = 1),
            programId = "prog-1",
        )
    }
}
