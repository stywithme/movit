package com.trainingvalidator.poc.training.workout

import android.util.Log
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WorkoutTrainingEngine - Hot-swap capable wrapper for TrainingEngine
 * 
 * This engine supports switching between exercises WITHOUT recreating the camera
 * or pose detection pipeline. Used for alternating mode workouts.
 * 
 * Key Features:
 * - Hot-swap: Switch exercise config instantly
 * - Preserves camera/pose detection state
 * - Manages exercise sequence for alternating mode
 * - Tracks progress across all exercises
 * 
 * Usage:
 * 1. Create with list of exercises
 * 2. Call start() to begin with first exercise
 * 3. Call switchToNextExercise() when rep limit is reached
 * 4. Continue until all exercises complete their targets
 */
class WorkoutTrainingEngine(
    private val exercises: List<LoadedExercise>,
    private val workoutConfig: WorkoutConfig,
    private val defaultDifficulty: DifficultyType = DifficultyType.BEGINNER
) {
    companion object {
        private const val TAG = "WorkoutTrainingEngine"
    }
    
    // ==================== Current State ====================
    
    private var currentExerciseIndex = 0
    private var currentEngine: TrainingEngine? = null
    private var _exerciseRepsCompleted = mutableMapOf<Int, Int>()  // index -> reps done
    private var _exerciseRepsTargets = mutableMapOf<Int, Int>()    // index -> target reps
    private var _totalRepsCompleted = 0
    private var _currentRound = 1
    
    // ==================== State Flows ====================
    
    private val _currentExercise = MutableStateFlow<LoadedExercise?>(null)
    val currentExercise: StateFlow<LoadedExercise?> = _currentExercise
    
    private val _isWorkoutCompleted = MutableStateFlow(false)
    val isWorkoutCompleted: StateFlow<Boolean> = _isWorkoutCompleted
    
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching
    
    private val _switchInfo = MutableStateFlow<SwitchInfo?>(null)
    val switchInfo: StateFlow<SwitchInfo?> = _switchInfo
    
    // ==================== Callbacks ====================
    
    /**
     * Called when exercise is switched (for UI animation)
     */
    var onExerciseSwitched: ((fromExercise: String, toExercise: String, repsThisSession: Int) -> Unit)? = null
    
    /**
     * Called when all exercises are complete
     */
    var onWorkoutCompleted: ((totalReps: Int, rounds: Int) -> Unit)? = null
    
    /**
     * Called when a round is complete (in alternating mode, after cycling through all exercises)
     */
    var onRoundCompleted: ((roundNumber: Int, totalRounds: Int) -> Unit)? = null
    
    // ==================== Properties ====================
    
    val isAlternatingMode: Boolean get() = workoutConfig.isAlternating()
    val repsPerSwitch: Int get() = workoutConfig.getEffectiveRepsPerSwitch()
    val totalExercises: Int get() = exercises.size
    
    // ==================== Initialization ====================
    
    init {
        // Initialize targets for each exercise
        exercises.forEachIndexed { index, exercise ->
            val targetReps = exercise.getTargetReps() ?: 10
            _exerciseRepsTargets[index] = targetReps
            _exerciseRepsCompleted[index] = 0
        }
        
        Log.d(TAG, "WorkoutTrainingEngine initialized with ${exercises.size} exercises")
        Log.d(TAG, "Mode: ${if (isAlternatingMode) "ALTERNATING" else "SEQUENTIAL"}, repsPerSwitch: $repsPerSwitch")
    }
    
    // ==================== Public API ====================
    
    /**
     * Get the current TrainingEngine
     * Returns null if not started
     */
    fun getEngine(): TrainingEngine? = currentEngine
    
    /**
     * Start the workout with the first exercise
     * Creates the initial TrainingEngine
     */
    fun start(): TrainingEngine {
        currentExerciseIndex = 0
        _currentRound = 1
        _totalRepsCompleted = 0
        _exerciseRepsCompleted.keys.forEach { _exerciseRepsCompleted[it] = 0 }
        
        val exercise = exercises[currentExerciseIndex]
        _currentExercise.value = exercise
        
        currentEngine = createEngine(exercise)
        
        Log.d(TAG, "Started with exercise: ${exercise.getDisplayName()}")
        
        return currentEngine!!
    }
    
    /**
     * Get reps allowed for current session
     * In alternating mode, limited by repsPerSwitch
     */
    fun getRepsForCurrentSession(): Int {
        if (!isAlternatingMode) return Int.MAX_VALUE
        
        val remaining = getRemainingRepsForCurrentExercise()
        return minOf(repsPerSwitch, remaining)
    }
    
    /**
     * Get remaining reps for current exercise to reach its target
     */
    fun getRemainingRepsForCurrentExercise(): Int {
        val target = _exerciseRepsTargets[currentExerciseIndex] ?: 0
        val completed = _exerciseRepsCompleted[currentExerciseIndex] ?: 0
        return (target - completed).coerceAtLeast(0)
    }
    
    /**
     * Called when reps are completed in the current session
     * Returns true if should switch to next exercise
     */
    fun onRepsCompleted(reps: Int): SwitchResult {
        // Update completed reps for current exercise
        val currentCompleted = _exerciseRepsCompleted[currentExerciseIndex] ?: 0
        val newCompleted = currentCompleted + reps
        _exerciseRepsCompleted[currentExerciseIndex] = newCompleted
        _totalRepsCompleted += reps
        
        val target = _exerciseRepsTargets[currentExerciseIndex] ?: 0
        
        Log.d(TAG, "Exercise $currentExerciseIndex: $newCompleted/$target reps completed")
        
        if (!isAlternatingMode) {
            // Sequential mode: check if current exercise is done
            if (newCompleted >= target) {
                return handleExerciseComplete()
            }
            return SwitchResult.Continue
        }
        
        // Alternating mode: always switch after session reps
        return handleAlternatingSwitch()
    }
    
    /**
     * Switch to next exercise (hot-swap)
     * Creates new TrainingEngine with the next exercise config
     * Returns the new engine
     */
    fun switchToNextExercise(): TrainingEngine? {
        val previousExercise = _currentExercise.value
        val previousName = previousExercise?.getDisplayName() ?: ""
        
        // Find next exercise with remaining reps
        val nextIndex = findNextIncompleteExercise()
        
        if (nextIndex == -1) {
            // All exercises complete - check for more rounds
            return handleRoundComplete()
        }
        
        currentExerciseIndex = nextIndex
        val nextExercise = exercises[currentExerciseIndex]
        
        // Signal switching (for UI animation)
        _isSwitching.value = true
        _switchInfo.value = SwitchInfo(
            fromExercise = previousName,
            toExercise = nextExercise.getDisplayName(),
            repsThisSession = getRepsForCurrentSession()
        )
        
        // Stop current engine
        currentEngine?.stop()
        
        // Create new engine with next exercise
        _currentExercise.value = nextExercise
        currentEngine = createEngine(nextExercise)
        
        Log.d(TAG, "Hot-swapped from $previousName to ${nextExercise.getDisplayName()}")
        
        // Notify callback
        onExerciseSwitched?.invoke(previousName, nextExercise.getDisplayName(), getRepsForCurrentSession())
        
        // Clear switching state after a brief moment (UI can animate)
        _isSwitching.value = false
        
        return currentEngine
    }
    
    /**
     * Force switch to a specific exercise by index
     */
    fun switchToExercise(index: Int): TrainingEngine? {
        if (index < 0 || index >= exercises.size) return null
        
        currentExerciseIndex = index - 1  // Will be incremented in switchToNextExercise
        return switchToNextExercise()
    }
    
    /**
     * Get progress info for UI
     */
    fun getProgressInfo(): WorkoutProgressInfo {
        return WorkoutProgressInfo(
            currentExerciseIndex = currentExerciseIndex,
            totalExercises = exercises.size,
            currentExerciseName = _currentExercise.value?.getDisplayName() ?: "",
            currentExerciseReps = _exerciseRepsCompleted[currentExerciseIndex] ?: 0,
            currentExerciseTarget = _exerciseRepsTargets[currentExerciseIndex] ?: 0,
            totalRepsCompleted = _totalRepsCompleted,
            totalRepsTarget = _exerciseRepsTargets.values.sum(),
            currentRound = _currentRound,
            totalRounds = workoutConfig.rounds,
            repsThisSession = getRepsForCurrentSession()
        )
    }
    
    /**
     * Stop the workout and cleanup
     */
    fun stop() {
        currentEngine?.stop()
        currentEngine = null
        _currentExercise.value = null
    }
    
    // ==================== Private Methods ====================
    
    private fun createEngine(exercise: LoadedExercise): TrainingEngine {
        return TrainingEngine(
            exerciseConfig = exercise.config,
            difficulty = exercise.difficulty,
            poseVariantIndex = exercise.workoutExercise.variantIndex
        )
    }
    
    private fun findNextIncompleteExercise(): Int {
        val exerciseCount = exercises.size
        
        // Start from next exercise and wrap around
        for (i in 1..exerciseCount) {
            val index = (currentExerciseIndex + i) % exerciseCount
            val completed = _exerciseRepsCompleted[index] ?: 0
            val target = _exerciseRepsTargets[index] ?: 0
            
            if (completed < target) {
                return index
            }
        }
        
        // All exercises complete
        return -1
    }
    
    private fun handleAlternatingSwitch(): SwitchResult {
        val nextIndex = findNextIncompleteExercise()
        
        if (nextIndex == -1) {
            // All done in this round
            if (_currentRound >= workoutConfig.rounds) {
                _isWorkoutCompleted.value = true
                onWorkoutCompleted?.invoke(_totalRepsCompleted, _currentRound)
                return SwitchResult.WorkoutComplete
            }
            
            // More rounds to go
            return SwitchResult.RoundComplete(_currentRound, workoutConfig.rounds)
        }
        
        // Need to switch
        return SwitchResult.SwitchNow(
            nextIndex = nextIndex,
            nextExerciseName = exercises[nextIndex].getDisplayName(),
            repsThisSession = minOf(
                repsPerSwitch,
                (_exerciseRepsTargets[nextIndex] ?: 0) - (_exerciseRepsCompleted[nextIndex] ?: 0)
            )
        )
    }
    
    private fun handleExerciseComplete(): SwitchResult {
        val nextIndex = currentExerciseIndex + 1
        
        if (nextIndex >= exercises.size) {
            // Round complete
            if (_currentRound >= workoutConfig.rounds) {
                _isWorkoutCompleted.value = true
                onWorkoutCompleted?.invoke(_totalRepsCompleted, _currentRound)
                return SwitchResult.WorkoutComplete
            }
            
            return SwitchResult.RoundComplete(_currentRound, workoutConfig.rounds)
        }
        
        // Move to next exercise
        return SwitchResult.SwitchNow(
            nextIndex = nextIndex,
            nextExerciseName = exercises[nextIndex].getDisplayName(),
            repsThisSession = Int.MAX_VALUE
        )
    }
    
    private fun handleRoundComplete(): TrainingEngine? {
        if (_currentRound >= workoutConfig.rounds) {
            // Workout complete
            _isWorkoutCompleted.value = true
            onWorkoutCompleted?.invoke(_totalRepsCompleted, _currentRound)
            return null
        }
        
        // Start new round
        _currentRound++
        currentExerciseIndex = 0
        
        // Reset completed reps for new round
        exercises.forEachIndexed { index, _ ->
            _exerciseRepsCompleted[index] = 0
        }
        
        onRoundCompleted?.invoke(_currentRound - 1, workoutConfig.rounds)
        
        // Start first exercise of new round
        val exercise = exercises[0]
        _currentExercise.value = exercise
        
        currentEngine?.stop()
        currentEngine = createEngine(exercise)
        
        Log.d(TAG, "Starting round $_currentRound with ${exercise.getDisplayName()}")
        
        return currentEngine
    }
}

