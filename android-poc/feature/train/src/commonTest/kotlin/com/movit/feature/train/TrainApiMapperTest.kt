package com.movit.feature.train

import com.movit.resources.strings.TrainStrings
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.HomeStatsDto
import com.movit.core.network.dto.TrainActiveProgramDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto
import com.movit.core.network.dto.RecentWorkoutDto
import com.movit.core.network.dto.WeekProgressDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
class TrainApiMapperTest {

    @Test
    fun mapsActivePlanWithLaunchTarget() {
        runBlocking {
            val strings = TrainStrings.load("en")
            val data = HomeDataDto(
                trainMode = TrainModeDto(
                    status = "active",
                    activeProgram = TrainActiveProgramDto(
                        id = "prog-1",
                        name = mapOf("en" to "Full Body Plan"),
                        weekNumber = 2,
                        dayNumber = 3,
                        totalWeeks = 4,
                        weekProgress = WeekProgressDto(completed = 2, total = 5),
                    ),
                    todayWorkout = TrainTodayWorkoutDto(
                        plannedWorkoutId = "pw-1",
                        name = mapOf("en" to "Lower Body"),
                        exerciseCount = 5,
                        estimatedMinutes = 22,
                        isCompleted = false,
                    ),
                ),
                stats = HomeStatsDto(streak = 3, avgFormScore = 82),
            )

            val dashboard = TrainApiMapper.map(data, language = "en", strings = strings)

            assertEquals(TrainDashboardStatus.ActivePlan, dashboard.status)
            assertEquals("Full Body Plan", dashboard.program?.name)
            val session = dashboard.today?.sessions?.single()
            assertNotNull(session?.launchTarget)
            assertEquals("prog-1", session.launchTarget.programId)
            assertEquals("pw-1", session.launchTarget.plannedWorkoutId)
        }
    }

    @Test
    fun mapsCompletedTodayStatus() {
        runBlocking {
            val strings = TrainStrings.load("en")
            val data = HomeDataDto(
                trainMode = TrainModeDto(
                    status = "active",
                    todayWorkout = TrainTodayWorkoutDto(isCompleted = true),
                ),
            )

            val dashboard = TrainApiMapper.map(data, language = "en", strings = strings)

            assertEquals(TrainDashboardStatus.CompletedToday, dashboard.status)
            assertEquals(strings.viewReport, dashboard.today?.primaryActionLabel)
        }
    }

    @Test
    fun mapsWeekOptionsForActiveProgram() {
        runBlocking {
            val strings = TrainStrings.load("en")
            val data = HomeDataDto(
                trainMode = TrainModeDto(
                    status = "active",
                    activeProgram = TrainActiveProgramDto(
                        id = "prog-1",
                        name = mapOf("en" to "Full Body Plan"),
                        weekNumber = 2,
                        dayNumber = 3,
                        totalWeeks = 4,
                        weekProgress = WeekProgressDto(completed = 2, total = 5),
                    ),
                ),
            )

            val dashboard = TrainApiMapper.map(data, language = "en", strings = strings)

            assertEquals(4, dashboard.weekOptions.size)
            assertEquals("Week 2", dashboard.weekOptions[1].title)
        }
    }

    @Test
    fun mapsReportTrendDelta() {
        runBlocking {
            val strings = TrainStrings.load("en")
            val data = HomeDataDto(
                trainMode = TrainModeDto(status = "active"),
                stats = HomeStatsDto(avgFormScore = 85, streak = 2),
                recentWorkouts = listOf(
                    RecentWorkoutDto(
                        formScore = 80,
                        totalReps = 120,
                        exerciseName = mapOf("en" to "Squat"),
                    ),
                ),
            )

            val dashboard = TrainApiMapper.map(data, language = "en", strings = strings)

            assertNotNull(dashboard.report?.trendDeltaPercent)
            assertTrue(dashboard.report?.trendChartPoints?.isNotEmpty() == true)
        }
    }

    @Test
    fun mapsNoPlanStatus() {
        runBlocking {
            val strings = TrainStrings.load("en")
            val data = HomeDataDto(
                trainMode = TrainModeDto(status = "no_assessment"),
            )

            val dashboard = TrainApiMapper.map(data, language = "en", strings = strings)

            assertEquals(TrainDashboardStatus.NoPlan, dashboard.status)
            assertEquals(strings.explorePrograms, dashboard.today?.primaryActionLabel)
        }
    }
}
