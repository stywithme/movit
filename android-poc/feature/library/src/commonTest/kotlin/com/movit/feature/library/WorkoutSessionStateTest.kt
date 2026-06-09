package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkoutSessionStateTest {

    @Test
    fun sessionKey_roundTrips() {
        val encoded = WorkoutSessionKeys.encode(
            programId = "prog-123",
            weekNumber = 2,
            dayNumber = 3,
            plannedWorkoutId = "workout-abc",
        )
        val parsed = WorkoutSessionKeys.parse(encoded)
        assertNotNull(parsed)
        assertEquals("prog-123", parsed.programId)
        assertEquals(2, parsed.weekNumber)
        assertEquals(3, parsed.dayNumber)
        assertEquals("workout-abc", parsed.plannedWorkoutId)
    }

    @Test
    fun preview_loadsSections() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val session = viewModel.state.value.session
        assertNotNull(session)
        assertTrue(session.sections.isNotEmpty())
        assertEquals(6, session.exerciseCount)
    }

    @Test
    fun editDetails_updatesExerciseMetrics() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.toggleEditMode()
        viewModel.openEditSheet("ex-barbell-squat")
        viewModel.updateEditDraft { it.copy(sets = 4, reps = 8, weightKg = 50f, restSeconds = 90) }
        viewModel.saveEditDetails()

        val exercise = viewModel.state.value.session
            ?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
            ?.first { it.id == "ex-barbell-squat" }

        assertNotNull(exercise)
        assertEquals(4, exercise.sets)
        assertEquals(8, exercise.reps)
        assertEquals(50f, exercise.weightKg)
        assertEquals("4 × 8", exercise.setsLabel)
        assertEquals("50 kg", exercise.weightLabel)
        assertEquals("90s rest", exercise.restLabel)
    }
}
