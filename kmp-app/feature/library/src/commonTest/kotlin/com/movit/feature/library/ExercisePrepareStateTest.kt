package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExercisePrepareStateTest {

    @Test
    fun preview_loadsSquatWithInstructionsAndMuscles() {
        val viewModel = ExercisePrepareViewModel(
            exerciseId = "ex-squat",
            repository = FakeLibraryRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }

        val state = viewModel.state.value
        assertNotNull(state.exercise)
        assertEquals("Bodyweight Squat", state.exercise.name)
        assertEquals("squat", state.exercise.legacyFileName)
        assertEquals(1, state.exercise.instructions.size)
        assertEquals(2, state.exercise.targetMuscles.size)
        assertEquals(60, state.restSeconds)
    }

    @Test
    fun restMode_showsUpNextExercise() {
        WorkoutFlowCache.clearAll()
        WorkoutFlowCache.put(
            WorkoutFlowConfigUi(
                workoutId = "workout-rest",
                title = "Rest test",
                subtitle = "Session",
                exercises = listOf(
                    WorkoutFlowExerciseUi("ex-squat", "squat", "Bodyweight Squat", 3, 12, null, 30),
                    WorkoutFlowExerciseUi("ex-lunge", "lunge", "Reverse Lunge", 3, 10, null, 45),
                ),
            ),
        )
        val viewModel = ExercisePrepareViewModel(
            exerciseId = "ex-squat",
            workoutId = "workout-rest",
            upNextExerciseId = "ex-lunge",
        ).apply { enableRestTicker = false }
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.enterRestMode()

        val state = viewModel.state.value
        assertEquals(ExercisePrepareMode.Rest, state.mode)
        assertEquals("Reverse Lunge", state.displayExercise?.name)
        assertEquals(40, state.headerProgressPercent)
    }

    @Test
    fun restControls_updateStaticTimerAndReturnToPrepare() {
        val viewModel = ExercisePrepareViewModel(
            exerciseId = "ex-squat",
            repository = FakeLibraryRepository(),
        ).apply { enableRestTicker = false }
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.enterRestMode()
        viewModel.addRestTime()
        assertEquals(75, viewModel.state.value.restSeconds)
        viewModel.toggleRestPause()
        assertTrue(viewModel.state.value.isRestPaused)
        viewModel.skipRest()

        val state = viewModel.state.value
        assertEquals(ExercisePrepareMode.Prepare, state.mode)
        assertEquals(false, state.isRestPaused)
    }

    @Test
    fun legacySlug_mapsKnownExerciseIds() {
        assertEquals("squat", legacySlug("ex-squat"))
        assertEquals("squat-warm", legacySlug("ex-squat-warm"))
        assertEquals("lunge", legacySlug("ex-lunge"))
    }

    @Test
    fun normalizeTrainingSlug_mapsExploreIds() {
        assertEquals("squat", normalizeTrainingSlug("ex-squat"))
        assertEquals("bodyweight-squat", normalizeTrainingSlug("bodyweight-squat"))
    }

    @Test
    fun workoutExercise_loadsFromWorkoutFlowCache() {
        WorkoutFlowCache.clearAll()
        WorkoutFlowCache.put(
            WorkoutFlowConfigUi(
                workoutId = "workout-1",
                title = "Lower body",
                subtitle = "Workout session",
                exercises = listOf(
                    WorkoutFlowExerciseUi(
                        id = "planned-ex-1",
                        exerciseSlug = "walking-lunge",
                        name = "Walking Lunge",
                        sets = 4,
                        reps = 10,
                        durationSeconds = null,
                        restSeconds = 45,
                    ),
                ),
            ),
        )
        val viewModel = ExercisePrepareViewModel(
            exerciseId = "planned-ex-1",
            workoutId = "workout-1",
        )

        kotlinx.coroutines.runBlocking { viewModel.load() }

        val exercise = viewModel.state.value.exercise
        assertNotNull(exercise)
        assertEquals("Walking Lunge", exercise.name)
        assertEquals("walking-lunge", exercise.legacyFileName)
        assertEquals("45s", exercise.rest)
        assertEquals(45, viewModel.state.value.restSeconds)
    }

    @Test
    fun restTick_decrementsAndReturnsToPrepareAtZero() {
        val resting = ExercisePrepareUiState(
            mode = ExercisePrepareMode.Rest,
            restSeconds = 2,
        )
        assertEquals(1, applyRestSecondTick(resting).restSeconds)
        val done = applyRestSecondTick(resting.copy(restSeconds = 1))
        assertEquals(ExercisePrepareMode.Prepare, done.mode)
        assertEquals(0, done.restSeconds)
    }

    @Test
    fun restTick_respectsPause() {
        val paused = ExercisePrepareUiState(
            mode = ExercisePrepareMode.Rest,
            restSeconds = 10,
            isRestPaused = true,
        )
        assertEquals(paused, applyRestSecondTick(paused))
    }

    @Test
    fun formatRestTimer_padsMinutesAndSeconds() {
        assertEquals("00:30", formatRestTimer(30))
        assertEquals("01:05", formatRestTimer(65))
        assertEquals("00:00", formatRestTimer(0))
    }
}
