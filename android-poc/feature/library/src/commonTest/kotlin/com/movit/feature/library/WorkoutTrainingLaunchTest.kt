package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorkoutTrainingLaunchTest {
    @Test
    fun toTrainingFlowItems_mapsRemainingExercisesFromIndex() {
        val config = WorkoutFlowConfigUi(
            workoutId = "session:test:1:1:pw-1",
            title = "Day 1",
            subtitle = "Legs",
            exercises = listOf(
                WorkoutFlowExerciseUi(
                    id = "ex-1",
                    exerciseSlug = "squat",
                    name = "Squat",
                    sets = 2,
                    reps = 10,
                    durationSeconds = null,
                ),
                WorkoutFlowExerciseUi(
                    id = "ex-2",
                    exerciseSlug = "lunge",
                    name = "Lunge",
                    sets = 1,
                    reps = 8,
                    durationSeconds = null,
                ),
            ),
            restBetweenSetsSeconds = 30,
        )
        val items = config.toTrainingFlowItems(startExerciseIndex = 1)
        assertEquals(1, items.size)
        val exercise = items.first() as com.movit.core.training.session.TrainingFlowItem.Exercise
        assertEquals("lunge", exercise.slug)
    }

    @Test
    fun resolvePlannedWorkoutLaunch_prefersSessionContext() {
        val context = WorkoutSessionContextUi(
            programId = "prog-1",
            programSlug = "prog",
            weekNumber = 2,
            dayNumber = 3,
            plannedWorkoutId = "pw-9",
        )
        val launch = resolvePlannedWorkoutLaunch("ignored", context)
        assertNotNull(launch)
        assertEquals("pw-9", launch.plannedWorkoutId)
        assertEquals("prog-1", launch.programId)
        assertEquals(2, launch.weekNumber)
        assertEquals(3, launch.dayNumber)
    }

    @Test
    fun resolvePlannedWorkoutLaunch_parsesEncodedWorkoutId() {
        val workoutId = WorkoutSessionKeys.encode(
            programId = "prog-2",
            weekNumber = 1,
            dayNumber = 4,
            plannedWorkoutId = "pw-2",
        )
        val launch = resolvePlannedWorkoutLaunch(workoutId, sessionContext = null)
        assertNotNull(launch)
        assertEquals("pw-2", launch.plannedWorkoutId)
        assertEquals("prog-2", launch.programId)
    }
}
