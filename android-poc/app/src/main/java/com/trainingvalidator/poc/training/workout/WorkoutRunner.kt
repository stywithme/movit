package com.trainingvalidator.poc.training.workout

import android.util.Log
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WorkoutRunner - Orchestrates a simple sequential workout
 *
 * The workout is a sequence of exercises. Each exercise can have multiple sets.
 * WorkoutActivity launches TrainingActivity for each set, showing rest between sets/exercises.
 */
class WorkoutRunner(
    private val workoutConfig: WorkoutConfig,
    private val exerciseRepository: ExerciseRepository
) {
    
    companion object {
        private const val TAG = "WorkoutRunner"
    }
    
    // ==================== State ====================
    
    private var _currentExerciseIndex = 0
    private var _currentSetIndex = 1
    private var _completedSetsTotal = 0
    private var _startTime: Long = 0L
    private var _exerciseResults = mutableListOf<WorkoutExerciseResult>()
    
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
     * Called when a new set is ready to start
     */
    var onExerciseReady: ((LoadedExercise) -> Unit)? = null
    
    /**
     * Called when rest period starts
     */
    var onRestStarted: ((restDurationMs: Long, isRoundRest: Boolean) -> Unit)? = null
    
    /**
     * Called when workout is completed
     */
    var onWorkoutCompleted: ((WorkoutResult) -> Unit)? = null
    
    // ==================== Public API ====================
    
    /**
     * Start the workout
     * Loads the first exercise and notifies listeners
     */
    fun start() {
        Log.d(TAG, "Starting workout: ${workoutConfig.name.en}")
        
        _currentExerciseIndex = 0
        _currentSetIndex = 1
        _completedSetsTotal = 0
        _startTime = System.currentTimeMillis()
        _exerciseResults.clear()
        _isCompleted.value = false
        
        _state.value = WorkoutState.PREPARING
        updateProgress()
        
        loadCurrentExercise()
    }
    
    /**
     * Called when the current exercise session is completed
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
        handleSetCompleted(completedReps, actualDurationMs, accuracy, isCompleted)
    }
    
    /**
     * Called when rest period is finished
     * UI should call this after countdown
     */
    fun onRestCompleted() {
        _restTimeRemainingMs.value = null
        
        when (_state.value) {
            WorkoutState.RESTING -> {
                // Continue with next set/exercise
                _state.value = WorkoutState.PREPARING
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
        if (_state.value == WorkoutState.RESTING) {
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
            difficulty = workoutConfig.difficulty,
            totalExercises = workoutConfig.exercises.size,
            totalSets = workoutConfig.exercises.sumOf { it.sets.coerceAtLeast(1) },
            estimatedDurationMs = workoutConfig.getEstimatedDurationMs()
        )
    }
    
    // ==================== Private Methods ====================

    private fun handleSetCompleted(
        completedReps: Int?,
        actualDurationMs: Long?,
        accuracy: Float,
        isCompleted: Boolean
    ) {
        val currentWorkoutExercise = workoutConfig.getExercise(_currentExerciseIndex)
        val totalSetsForExercise = currentWorkoutExercise?.sets?.coerceAtLeast(1) ?: 1

        _exerciseResults.add(
            WorkoutExerciseResult(
                exerciseName = currentWorkoutExercise?.exercise ?: "unknown",
                setNumber = _currentSetIndex,
                targetReps = currentWorkoutExercise?.targetReps,
                completedReps = completedReps,
                targetDurationMs = currentWorkoutExercise?.targetDurationSec?.let { it * 1000L },
                actualDurationMs = actualDurationMs,
                accuracy = accuracy,
                isCompleted = isCompleted
            )
        )

        _completedSetsTotal++

        Log.d(
            TAG,
            "Set completed: ${currentWorkoutExercise?.exercise} " +
                "(Set $_currentSetIndex/$totalSetsForExercise, Index $_currentExerciseIndex)"
        )

        val hasMoreSets = _currentSetIndex < totalSetsForExercise
        if (hasMoreSets) {
            _currentSetIndex++
            startRest(currentWorkoutExercise?.restBetweenSetsMs ?: 0L)
            return
        }

        // Move to next exercise
        _currentExerciseIndex++
        _currentSetIndex = 1

        if (_currentExerciseIndex >= workoutConfig.exercises.size) {
            handleWorkoutComplete()
        } else {
            val restMs = currentWorkoutExercise?.restAfterExerciseMs ?: 0L
            startRest(restMs)
        }
    }

    private fun loadCurrentExercise() {
        val workoutExercise = workoutConfig.getExercise(_currentExerciseIndex)

        if (workoutExercise == null) {
            Log.e(TAG, "No exercise at index $_currentExerciseIndex")
            return
        }

        val exerciseConfig = exerciseRepository.getExercise(workoutExercise.exercise)

        if (exerciseConfig == null) {
            Log.e(TAG, "Exercise not found in repository: ${workoutExercise.exercise}")
            onExerciseCompleted(isCompleted = false)
            return
        }

        val totalSetsForExercise = workoutExercise.sets.coerceAtLeast(1)
        val loadedExercise = LoadedExercise(
            config = exerciseConfig,
            workoutExercise = workoutExercise,
            index = _currentExerciseIndex,
            total = workoutConfig.exercises.size,
            setIndex = _currentSetIndex,
            totalSets = totalSetsForExercise
        )

        _currentExercise.value = loadedExercise
        updateProgress()

        Log.d(TAG, "Loaded ${exerciseConfig.name.en} (Set $_currentSetIndex/$totalSetsForExercise)")

        onExerciseReady?.invoke(loadedExercise)
    }

    private fun startRest(durationMs: Long) {
        if (durationMs <= 0L) {
            _state.value = WorkoutState.PREPARING
            loadCurrentExercise()
            return
        }

        _state.value = WorkoutState.RESTING
        _restTimeRemainingMs.value = durationMs
        updateProgress()

        Log.d(TAG, "Rest started: ${durationMs}ms")
        onRestStarted?.invoke(durationMs, false)
    }

    private fun handleWorkoutComplete() {
        Log.d(TAG, "Workout completed!")

        _state.value = WorkoutState.COMPLETED
        _isCompleted.value = true
        _currentExercise.value = null
        updateProgress()

        val result = WorkoutResult(
            workoutName = workoutConfig.name.en,
            totalExercises = workoutConfig.exercises.size,
            totalSets = workoutConfig.exercises.sumOf { it.sets.coerceAtLeast(1) },
            exerciseResults = _exerciseResults.toList(),
            totalDurationMs = System.currentTimeMillis() - _startTime,
            startTime = _startTime,
            endTime = System.currentTimeMillis()
        )

        onWorkoutCompleted?.invoke(result)
    }

    private fun updateProgress() {
        _progress.value = createProgress()
    }

    private fun createInitialProgress(): WorkoutProgress {
        return WorkoutProgress(
            currentExerciseIndex = 0,
            totalExercises = workoutConfig.exercises.size,
            currentSetIndex = 1,
            totalSetsForExercise = 1,
            completedSets = 0,
            totalSets = workoutConfig.exercises.sumOf { it.sets.coerceAtLeast(1) },
            isResting = false,
            isCompleted = false,
            currentExerciseName = ""
        )
    }

    private fun createProgress(): WorkoutProgress {
        val currentName = _currentExercise.value?.getDisplayName() ?: ""
        val currentExercise = workoutConfig.getExercise(_currentExerciseIndex)
        val totalSetsForExercise = currentExercise?.sets?.coerceAtLeast(1) ?: 1

        return WorkoutProgress(
            currentExerciseIndex = _currentExerciseIndex,
            totalExercises = workoutConfig.exercises.size,
            currentSetIndex = _currentSetIndex,
            totalSetsForExercise = totalSetsForExercise,
            completedSets = _completedSetsTotal,
            totalSets = workoutConfig.exercises.sumOf { it.sets.coerceAtLeast(1) },
            isResting = _state.value == WorkoutState.RESTING,
            isCompleted = _isCompleted.value,
            currentExerciseName = currentName
        )
    }
}

/**
 * Loaded exercise with all resolved values
 */
data class LoadedExercise(
    val config: ExerciseConfig,
    val workoutExercise: WorkoutExercise,
    val index: Int,
    val total: Int,
    val setIndex: Int,
    val totalSets: Int
) {
    /**
     * Get the target reps (from workout override or exercise default)
     */
    fun getTargetReps(): Int? {
        return workoutExercise.targetReps
            ?: config.repCountingConfig.reps
    }

    /**
     * Get the target duration in seconds (from workout override or exercise default)
     */
    fun getTargetDurationSec(): Int? {
        return workoutExercise.targetDurationSec
            ?: config.repCountingConfig.duration
    }

    /**
     * Check if this is a hold exercise
     */
    fun isHoldExercise(): Boolean = config.countingMethod == CountingMethod.HOLD

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
    val difficulty: String,
    val totalExercises: Int,
    val totalSets: Int,
    val estimatedDurationMs: Long
) {
    fun getEstimatedDurationMinutes(): Int = (estimatedDurationMs / 60000).toInt()
}
