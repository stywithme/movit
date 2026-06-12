package com.movit.core.data.sync

import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.ProgramExportDayDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.ProgramExportPlannedWorkoutDto
import com.movit.core.network.dto.ProgramExportWeekDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeekOfflinePackPrefetcherTest {

    @Test
    fun planFromProgram_collectsWorkoutDaysAndIds() {
        val program = ProgramExportDto(
            id = "prog-1",
            slug = "strength-101",
            coverImageUrl = "https://cdn.example/cover.jpg",
            weeks = listOf(
                ProgramExportWeekDto(
                    weekNumber = 1,
                    days = listOf(
                        ProgramExportDayDto(
                            dayNumber = 1,
                            plannedWorkouts = listOf(
                                ProgramExportPlannedWorkoutDto(
                                    id = "pw-1",
                                    name = LocalizedNameDto(en = "Day 1"),
                                ),
                            ),
                        ),
                        ProgramExportDayDto(dayNumber = 2, isRestDay = true),
                        ProgramExportDayDto(
                            dayNumber = 3,
                            plannedWorkouts = listOf(
                                ProgramExportPlannedWorkoutDto(
                                    id = "pw-2",
                                    name = LocalizedNameDto(en = "Day 3"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plan = WeekOfflinePackPrefetcher.planFromProgram(program, weekNumber = 1)
        assertNotNull(plan)
        assertEquals(listOf(1, 3), plan.workoutDayNumbers)
        assertEquals(listOf("pw-1", "pw-2"), plan.plannedWorkoutIds)
        assertEquals("https://cdn.example/cover.jpg", plan.coverImageUrl)
        assertNull(WeekOfflinePackPrefetcher.planFromProgram(program, weekNumber = 99))
    }
}
