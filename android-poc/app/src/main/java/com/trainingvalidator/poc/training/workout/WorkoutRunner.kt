package com.trainingvalidator.poc.training.workout

import android.content.res.AssetManager
import android.util.Log
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WorkoutRunner - Orchestrates a sequence of exercises for SEQUENTIAL mode
 * 
 * This class is used by WorkoutActivity to manage workouts where each exercise
 * is run in a SEPARATE TrainingActivity launch. After each exercise completes,
 * the user returns to WorkoutActivity which shows rest periods and progress.
 * 
 * Execution Modes:
 * 
 * 1. SEQUENTIAL (primary use case for this class):
 *    Complete all reps of Exercise 1, then rest, then Exercise 2, etc.
 *    Ex1 (10 reps) → Rest → Ex2 (10 reps) → Rest → Ex3 (10 reps)
 *    Each exercise launches a separate TrainingActivity instance.
 * 
 * 2. ALTERNATING (handled by WorkoutTrainingEngine instead):
 *    For true alternating workouts (1 rep left, 1 rep right), use 
 *    WorkoutTrainingEngine with Hot-Swap for seamless camera-continuous switching.
 * 
 * Architecture Note:
 *   - WorkoutRunner: Sequential mode orchestration (multiple TrainingActivity launches)
 *   - WorkoutTrainingEngine: Hot-Swap mode (single TrainingActivity, engine swapping)
 * 
 * Usage:
 * 1. Create WorkoutRunner with WorkoutConfig
 * 2. Call start() to begin
 * 3. Observe currentExercise, state, and progress flows
 * 4. Call onExerciseCompleted() when TrainingActivity returns
 * 5. Handle rest periods in UI
 * 6. Repeat until isCompleted
 */
