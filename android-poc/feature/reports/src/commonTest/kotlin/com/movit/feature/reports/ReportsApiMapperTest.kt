package com.movit.feature.reports

import com.movit.resources.strings.ReportsStrings
import com.movit.core.network.dto.ReportDashboardExerciseDto
import com.movit.core.network.dto.ReportDashboardSummaryDto
import com.movit.core.network.dto.ReportDashboardTrendsDto
import com.movit.core.network.dto.ReportInsightDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
class ReportsApiMapperTest {

    @Test
    fun mapsDashboardWithKpisAndExercises() {
        runBlocking {
            val strings = ReportsStrings.load("en")
            val response = ReportsDashboardApiResponse(
                success = true,
                period = "all",
                summary = ReportDashboardSummaryDto(
                    daysTrained = 8,
                    totalReps = 420,
                    totalVolume = 1250f,
                    totalTrainingTime = 3_600_000L,
                    currentStreak = 5,
                    overallFormScore = 84f,
                ),
                trends = ReportDashboardTrendsDto(
                    formScoreByWeek = listOf(78f, 82f, 84f),
                    attendanceByWeek = listOf(3, 4, 5),
                    volumeByWeek = listOf(300f, 420f, 530f),
                ),
                exerciseBreakdown = listOf(
                    ReportDashboardExerciseDto(
                        exerciseSlug = "squat",
                        exerciseName = "Squat",
                        averageFormScore = 88f,
                        workoutsCount = 4,
                    ),
                ),
                insights = listOf(
                    ReportInsightDto(type = "form_trend", message = "Form is improving."),
                ),
            )

            val ui = ReportsApiMapper.map(response, strings)

            assertEquals(ReportsHubState.Success, ui.hubState)
            assertEquals(4, ui.kpis.size)
            assertEquals("420", ui.kpis[1].value)
            assertEquals(1, ui.exercises.size)
            assertEquals("squat", ui.exercises.first().id)
            assertEquals(3, ui.formScorePoints.size)
            assertEquals(7.69f, ui.improvementRatePercent!!, 0.1f)
        }
    }

    @Test
    fun computeImprovementRate_returnsNullForSinglePoint() {
        assertEquals(null, ReportsApiMapper.computeImprovementRate(listOf(80f)))
    }

    @Test
    fun computeImprovementRate_calculatesDelta() {
        val rate = ReportsApiMapper.computeImprovementRate(listOf(80f, 88f))
        assertEquals(10f, rate!!, 0.01f)
    }

    @Test
    fun emptyDashboardMapsToEmptyState() {
        runBlocking {
            val strings = ReportsStrings.load("en")
            val response = ReportsDashboardApiResponse(
                success = true,
                summary = ReportDashboardSummaryDto(),
            )

            val ui = ReportsApiMapper.map(response, strings)

            assertEquals(ReportsHubState.Empty, ui.hubState)
        }
    }

    @Test
    fun hasTrainingDataDetectsExerciseBreakdown() {
        val response = ReportsDashboardApiResponse(
            success = true,
            summary = ReportDashboardSummaryDto(),
            exerciseBreakdown = listOf(
                ReportDashboardExerciseDto(exerciseSlug = "lunge"),
            ),
        )

        assertTrue(ReportsApiMapper.hasTrainingData(response))
    }
}
