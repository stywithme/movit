package com.movit.feature.training

/** Pure policy for camera flip UI + processing freeze (testable smoke for §34.5 flip camera). */
internal object TrainingCameraSwitchPolicy {
    fun onSwitchStarted(state: TrainingSessionUiState): TrainingSessionUiState =
        state.copy(
            isCameraSwitching = true,
            errorMessage = null,
        )
}
