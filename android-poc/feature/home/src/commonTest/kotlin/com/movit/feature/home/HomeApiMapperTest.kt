package com.movit.feature.home

import com.movit.core.network.dto.CatchUpSuggestionDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.HomeUserDto
import com.movit.core.network.dto.MissedSlotDto
import com.movit.core.network.dto.TrainActiveProgramDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto
import com.movit.core.network.dto.WeekProgressDto
import com.movit.resources.strings.HomeStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeApiMapperTest {

    @Test
    fun activeTrainMode_mapsWeeklyProgress() = runBlocking {
        val data = HomeDataDto(
            user = HomeUserDto(name = "Mahmoud", level = 2, bodyScore = 65.0, levelProgress = 62),
            trainMode = TrainModeDto(
                status = "active",
                activeProgram = TrainActiveProgramDto(
                    name = mapOf("en" to "Full Body"),
                    weekNumber = 2,
                    dayNumber = 3,
                    totalWeeks = 4,
                    weekProgress = WeekProgressDto(completed = 3, total = 5),
                ),
                todayWorkout = TrainTodayWorkoutDto(
                    name = mapOf("en" to "Upper Body"),
                    exerciseCount = 5,
                    estimatedMinutes = 38,
                ),
            ),
        )
        val dashboard = HomeApiMapper.map(data, "en", "Athlete", HomeStrings.load("en"))
        assertEquals(60, dashboard.progress.weeklyCompletionPercent)
        assertNotNull(dashboard.levelCard)
        assertEquals(62, dashboard.levelCard?.progressPercent)
        assertTrue(dashboard.journeyRows.isNotEmpty())
        assertEquals("timeline", dashboard.journeyRows.first().id)
    }

    @Test
    fun catchUpSuggestion_mapsCatchUpUi() = runBlocking {
        val data = HomeDataDto(
            trainMode = TrainModeDto(
                status = "active",
                activeProgram = TrainActiveProgramDto(
                    id = "prog-1",
                    name = mapOf("en" to "Full Body"),
                ),
                catchUpSuggestion = CatchUpSuggestionDto(
                    missedTrainingDays = 1,
                    message = "You missed yesterday",
                    missedSlots = listOf(MissedSlotDto(weekNumber = 2, dayNumber = 2)),
                ),
            ),
        )
        val dashboard = HomeApiMapper.map(data, "en", "Athlete", HomeStrings.load("en"))
        assertNotNull(dashboard.catchUp)
        assertEquals("You missed yesterday", dashboard.catchUp?.message)
        assertEquals("prog-1", dashboard.catchUp?.programId)
        assertEquals(2, dashboard.catchUp?.weekNumber)
        assertEquals(2, dashboard.catchUp?.dayNumber)
    }

    @Test
    fun reassessmentDue_mapsTodayPlanWithAssessmentAction() = runBlocking {
        val data = HomeDataDto(
            trainMode = TrainModeDto(status = "reassessment_due"),
        )
        val dashboard = HomeApiMapper.map(data, "en", "Athlete", HomeStrings.load("en"))
        assertNotNull(dashboard.todayPlan)
        assertTrue(dashboard.todayPlan?.opensAssessment == true)
    }
}
