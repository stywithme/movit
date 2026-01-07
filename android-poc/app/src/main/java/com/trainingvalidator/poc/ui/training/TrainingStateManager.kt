package com.trainingvalidator.poc.ui.training

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TrainingStateManager - Manages training state transitions
 * 
 * Handles the state machine for training flow:
 * SETUP_POSE → COUNTDOWN → TRAINING → COMPLETED
 * 
 * Also handles visibility pause/resume states
 */
class TrainingStateManager {
    
    companion object {
        private const val TAG = "TrainingStateManager"
    }
    
    /**
     * Training states representing the UI flow
     */
    enum class TrainingState {
        /** Waiting for user to get into starting position */
        SETUP_POSE,
        
        /** Countdown before training starts (3-2-1-GO) */
        COUNTDOWN,
        
        /** Active training in progress */
        TRAINING,
        
        /** Training paused by user */
        PAUSED,
        
        /** Training completed (target reached or video ended) */
        COMPLETED,
        
        /** Paused due to joints not visible in camera */
        VISIBILITY_PAUSED,
        
        /** After visibility returns, validating startPose before resume */
        VISIBILITY_SETUP_POSE
    }
    
    // Current state
    private val _state = MutableStateFlow(TrainingState.SETUP_POSE)
    val state: StateFlow<TrainingState> = _state.asStateFlow()
    
    // Saved state for visibility resume
    private var savedRepCount: Int = 0
    
    /**
     * Get current state value
     */
    val currentState: TrainingState
        get() = _state.value
    
    /**
     * Transition to a new state
     * Returns true if transition was valid
     */
    fun transitionTo(newState: TrainingState): Boolean {
        val currentState = _state.value
        
        // Validate transition
        if (!isValidTransition(currentState, newState)) {
            Log.w(TAG, "Invalid transition: $currentState -> $newState")
            return false
        }
        
        Log.d(TAG, "State transition: $currentState -> $newState")
        _state.value = newState
        return true
    }
    
    /**
     * Check if a state transition is valid
     */
    private fun isValidTransition(from: TrainingState, to: TrainingState): Boolean {
        return when (from) {
            TrainingState.SETUP_POSE -> to in listOf(
                TrainingState.COUNTDOWN,
                TrainingState.COMPLETED // For video mode direct start
            )
            
            TrainingState.COUNTDOWN -> to in listOf(
                TrainingState.TRAINING,
                TrainingState.SETUP_POSE // If pose is lost during countdown
            )
            
            TrainingState.TRAINING -> to in listOf(
                TrainingState.PAUSED,
                TrainingState.COMPLETED,
                TrainingState.VISIBILITY_PAUSED
            )
            
            TrainingState.PAUSED -> to in listOf(
                TrainingState.TRAINING,
                TrainingState.COMPLETED
            )
            
            TrainingState.COMPLETED -> to in listOf(
                TrainingState.SETUP_POSE // Restart
            )
            
            TrainingState.VISIBILITY_PAUSED -> to in listOf(
                TrainingState.VISIBILITY_SETUP_POSE,
                TrainingState.COMPLETED
            )
            
            TrainingState.VISIBILITY_SETUP_POSE -> to in listOf(
                TrainingState.COUNTDOWN,
                TrainingState.VISIBILITY_PAUSED // If visibility lost again
            )
        }
    }
    
    /**
     * Force state (for initialization or special cases)
     */
    fun forceState(state: TrainingState) {
        Log.d(TAG, "Force state: $state")
        _state.value = state
    }
    
    /**
     * Save rep count when pausing for visibility
     */
    fun saveRepCountForVisibility(repCount: Int) {
        savedRepCount = repCount
        Log.d(TAG, "Saved rep count for visibility: $repCount")
    }
    
    /**
     * Get saved rep count for visibility resume
     */
    fun getSavedRepCount(): Int = savedRepCount
    
    /**
     * Check if currently in an active training state
     */
    fun isActiveTraining(): Boolean {
        return _state.value == TrainingState.TRAINING
    }
    
    /**
     * Check if in any paused state
     */
    fun isPaused(): Boolean {
        return _state.value in listOf(
            TrainingState.PAUSED,
            TrainingState.VISIBILITY_PAUSED
        )
    }
    
    /**
     * Check if training can process frames
     */
    fun canProcessFrames(): Boolean {
        return _state.value in listOf(
            TrainingState.SETUP_POSE,
            TrainingState.COUNTDOWN,
            TrainingState.TRAINING,
            TrainingState.VISIBILITY_SETUP_POSE
        )
    }
    
    /**
     * Reset to initial state
     */
    fun reset() {
        _state.value = TrainingState.SETUP_POSE
        savedRepCount = 0
        Log.d(TAG, "State manager reset")
    }
}
