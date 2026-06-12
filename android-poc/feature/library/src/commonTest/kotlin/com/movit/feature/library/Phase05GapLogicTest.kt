package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.ExerciseMetricsSummaryDto
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.ReportInsightDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.movit.resources.strings.SessionStrings
import kotlinx.coroutines.runBlocking

class Phase05GapLogicTest {

    @Test
    fun skipWarmup_setsFlagAndHidesWarmupFromTrainingSections() {
        val session = WorkoutSessionPreviewData.preview
        assertTrue(session.hasWarmupSection())
        val skipped = session.withoutWarmup()
        assertTrue(skipped.warmupSkipped)
        assertEquals(0, skipped.sectionsForTraining().count { it.phaseRole == "WARMUP" })
    }

    @Test
    fun catchUpResolver_showsPromptForMissedDay() {
        val prompt = SessionCatchUpResolver.resolve(
            weekNumber = 1,
            dayNumber = 2,
            catchUpMessage = "You missed training days",
            missedSlots = listOf(1 to 2, 1 to 3),
        )
        assertNotNull(prompt)
        assertEquals(1, prompt.missedWeekNumber)
        assertEquals(2, prompt.missedDayNumber)
    }

    @Test
    fun catchUpResolver_ignoresNonMissedDay() {
        val prompt = SessionCatchUpResolver.resolve(
            weekNumber = 2,
            dayNumber = 1,
            catchUpMessage = "Catch up",
            missedSlots = listOf(1 to 2),
        )
        assertNull(prompt)
    }

    @Test
    fun workoutFlowSaveEncoder_updatesSetsAndRest() {
        val plan = EffectivePlanPayloadDto(
            plannedWorkouts = listOf(
                EffectivePlannedWorkoutDto(
                    id = "pw-1",
                    items = listOf(
                        EffectivePlanItemDto(
                            id = "ex-barbell-squat",
                            type = "exercise",
                            sets = 3,
                            targetReps = 12,
                            restBetweenSetsMs = 60_000,
                        ),
                    ),
                ),
            ),
        )
        val config = WorkoutFlowConfigUi(
            workoutId = "session:prog:1:1:pw-1",
            title = "Leg day",
            subtitle = "Week 1",
            exercises = listOf(
                WorkoutFlowExerciseUi(
                    id = "ex-barbell-squat",
                    exerciseSlug = "barbell-squat",
                    name = "Squat",
                    sets = 4,
                    reps = 10,
                    durationSeconds = null,
                ),
            ),
            restBetweenSetsSeconds = 90,
        )
        val request = WorkoutFlowSaveEncoder.encodeDayUpdate(
            config = config,
            context = WorkoutSessionContextUi(
                programId = "prog",
                programSlug = "prog",
                weekNumber = 1,
                dayNumber = 1,
                plannedWorkoutId = "pw-1",
            ),
            plan = plan,
        )
        val item = request.customizations["day_1_1"]!!.single().items.single()
        assertEquals(4, item.sets)
        assertEquals(90_000, item.restBetweenSetsMs)
    }

    @Test
    fun workoutFlowSaveEncoder_reordersAndRemovesExercises() {
        val plan = EffectivePlanPayloadDto(
            plannedWorkouts = listOf(
                EffectivePlannedWorkoutDto(
                    id = "pw-1",
                    items = listOf(
                        EffectivePlanItemDto(
                            id = "ex-a",
                            type = "exercise",
                            sets = 3,
                            targetReps = 10,
                        ),
                        EffectivePlanItemDto(
                            id = "ex-b",
                            type = "exercise",
                            sets = 3,
                            targetReps = 12,
                        ),
                        EffectivePlanItemDto(
                            id = "rest-1",
                            type = "rest",
                            restDurationMs = 60_000,
                        ),
                    ),
                ),
            ),
        )
        val config = WorkoutFlowConfigUi(
            workoutId = "session:prog:1:1:pw-1",
            title = "Leg day",
            subtitle = "Week 1",
            exercises = listOf(
                WorkoutFlowExerciseUi(
                    id = "ex-b",
                    exerciseSlug = "lunge",
                    name = "Lunge",
                    sets = 4,
                    reps = 8,
                    durationSeconds = null,
                ),
            ),
            restBetweenSetsSeconds = 60,
        )
        val request = WorkoutFlowSaveEncoder.encodeDayUpdate(
            config = config,
            context = WorkoutSessionContextUi(
                programId = "prog",
                programSlug = "prog",
                weekNumber = 1,
                dayNumber = 1,
                plannedWorkoutId = "pw-1",
            ),
            plan = plan,
        )
        val items = request.customizations["day_1_1"]!!.single().items
        assertEquals(2, items.size)
        assertEquals("ex-b", items.first().id)
        assertEquals(4, items.first().sets)
        assertEquals("rest-1", items.last().id)
    }

    @Test
    fun workoutFormInsightLoader_mapsApiResponse() {
        val insight = WorkoutFormInsightLoader.mapResponse(
            MetricsApiResponse(
                success = true,
                summary = ExerciseMetricsSummaryDto(averageFormScore = 92.4f),
                insights = listOf(ReportInsightDto(type = "form_tip", message = "Keep depth consistent.")),
            ),
        )
        assertNotNull(insight)
        assertEquals(92, insight.formPercent)
        assertEquals("Keep depth consistent.", insight.tip)
    }

    @Test
    fun mapPlannedWorkoutCards_marksSelectedWorkout() {
        runBlocking {
            val plan = EffectivePlanPayloadDto(
                plannedWorkouts = listOf(
                    EffectivePlannedWorkoutDto(id = "a", sortOrder = 0, name = mapOf("en" to "AM")),
                    EffectivePlannedWorkoutDto(id = "b", sortOrder = 1, name = mapOf("en" to "PM")),
                ),
            )
            val cards = WorkoutSessionApiMapper.mapPlannedWorkoutCards(
                plan = plan,
                selectedPlannedWorkoutId = "b",
                language = "en",
                strings = SessionStrings.load("en"),
            )
            assertEquals(2, cards.size)
            assertFalse(cards.first { it.id == "a" }.isSelected)
            assertTrue(cards.first { it.id == "b" }.isSelected)
        }
    }
}