class WorkoutRunner(
    private val workoutConfig: WorkoutConfig,
    private val assets: AssetManager
) {
    
    companion object {
        private const val TAG = "WorkoutRunner"
    }
    
    // ==================== State ====================
    
    private var _currentRound = 1
    private var _currentExerciseIndex = 0
    private var _completedExercisesTotal = 0
    private var _startTime: Long = 0L
    private var _exerciseResults = mutableListOf<WorkoutExerciseResult>()
    
    // Alternating mode state
    private var _exerciseRepsCompleted = mutableMapOf<Int, Int>()  // index -> reps done
    private var _exerciseRepsTargets = mutableMapOf<Int, Int>()    // index -> target reps
    private var _loadedExercises = mutableListOf<LoadedExercise>() // Pre-loaded for alternating
    private var _totalRepsInRound = 0
    private var _totalRepsCompleted = 0
    
    // ==================== State Flows ====================
    
    private val _state = MutableStateFlow(WorkoutState.IDLE)
    val state: StateFlow<WorkoutState> = _state
    
    private val _currentExercise = MutableStateFlow<LoadedExercise?>(null)
    val currentExercise: StateFlow<LoadedExercise?> = _currentExercise
    
    private val _progress = MutableStateFlow(createInitialProgress())
    val progress: StateFlow<WorkoutProgress> = _progress
    
    private val _restTimeRemainingMs = MutableStateFlow<Long?>(null)
    val restTimeRemainingMs: StateFlow<Long?> = _restTimeRemainingMs
    
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted
    
    // ==================== Callbacks ====================
    
    /**
     * Called when a new exercise is ready to start
     * In alternating mode, includes maxRepsThisSession
     */
    var onExerciseReady: ((LoadedExercise) -> Unit)? = null
    
    /**
     * Called when rest period starts
     */
    var onRestStarted: ((restDurationMs: Long, isRoundRest: Boolean) -> Unit)? = null
    
    /**
     * Called when switching to next exercise in alternating mode (no rest)
     */
    var onExerciseSwitched: ((LoadedExercise, previousExerciseName: String) -> Unit)? = null
    
    /**
     * Called when workout is completed
     */
    var onWorkoutCompleted: ((WorkoutResult) -> Unit)? = null
    
    /**
     * Called when round is completed (for multi-round workouts)
     */
    var onRoundCompleted: ((roundNumber: Int, totalRounds: Int) -> Unit)? = null
    
    // ==================== Public Properties ====================
    
    /**
     * Check if this workout uses alternating mode
     */
    val isAlternatingMode: Boolean get() = workoutConfig.isAlternating()
    
    /**
     * Get reps per switch (how many reps before switching exercise)
     */
    val repsPerSwitch: Int get() = workoutConfig.getEffectiveRepsPerSwitch()
    
    // ==================== Public API ====================
    
    /**
     * Start the workout
     * Loads the first exercise and notifies listeners
     */
    fun start() {
        Log.d(TAG, "Starting workout: ${workoutConfig.name.en} (mode: ${workoutConfig.executionMode})")
        
        _currentRound = 1
        _currentExerciseIndex = 0
        _completedExercisesTotal = 0
        _startTime = System.currentTimeMillis()
        _exerciseResults.clear()
        _isCompleted.value = false
        
        // Initialize alternating mode state
        if (isAlternatingMode) {
            initializeAlternatingMode()
        }
        
        _state.value = WorkoutState.PREPARING
        updateProgress()
        
        loadCurrentExercise()
    }
    
    /**
     * Called when the current exercise session is completed
     * 
     * In SEQUENTIAL mode: Called when all reps of an exercise are done
     * In ALTERNATING mode: Called when repsPerSwitch reps are done
     * 
     * @param completedReps Number of reps completed in this session
     * @param actualDurationMs Duration of this session
     * @param accuracy Accuracy percentage
     * @param isCompleted Whether the target was reached
     */
    fun onExerciseCompleted(
        completedReps: Int? = null,
        actualDurationMs: Long? = null,
        accuracy: Float = 0f,
        isCompleted: Boolean = true
    ) {
        if (isAlternatingMode) {
            handleAlternatingExerciseCompleted(completedReps ?: 0, accuracy)
        } else {
            handleSequentialExerciseCompleted(completedReps, actualDurationMs, accuracy, isCompleted)
        }
    }
    
    /**
     * Called when rest period is finished
     * UI should call this after countdown
     */
    fun onRestCompleted() {
        _restTimeRemainingMs.value = null
        
        when (_state.value) {
            WorkoutState.RESTING -> {
                // Load next exercise
                _state.value = WorkoutState.PREPARING
                loadCurrentExercise()
            }
            WorkoutState.ROUND_REST -> {
                // Start new round
                _state.value = WorkoutState.PREPARING
                if (isAlternatingMode) {
                    initializeAlternatingMode()  // Reset for new round
                }
                loadCurrentExercise()
            }
            else -> {
                Log.w(TAG, "onRestCompleted called in unexpected state: ${_state.value}")
            }
        }
    }
    
    /**
     * Pause the workout
     */
    fun pause() {
        if (_state.value == WorkoutState.EXERCISING || _state.value == WorkoutState.RESTING) {
            _state.value = WorkoutState.PAUSED
        }
    }
    
    /**
     * Resume the workout
     */
    fun resume() {
        if (_state.value == WorkoutState.PAUSED) {
            if (_restTimeRemainingMs.value != null) {
                _state.value = WorkoutState.RESTING
            } else {
                _state.value = WorkoutState.EXERCISING
            }
        }
    }
    
    /**
     * Mark workout as actively exercising
     */
    fun markExercising() {
        _state.value = WorkoutState.EXERCISING
    }
    
    /**
     * Skip current rest period
     */
    fun skipRest() {
        if (_state.value == WorkoutState.RESTING || _state.value == WorkoutState.ROUND_REST) {
            onRestCompleted()
        }
    }
    
    /**
     * Get workout info for display
     */
    fun getWorkoutInfo(): WorkoutInfo {
        return WorkoutInfo(
            name = workoutConfig.name,
            description = workoutConfig.description,
            type = workoutConfig.type,
            totalExercises = workoutConfig.exercises.size,
            totalRounds = workoutConfig.rounds,
            estimatedDurationMs = workoutConfig.getEstimatedDurationMs(),
            isAlternating = isAlternatingMode,
            repsPerSwitch = if (isAlternatingMode) repsPerSwitch else null
        )
    }
    
    /**
     * Get remaining reps for current exercise (alternating mode)
     */
    fun getRemainingRepsForCurrentExercise(): Int {
        if (!isAlternatingMode) return 0
        val target = _exerciseRepsTargets[_currentExerciseIndex] ?: 0
        val completed = _exerciseRepsCompleted[_currentExerciseIndex] ?: 0
        return (target - completed).coerceAtLeast(0)
    }
    
    /**
     * Get reps for this switch session
     * Returns min(repsPerSwitch, remaining reps for this exercise)
     */
    fun getRepsForThisSession(): Int {
        if (!isAlternatingMode) return Int.MAX_VALUE
        val remaining = getRemainingRepsForCurrentExercise()
        return minOf(repsPerSwitch, remaining)
    }
    
    // ==================== Private Methods - Initialization ====================
    
    private fun initializeAlternatingMode() {
        _exerciseRepsCompleted.clear()
        _exerciseRepsTargets.clear()
        _loadedExercises.clear()
        _totalRepsCompleted = 0
        _totalRepsInRound = 0
        
        // Pre-load all exercises and set up targets
        workoutConfig.exercises.forEachIndexed { index, workoutExercise ->
            // Load exercise config
            val exerciseConfig = ExerciseLoader.load(assets, workoutExercise.exercise)
            if (exerciseConfig != null) {
                val loadedExercise = LoadedExercise(
                    config = exerciseConfig,
                    workoutExercise = workoutExercise,
                    round = _currentRound,
                    indexInRound = index,
                    totalInRound = workoutConfig.exercises.size,
                    maxRepsThisSession = null  // Will be set when exercise starts
                )
                _loadedExercises.add(loadedExercise)
                
                // Set target reps
                val targetReps = workoutExercise.target.reps 
                    ?: exerciseConfig.repCountingConfig.reps
                    ?: 10
                
                _exerciseRepsTargets[index] = targetReps
                _exerciseRepsCompleted[index] = 0
                _totalRepsInRound += targetReps
            }
        }
        
        Log.d(TAG, "Alternating mode initialized: ${_loadedExercises.size} exercises, " +
                "$_totalRepsInRound total reps, $repsPerSwitch reps per switch")
    }
    
    // ==================== Private Methods - Sequential Mode ====================
    
    private fun handleSequentialExerciseCompleted(
        completedReps: Int?,
        actualDurationMs: Long?,
        accuracy: Float,
        isCompleted: Boolean
    ) {
        val currentWorkoutExercise = workoutConfig.getExercise(_currentExerciseIndex)
        
        // Record result
        _exerciseResults.add(WorkoutExerciseResult(
            exerciseName = currentWorkoutExercise?.exercise ?: "unknown",
            round = _currentRound,
            targetReps = currentWorkoutExercise?.target?.reps,
            completedReps = completedReps,
            targetDurationMs = currentWorkoutExercise?.target?.getDurationMs(),
            actualDurationMs = actualDurationMs,
            accuracy = accuracy,
            isCompleted = isCompleted
        ))
        
        _completedExercisesTotal++
        
        Log.d(TAG, "Sequential: Exercise completed: ${currentWorkoutExercise?.exercise} " +
                "(Round $_currentRound, Index $_currentExerciseIndex)")
        
        // Move to next
        _currentExerciseIndex++
        
        // Check if round is complete
        if (_currentExerciseIndex >= workoutConfig.exercises.size) {
            handleRoundComplete()
        } else {
            // More exercises in this round - start rest
            startExerciseRest()
        }
    }
    
    // ==================== Private Methods - Alternating Mode ====================
    
    private fun handleAlternatingExerciseCompleted(completedReps: Int, accuracy: Float) {
        val previousExerciseName = _currentExercise.value?.getDisplayName() ?: "Exercise"
        
        // Update reps completed for current exercise
        val currentCompleted = _exerciseRepsCompleted[_currentExerciseIndex] ?: 0
        val newCompleted = currentCompleted + completedReps
        _exerciseRepsCompleted[_currentExerciseIndex] = newCompleted
        _totalRepsCompleted += completedReps
        
        val targetForCurrent = _exerciseRepsTargets[_currentExerciseIndex] ?: 0
        
        Log.d(TAG, "Alternating: Exercise $_currentExerciseIndex completed $completedReps reps " +
                "($newCompleted/$targetForCurrent total)")
        
        // Check if this exercise is now complete
        if (newCompleted >= targetForCurrent) {
            // This exercise is done, mark it
            Log.d(TAG, "Exercise $_currentExerciseIndex fully completed")
        }
        
        // Find next exercise that still has reps remaining
        val nextIndex = findNextIncompleteExercise()
        
        if (nextIndex == -1) {
            // All exercises in this round are complete
            handleRoundComplete()
        } else {
            // Switch to next exercise
            _currentExerciseIndex = nextIndex
            updateProgress()
            
            // Check if we need rest between switches
            if (workoutConfig.restBetweenSwitchMs > 0) {
                startSwitchRest()
            } else {
                // No rest, immediate switch
                loadCurrentExerciseForAlternating()
                _currentExercise.value?.let { exercise ->
                    onExerciseSwitched?.invoke(exercise, previousExerciseName)
                }
            }
        }
    }
    
    private fun findNextIncompleteExercise(): Int {
        val exerciseCount = workoutConfig.exercises.size
        
        // Start from next exercise and wrap around
        for (i in 1..exerciseCount) {
            val index = (_currentExerciseIndex + i) % exerciseCount
            val completed = _exerciseRepsCompleted[index] ?: 0
            val target = _exerciseRepsTargets[index] ?: 0
            
            if (completed < target) {
                return index
            }
        }
        
        // All exercises complete
        return -1
    }
    
    private fun loadCurrentExerciseForAlternating() {
        if (_currentExerciseIndex >= _loadedExercises.size) {
            Log.e(TAG, "Invalid exercise index: $_currentExerciseIndex")
            return
        }
        
        val baseExercise = _loadedExercises[_currentExerciseIndex]
        val remaining = getRemainingRepsForCurrentExercise()
        val repsThisSession = minOf(repsPerSwitch, remaining)
        
        // Create new LoadedExercise with updated maxRepsThisSession
        val exerciseWithLimit = baseExercise.copy(
            round = _currentRound,
            maxRepsThisSession = repsThisSession
        )
        
        _currentExercise.value = exerciseWithLimit
        updateProgress()
        
        Log.d(TAG, "Alternating: Loaded ${baseExercise.config.name.en} " +
                "(${repsThisSession} reps this session, ${remaining} remaining)")
        
        onExerciseReady?.invoke(exerciseWithLimit)
    }
    
    private fun startSwitchRest() {
        _state.value = WorkoutState.RESTING
        _restTimeRemainingMs.value = workoutConfig.restBetweenSwitchMs
        updateProgress()
        
        Log.d(TAG, "Rest between switches: ${workoutConfig.restBetweenSwitchMs}ms")
        onRestStarted?.invoke(workoutConfig.restBetweenSwitchMs, false)
    }
    
    // ==================== Private Methods - Common ====================
    
    private fun loadCurrentExercise() {
        if (isAlternatingMode) {
            loadCurrentExerciseForAlternating()
        } else {
            loadCurrentExerciseSequential()
        }
    }
    
    private fun loadCurrentExerciseSequential() {
        val workoutExercise = workoutConfig.getExercise(_currentExerciseIndex)
        
        if (workoutExercise == null) {
            Log.e(TAG, "No exercise at index $_currentExerciseIndex")
            return
        }
        
        // Load the exercise config
        val exerciseConfig = ExerciseLoader.load(assets, workoutExercise.exercise)
        
        if (exerciseConfig == null) {
            Log.e(TAG, "Failed to load exercise: ${workoutExercise.exercise}")
            onExerciseCompleted(isCompleted = false)
            return
        }
        
        val loadedExercise = LoadedExercise(
            config = exerciseConfig,
            workoutExercise = workoutExercise,
            round = _currentRound,
            indexInRound = _currentExerciseIndex,
            totalInRound = workoutConfig.exercises.size,
            maxRepsThisSession = null  // No limit in sequential mode
        )
        
        _currentExercise.value = loadedExercise
        updateProgress()
        
        Log.d(TAG, "Sequential: Loaded ${exerciseConfig.name.en}")
        
        onExerciseReady?.invoke(loadedExercise)
    }
    
    private fun handleRoundComplete() {
        Log.d(TAG, "Round $_currentRound completed")
        
        onRoundCompleted?.invoke(_currentRound, workoutConfig.rounds)
        
        if (_currentRound >= workoutConfig.rounds) {
            handleWorkoutComplete()
        } else {
            _currentRound++
            _currentExerciseIndex = 0
            startRoundRest()
        }
    }
    
    private fun handleWorkoutComplete() {
        Log.d(TAG, "Workout completed!")
        
        _state.value = WorkoutState.COMPLETED
        _isCompleted.value = true
        _currentExercise.value = null
        updateProgress()
        
        val result = WorkoutResult(
            workoutName = workoutConfig.name.en,
            totalRounds = workoutConfig.rounds,
            completedRounds = _currentRound,
            totalExercises = _completedExercisesTotal,
            exerciseResults = _exerciseResults.toList(),
            totalDurationMs = System.currentTimeMillis() - _startTime,
            startTime = _startTime,
            endTime = System.currentTimeMillis()
        )
        
        onWorkoutCompleted?.invoke(result)
    }
    
    private fun startExerciseRest() {
        _state.value = WorkoutState.RESTING
        _restTimeRemainingMs.value = workoutConfig.restBetweenExercisesMs
        updateProgress()
        
        Log.d(TAG, "Rest between exercises: ${workoutConfig.restBetweenExercisesMs}ms")
        onRestStarted?.invoke(workoutConfig.restBetweenExercisesMs, false)
    }
    
    private fun startRoundRest() {
        _state.value = WorkoutState.ROUND_REST
        _restTimeRemainingMs.value = workoutConfig.restBetweenRoundsMs
        updateProgress()
        
        Log.d(TAG, "Rest between rounds: ${workoutConfig.restBetweenRoundsMs}ms")
        onRestStarted?.invoke(workoutConfig.restBetweenRoundsMs, true)
    }
    
    private fun updateProgress() {
        _progress.value = createProgress()
    }
    
    private fun createInitialProgress(): WorkoutProgress {
        return WorkoutProgress(
            currentRound = 1,
            totalRounds = workoutConfig.rounds,
            currentExerciseIndex = 0,
            totalExercises = workoutConfig.exercises.size,
            completedExercises = 0,
            isResting = false,
            isCompleted = false,
            isAlternating = workoutConfig.isAlternating()
        )
    }
    
    private fun createProgress(): WorkoutProgress {
        val currentName = _currentExercise.value?.getDisplayName() ?: ""
        
        return WorkoutProgress(
            currentRound = _currentRound,
            totalRounds = workoutConfig.rounds,
            currentExerciseIndex = _currentExerciseIndex,
            totalExercises = workoutConfig.exercises.size,
            completedExercises = _completedExercisesTotal,
            isResting = _state.value == WorkoutState.RESTING || _state.value == WorkoutState.ROUND_REST,
            isCompleted = _isCompleted.value,
            // Alternating mode fields
            isAlternating = isAlternatingMode,
            exerciseRepsCompleted = _exerciseRepsCompleted.toMap(),
            exerciseRepsTargets = _exerciseRepsTargets.toMap(),
            currentExerciseName = currentName,
            totalRepsCompleted = _totalRepsCompleted,
            totalRepsTarget = _totalRepsInRound
        )
    }
}

