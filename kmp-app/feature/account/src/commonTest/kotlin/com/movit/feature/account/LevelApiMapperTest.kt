package com.movit.feature.account

import com.movit.core.network.dto.ActivePlanDto
import com.movit.core.network.dto.ActivePlanProgramDto
import com.movit.core.network.dto.DomainLevelDto
import com.movit.core.network.dto.LevelInfoDetailDto
import com.movit.core.network.dto.LevelProfileDetailDto
import com.movit.core.network.dto.LimitingFactorDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.RegionLevelDto
import com.movit.core.network.dto.PlanProgramInfoDto
import com.movit.core.network.dto.PlanProgressDto
import com.movit.core.network.dto.ReassessmentDto
import com.movit.resources.strings.LevelStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelApiMapperTest {

    @Test
    fun mapsLevelProfileDomainsAndProgress() = runBlocking {
        val dto = LevelProfileDetailDto(
            overallLevel = 2,
            bodyScore = 65.0,
            domainLevels = listOf(
                DomainLevelDto(domain = "mobility", score = 78.0),
                DomainLevelDto(domain = "strength", score = 58.0),
            ),
            levelInfo = LevelInfoDetailDto(
                code = "building",
                name = LocalizedNameDto(en = "Building", ar = "بناء"),
            ),
        )
        val strings = LevelStrings.load("en")
        val ui = LevelApiMapper.map(dto, plan = null, reassessments = emptyList(), strings)

        assertEquals(2, ui.levelNumber)
        assertEquals("Building", ui.levelName)
        assertEquals(65, ui.bodyScore)
        assertEquals(2, ui.domains.size)
        assertEquals("Mobility", ui.domains.first().name)
        assertEquals(78, ui.domains.first().score)
        assertTrue(ui.planPhases.isEmpty())
    }

    @Test
    fun mapsActivePlanProgramsToPhases() = runBlocking {
        val plan = ActivePlanDto(
            programs = listOf(
                ActivePlanProgramDto(
                    sortOrder = 0,
                    status = "completed",
                    program = PlanProgramInfoDto(
                        name = mapOf("en" to "Mobility Starter"),
                        durationWeeks = 4,
                        levelMin = com.movit.core.network.dto.PlanLevelDto(
                            code = "foundation",
                            name = mapOf("en" to "Foundation"),
                        ),
                    ),
                    progress = PlanProgressDto(completedDays = 28, totalDays = 28),
                ),
                ActivePlanProgramDto(
                    sortOrder = 1,
                    status = "active",
                    program = PlanProgramInfoDto(
                        name = mapOf("en" to "Full Body 4-Week"),
                        durationWeeks = 4,
                        levelMin = com.movit.core.network.dto.PlanLevelDto(
                            code = "building",
                            name = mapOf("en" to "Building"),
                        ),
                    ),
                    progress = PlanProgressDto(
                        completedDays = 6,
                        totalDays = 12,
                        currentWeek = 2,
                        currentDay = 3,
                    ),
                ),
                ActivePlanProgramDto(
                    sortOrder = 2,
                    status = "scheduled",
                    program = PlanProgramInfoDto(
                        levelMin = com.movit.core.network.dto.PlanLevelDto(
                            code = "strength",
                            name = mapOf("en" to "Strength"),
                        ),
                    ),
                ),
            ),
        )
        val strings = LevelStrings.load("en")
        val ui = LevelApiMapper.map(
            dto = LevelProfileDetailDto(overallLevel = 2, bodyScore = 65.0),
            plan = plan,
            reassessments = listOf(ReassessmentDto(scheduledDate = "2026-07-01")),
            strings = strings,
        )

        assertEquals(3, ui.planPhases.size)
        assertEquals(PlanPhaseStatus.Done, ui.planPhases[0].status)
        assertTrue(ui.planPhases[0].title.contains("Foundation"))
        assertTrue(ui.planPhases[0].title.contains("Completed"))
        assertEquals(PlanPhaseStatus.Active, ui.planPhases[1].status)
        assertEquals(50, ui.planPhases[1].progressPercent)
        assertEquals(PlanPhaseStatus.Upcoming, ui.planPhases[2].status)
    }

    @Test
    fun mapsRegionLevelsAndLimitingFactors() = runBlocking {
        val dto = LevelProfileDetailDto(
            overallLevel = 2,
            bodyScore = 65.0,
            regionLevels = listOf(
                RegionLevelDto(region = "hips", level = 3, score = 80.0, isLimiting = false),
                RegionLevelDto(region = "shoulders", level = 2, score = 58.0, isLimiting = true),
            ),
            limitingFactors = listOf(
                LimitingFactorDto(type = "region", code = "shoulders", currentLevel = 2, targetLevel = 3, gap = 1),
            ),
        )
        val strings = LevelStrings.load("en")
        val ui = LevelApiMapper.map(dto, plan = null, reassessments = emptyList(), strings)

        assertEquals(2, ui.regions.size)
        assertTrue(ui.regions[1].isLimiting)
        assertEquals(1, ui.limitingFactors.size)
        assertEquals(2, ui.limitingFactors.first().currentLevel)
    }

    @Test
    fun usesScheduledReassessmentLabel() = runBlocking {
        val strings = LevelStrings.load("en")
        val ui = LevelApiMapper.map(
            dto = LevelProfileDetailDto(overallLevel = 2, bodyScore = 65.0),
            plan = null,
            reassessments = listOf(ReassessmentDto(scheduledDate = "July 1")),
            strings = strings,
        )

        assertTrue(ui.reassessmentLabel.contains("July 1"))
    }
}
