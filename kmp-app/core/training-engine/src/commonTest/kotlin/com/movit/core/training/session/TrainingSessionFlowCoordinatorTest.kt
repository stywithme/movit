package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrainingSessionFlowCoordinatorTest {
    private val squat = TrainingFlowItem.Exercise(
        slug = "bodyweight-squat",
        displayName = "Squat",
        sets = 2,
        targetReps = 10,
        restBetweenSetsMs = 5_000L,
        restAfterExerciseMs = 0L,
    )

    @Test
    fun start_opensPreExerciseForFirstItem() {
        val coordinator = TrainingSessionFlowCoordinator(listOf(squat))
        coordinator.start()

        val state = coordinator.state.value
        assertIs<TrainingSessionFlowCoordinator.State.PreExercise>(state)
        assertEquals(1, state.setNumber)
        assertEquals(2, state.totalSets)
    }

    @Test
    fun onExerciseCompleted_entersRestBetweenSetsBeforeNextSet() {
        val coordinator = TrainingSessionFlowCoordinator(listOf(squat))
        coordinator.start()
        coordinator.markExercising()
        coordinator.onExerciseCompleted()

        val rest = coordinator.state.value
        assertIs<TrainingSessionFlowCoordinator.State.Rest>(rest)
        assertEquals(TrainingSessionFlowCoordinator.RestContext.BETWEEN_SETS, rest.restContext)
        assertEquals(2, rest.setNumber)
        assertEquals(5_000L, rest.remainingMs)
    }

    @Test
    fun skipRest_advancesToPreExercise() {
        val coordinator = TrainingSessionFlowCoordinator(listOf(squat))
        coordinator.start()
        coordinator.markExercising()
        coordinator.onExerciseCompleted()
        coordinator.skipRest()

        val state = coordinator.state.value
        assertIs<TrainingSessionFlowCoordinator.State.PreExercise>(state)
        assertEquals(2, state.setNumber)
    }

    @Test
    fun tickRest_completesAndOpensPreExercise() {
        val coordinator = TrainingSessionFlowCoordinator(listOf(squat))
        coordinator.start()
        coordinator.markExercising()
        coordinator.onExerciseCompleted()

        assertTrue(coordinator.tickRest(5_000L))
        assertIs<TrainingSessionFlowCoordinator.State.PreExercise>(coordinator.state.value)
    }

    @Test
    fun lastSetCompletesWorkoutWhenSingleExercise() {
        val coordinator = TrainingSessionFlowCoordinator(listOf(squat.copy(sets = 1)))
        coordinator.start()
        coordinator.markExercising()
        coordinator.onExerciseCompleted()

        assertEquals(TrainingSessionFlowCoordinator.State.WorkoutComplete, coordinator.state.value)
    }

    @Test
    fun workoutProgressPercent_excludesWarmupFromDenominator() {
        val mainOnly = listOf(squat.copy(sets = 2, phaseRole = "MAIN"))
        val coordinator = TrainingSessionFlowCoordinator(mainOnly)
        coordinator.start()
        assertEquals(0, coordinator.workoutProgressPercent())
        coordinator.markExercising()
        coordinator.onExerciseCompleted()
        assertEquals(50, coordinator.workoutProgressPercent())
    }
}
