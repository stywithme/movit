package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExercisePrepareStateTest {

    @Test
    fun preview_loadsSquatWithInstructionsAndMuscles() {
        val viewModel = ExercisePrepareViewModel(exerciseId = "ex-squat")
        kotlinx.coroutines.runBlocking { viewModel.load() }

        val state = viewModel.state.value
        assertNotNull(state.exercise)
        assertEquals("Bodyweight Squat", state.exercise.name)
        assertEquals("bodyweight-squat", state.exercise.legacyFileName)
        assertEquals(3, state.exercise.instructions.size)
        assertEquals(3, state.exercise.targetMuscles.size)
        assertEquals(30, state.restSeconds)
    }

    @Test
    fun restMode_showsUpNextExercise() {
        val viewModel = ExercisePrepareViewModel(exerciseId = "ex-squat")
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.enterRestMode()

        val state = viewModel.state.value
        assertEquals(ExercisePrepareMode.Rest, state.mode)
        assertEquals("Leg Swings", state.displayExercise?.name)
        assertEquals(40, state.headerProgressPercent)
    }

    @Test
    fun restControls_updateStaticTimerAndReturnToPrepare() {
        val viewModel = ExercisePrepareViewModel(exerciseId = "ex-squat")
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.enterRestMode()
        viewModel.addRestTime()
        assertEquals(45, viewModel.state.value.restSeconds)
        viewModel.toggleRestPause()
        assertTrue(viewModel.state.value.isRestPaused)
        viewModel.skipRest()

        val state = viewModel.state.value
        assertEquals(ExercisePrepareMode.Prepare, state.mode)
        assertEquals(false, state.isRestPaused)
    }

    @Test
    fun legacySlug_mapsKnownExerciseIds() {
        assertEquals("bodyweight-squat", legacySlug("ex-squat"))
        assertEquals("bodyweight-squat", legacySlug("ex-squat-warm"))
        assertEquals("lunge", legacySlug("ex-lunge"))
    }

    @Test
    fun formatRestTimer_padsMinutesAndSeconds() {
        assertEquals("00:30", formatRestTimer(30))
        assertEquals("01:05", formatRestTimer(65))
        assertEquals("00:00", formatRestTimer(0))
    }
}