/**
 * Result of checking if we should switch exercises
 */
sealed class SwitchResult {
    /** Continue with current exercise */
    object Continue : SwitchResult()
    
    /** Switch to next exercise immediately */
    data class SwitchNow(
        val nextIndex: Int,
        val nextExerciseName: String,
        val repsThisSession: Int
    ) : SwitchResult()
    
    /** Current round is complete, need rest before next */
    data class RoundComplete(
        val roundNumber: Int,
        val totalRounds: Int
    ) : SwitchResult()
    
    /** Entire workout is complete */
    object WorkoutComplete : SwitchResult()
}

/**
 * Info about the current switch (for UI animation)
 */
data class SwitchInfo(
    val fromExercise: String,
    val toExercise: String,
    val repsThisSession: Int
)

/**
 * Progress info for UI display
 */
data class WorkoutProgressInfo(
    val currentExerciseIndex: Int,
    val totalExercises: Int,
    val currentExerciseName: String,
    val currentExerciseReps: Int,
    val currentExerciseTarget: Int,
    val totalRepsCompleted: Int,
    val totalRepsTarget: Int,
    val currentRound: Int,
    val totalRounds: Int,
    val repsThisSession: Int
) {
    fun getExerciseProgress(): Float {
        if (currentExerciseTarget == 0) return 0f
        return currentExerciseReps.toFloat() / currentExerciseTarget
    }
    
    fun getOverallProgress(): Float {
        if (totalRepsTarget == 0) return 0f
        return totalRepsCompleted.toFloat() / totalRepsTarget
    }
    
    fun getPositionDisplay(): String = "${currentExerciseIndex + 1}/${totalExercises}"
}
