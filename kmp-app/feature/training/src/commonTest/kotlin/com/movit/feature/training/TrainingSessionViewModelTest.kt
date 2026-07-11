package com.movit.feature.training

import com.movit.core.training.session.SessionRunState
import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingSessionViewModelTest {
  @Test
  fun workoutUploadContext_usesExploreContextConstant() {
    assertEquals("explore_workout", WorkoutUploadContext.EXPLORE_WORKOUT_CONTEXT)
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
  fun trainingSessionRouteArgs_carriesPoseVariantIndex() {
    val args = TrainingSessionRouteArgs(
      exerciseSlug = "curl",
      exerciseName = "Curl",
      targetReps = 10,
      poseVariantIndex = 2,
    )
    assertEquals(2, args.poseVariantIndex)
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

  @Test
  fun requiresCamera_falseWhenCompleteOrResting() {
    val base = TrainingSessionUiState(
      exerciseSlug = "squat",
      exerciseName = "Squat",
      targetReps = 10,
    )
    assertTrue(base.requiresCamera())
    assertFalse(base.copy(isComplete = true).requiresCamera())
    assertFalse(base.copy(runState = SessionRunState.COMPLETED).requiresCamera())
    assertFalse(
      base.copy(
        workoutFlowPhase = WorkoutFlowPhase.REST,
        isResting = true,
      ).requiresCamera(),
    )
  }

  /**
   * WP-01 / J-01: multi-exercise flow A (4 variants) → B (2 variants) with
   * poseVariantIndex=2 must clamp against B's count (→ 1), not A's.
   * Mirrors applyFlowExercise order: load new config, then resolve.
   */
  @Test
  fun applyFlowExercise_clampsPoseVariantIndexAgainstNewExerciseVariantCount() {
    val exerciseB = TrainingFlowItem.Exercise(
      slug = "exercise-b",
      displayName = "B",
      poseVariantIndex = 2,
    )
    // After loading B (2 variants) — correct order:
    val clamped = TrainingPoseVariantResolver.resolve(
      routePoseVariantIndex = 0,
      flowExercise = exerciseB,
      variantCount = 2,
    )
    assertEquals(1, clamped)

    // Bug reproduction: clamping against previous exercise A (4 variants) would keep 2.
    val wronglyClampedAgainstA = TrainingPoseVariantResolver.resolve(
      routePoseVariantIndex = 0,
      flowExercise = exerciseB,
      variantCount = 4,
    )
    assertEquals(2, wronglyClampedAgainstA)
  }
}
