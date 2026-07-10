package com.movit.feature.training

import com.movit.core.training.session.SessionRunState

/**
 * P1.4 — when Back should pop vs show Continue / Save and exit / End workout.
 *
 * Inactive setup (no meaningful progress yet) → normal pop.
 * Training, rest, paused, or any workout progress → confirm.
 */
object TrainingSessionExitPolicy {
    fun shouldConfirmExit(state: TrainingSessionUiState): Boolean {
        if (state.isComplete || state.isWorkoutComplete) return false
        if (state.exitPrompt != null) return false
        if (state.isResting || state.workoutFlowPhase == WorkoutFlowPhase.REST) return true
        when (state.runState) {
            SessionRunState.TRAINING,
            SessionRunState.PAUSED,
            SessionRunState.AUTO_PAUSED,
            SessionRunState.RESUME_SETUP,
            SessionRunState.RESUME_COUNTDOWN,
            SessionRunState.COUNTDOWN,
            -> return true
            SessionRunState.IDLE,
            SessionRunState.SETUP_POSE,
            SessionRunState.COMPLETED,
            -> Unit
        }
        return hasProgressWorthSaving(state)
    }

    fun hasProgressWorthSaving(state: TrainingSessionUiState): Boolean {
        if (state.repCount > 0) return true
        if (state.currentSetNumber > 1) return true
        if (state.workoutFlowEnabled && state.workoutFlowProgressPercent > 0) return true
        return false
    }
}
