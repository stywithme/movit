package com.movit.feature.training

import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingSessionViewModelTest {
  @Test
  fun workoutUploadContext_usesExploreContextConstant() {
    assertEquals("explore_workout", WorkoutExecutionBatchCoordinator.EXPLORE_WORKOUT_CONTEXT)
  }

  @Test
  fun flowCoordinator_restNearEndThreshold_isThreeSeconds() {
    assertEquals(3_000L, TrainingSessionFlowCoordinator.REST_NEAR_END_MS)
  }

  @Test
  fun trainingSessionRouteArgs_supportsTypedWorkoutFlow() {
    val args = TrainingSessionRouteArgs(
      exerciseSlug = "bodyweight-squat",
      exerciseName = "Squat",
      targetReps = 10,
      flowItems = listOf(
        TrainingFlowItem.Exercise(
          slug = "bodyweight-squat",
          displayName = "Squat",
          sets = 2,
        ),
      ),
      uploadContext = WorkoutUploadContext(workoutGroupId = "grp-1", workoutTemplateId = "wt-1"),
    )
    assertEquals("bodyweight-squat", args.exerciseSlug)
    assertEquals(1, args.flowItems?.size)
    assertEquals("grp-1", args.uploadContext?.workoutGroupId)
  }

  @Test
  fun trainingSessionRouteArgs_carriesPlannedWorkoutAndStartIndex() {
    val args = TrainingSessionRouteArgs(
      exerciseSlug = "squat",
      exerciseName = "Squat",
      targetReps = 10,
      workoutId = "session:prog:1:1:pw-1",
      startExerciseIndex = 2,
      plannedWorkout = PlannedWorkoutContext(
        plannedWorkoutId = "pw-1",
        programId = "prog-1",
        weekNumber = 1,
        dayNumber = 1,
      ),
    )
    assertEquals(2, args.startExerciseIndex)
    assertEquals("pw-1", args.plannedWorkout?.plannedWorkoutId)
    assertEquals("prog-1", args.plannedWorkout?.programId)
  }

  @Test
  fun workoutFlowPhase_defaultsToNone() {
    val state = TrainingSessionUiState(
      exerciseSlug = "squat",
      exerciseName = "Squat",
      targetReps = 10,
    )
    assertEquals(WorkoutFlowPhase.NONE, state.workoutFlowPhase)
    assertFalse(state.isResting)
  }

  @Test
  fun workoutFlowPhase_canRepresentRest() {
    val state = TrainingSessionUiState(
      exerciseSlug = "squat",
      exerciseName = "Squat",
      targetReps = 10,
      workoutFlowEnabled = true,
      workoutFlowPhase = WorkoutFlowPhase.REST,
      isResting = true,
      restSecondsRemaining = 25,
      nextExerciseName = "Lunge",
    )
    assertTrue(state.isResting)
    assertEquals(25, state.restSecondsRemaining)
  }
}
