package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutFlowStateTest {

    @Test
    fun customize_stepperUpdatesSets() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val firstId = viewModel.state.value.config?.exercises?.first()?.id
        assertNotNull(firstId)
        val initialSets = viewModel.state.value.config!!.exercises.first().sets
        viewModel.onSetsChanged(firstId, initialSets + 1)
        assertEquals(initialSets + 1, viewModel.state.value.config!!.exercises.first().sets)
    }

    @Test
    fun customize_repsStepperUpdatesReps() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val firstId = viewModel.state.value.config?.exercises?.first()?.id
        assertNotNull(firstId)
        viewModel.onRepsChanged(firstId, 15)
        assertEquals(15, viewModel.state.value.config!!.exercises.first().reps)
    }

    @Test
    fun customize_deleteExercise_removesFromList() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val initialCount = viewModel.state.value.config!!.exercises.size
        val firstId = viewModel.state.value.config!!.exercises.first().id
        viewModel.deleteExercise(firstId)
        assertEquals(initialCount - 1, viewModel.state.value.config!!.exercises.size)
    }

    @Test
    fun customize_moveExercise_reordersList() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val exercises = viewModel.state.value.config!!.exercises
        if (exercises.size < 2) return
        val firstId = exercises.first().id
        val secondId = exercises[1].id
        viewModel.moveExercise(firstId, 1)
        val reordered = viewModel.state.value.config!!.exercises
        assertEquals(secondId, reordered.first().id)
        assertEquals(firstId, reordered[1].id)
    }

    @Test
    fun customize_commitForRun_rejectsEmptyList() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.state.value.config!!.exercises.forEach { viewModel.deleteExercise(it.id) }
        viewModel.commitForRun()
        assertNull(WorkoutFlowCache.get("preview"))
    }

    @Test
    fun customize_restSegmentUpdatesSeconds() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.onRestOptionSelected(90)
        assertEquals(90, viewModel.state.value.config?.restBetweenSetsSeconds)
    }

    @Test
    fun customize_commitForRun_populatesCache() {
        WorkoutFlowCache.clearAll()
        val viewModel = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        var committed: WorkoutFlowConfigUi? = null
        viewModel.commitForRun { committed = it }
        assertNotNull(committed)
        assertEquals(committed, WorkoutFlowCache.get("preview"))
    }

    @Test
    fun run_loadsSequenceFromCache() {
        WorkoutFlowCache.clearAll()
        val customize = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking {
            customize.load()
            customize.commitForRun()
        }
        val run = WorkoutRunViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { run.load() }
        val state = run.state.value
        assertNotNull(state.config)
        assertTrue(state.config!!.exercises.isNotEmpty())
        assertEquals(1, state.currentSet)
        assertTrue(state.progressPercent in 0..100)
        assertTrue(state.sequenceItems().any { it.status == WorkoutRunExerciseStatus.Active })
    }

    @Test
    fun mapper_buildsFlowFromSessionPreview() {
        val config = WorkoutFlowMapper.fromSession(WorkoutSessionPreviewData.preview)
        assertEquals("preview", config.workoutId)
        assertTrue(config.exercises.size >= 5)
        assertEquals(60, config.restBetweenSetsSeconds)
    }

    @Test
    fun run_legacyFileName_matchesCurrentExerciseSlug() {
        WorkoutFlowCache.clearAll()
        val customize = WorkoutCustomizeViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking {
            customize.load()
            customize.commitForRun()
        }
        val run = WorkoutRunViewModel(
            workoutId = "preview",
            sessionRepository = DefaultWorkoutSessionRepository(),
        )
        kotlinx.coroutines.runBlocking { run.load() }
        assertEquals("bodyweight-squat", run.legacyFileNameForStart())
    }
}
