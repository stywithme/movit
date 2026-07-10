package com.movit.feature.training

import com.movit.core.training.session.SessionRunState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingSessionExitPolicyTest {
    @Test
    fun inactiveSetup_withoutProgress_doesNotConfirm() {
        val state = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            runState = SessionRunState.SETUP_POSE,
        )
        assertFalse(TrainingSessionExitPolicy.shouldConfirmExit(state))
    }

    @Test
    fun training_confirmsExit() {
        val state = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            runState = SessionRunState.TRAINING,
        )
        assertTrue(TrainingSessionExitPolicy.shouldConfirmExit(state))
    }

    @Test
    fun rest_confirmsExit() {
        val state = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            workoutFlowEnabled = true,
            workoutFlowPhase = WorkoutFlowPhase.REST,
            isResting = true,
            runState = SessionRunState.IDLE,
        )
        assertTrue(TrainingSessionExitPolicy.shouldConfirmExit(state))
    }

    @Test
    fun setup_withRepProgress_confirmsExit() {
        val state = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            runState = SessionRunState.SETUP_POSE,
            repCount = 3,
        )
        assertTrue(TrainingSessionExitPolicy.shouldConfirmExit(state))
        assertTrue(TrainingSessionExitPolicy.hasProgressWorthSaving(state))
    }

    @Test
    fun complete_doesNotConfirm() {
        val state = TrainingSessionUiState(
            exerciseSlug = "squat",
            exerciseName = "Squat",
            targetReps = 10,
            isComplete = true,
            runState = SessionRunState.COMPLETED,
        )
        assertFalse(TrainingSessionExitPolicy.shouldConfirmExit(state))
    }
}