/**
 * Loaded exercise with all resolved values
 * Ready to be passed to TrainingEngine
 * 
 * @param maxRepsThisSession In alternating mode, the max reps for this session
 *                           (null = no limit, complete full target)
 */
data class LoadedExercise(
    val config: ExerciseConfig,
    val workoutExercise: WorkoutExercise,
    val round: Int,
    val indexInRound: Int,
    val totalInRound: Int,
    val maxRepsThisSession: Int? = null  // For alternating mode
) {
    /**
     * Get the target reps (from workout override or exercise default)
     */
    fun getTargetReps(): Int? {
        return workoutExercise.target.reps
            ?: config.repCountingConfig.reps
    }
    
    /**
     * Get the effective target for this session
     * In alternating mode, this is min(targetReps, maxRepsThisSession)
     */
    fun getEffectiveTargetReps(): Int {
        val fullTarget = getTargetReps() ?: 10
        return maxRepsThisSession ?: fullTarget
    }
    
    /**
     * Get the target duration in seconds (from workout override or exercise default)
     */
    fun getTargetDurationSec(): Int? {
        return workoutExercise.target.durationSec
            ?: config.repCountingConfig.duration
    }
    
    /**
     * Check if this is a hold exercise
     */
    fun isHoldExercise(): Boolean = config.countingMethod == CountingMethod.HOLD
    
    /**
     * Check if this session has a rep limit (alternating mode)
     */
    fun hasRepLimit(): Boolean = maxRepsThisSession != null
    
    /**
     * Get display name
     */
    fun getDisplayName(language: String = "en"): String = config.name.get(language)
    
    /**
     * Get variant name
     */
    fun getVariantName(language: String = "en"): String? {
        return config.getPoseVariant(workoutExercise.variantIndex)?.name?.get(language)
    }
}

/**
 * Workout info for display
 */
data class WorkoutInfo(
    val name: LocalizedText,
    val description: LocalizedText?,
    val type: WorkoutType,
    val totalExercises: Int,
    val totalRounds: Int,
    val estimatedDurationMs: Long,
    val isAlternating: Boolean = false,
    val repsPerSwitch: Int? = null
) {
    fun getEstimatedDurationMinutes(): Int = (estimatedDurationMs / 60000).toInt()
}
