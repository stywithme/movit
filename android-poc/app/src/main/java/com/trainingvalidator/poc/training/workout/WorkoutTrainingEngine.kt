package com.trainingvalidator.poc.training.workout

import android.util.Log
import com.trainingvalidator.poc.training.TrainingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WorkoutTrainingEngine - Hot-swap engine (legacy)
 *
 * This engine is kept for compatibility with the legacy Workout Mode.
 * The simplified workout flow is now handled by WorkoutRunner.
 */
class WorkoutTrainingEngine(
    private val exercises: List<LoadedExercise>
) {
    companion object {
        private const val TAG = "WorkoutTrainingEngine"
    }

    private var currentExerciseIndex = 0
    private var currentEngine: TrainingEngine? = null
    private val _exerciseRepsCompleted = mutableMapOf<Int, Int>()
    private val _exerciseRepsTargets = mutableMapOf<Int, Int>()
    private var _totalRepsCompleted = 0
    private var _totalCorrectReps = 0

    private val _currentExercise = MutableStateFlow<LoadedExercise?>(null)
    val currentExercise: StateFlow<LoadedExercise?> = _currentExercise

    private val _isWorkoutCompleted = MutableStateFlow(false)
    val isWorkoutCompleted: StateFlow<Boolean> = _isWorkoutCompleted

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching

    private val _switchInfo = MutableStateFlow<SwitchInfo?>(null)
    val switchInfo: StateFlow<SwitchInfo?> = _switchInfo

    var onExerciseSwitched: ((fromExercise: String, toExercise: String, repsThisSession: Int) -> Unit)? = null
    var onWorkoutCompleted: ((totalReps: Int, rounds: Int) -> Unit)? = null
    var onRoundCompleted: ((roundNumber: Int, totalRounds: Int) -> Unit)? = null

    init {
        exercises.forEachIndexed { index, exercise ->
            val targetReps = exercise.getTargetReps() ?: 10
            _exerciseRepsTargets[index] = targetReps
            _exerciseRepsCompleted[index] = 0
        }

        Log.d(TAG, "WorkoutTrainingEngine initialized with ${exercises.size} exercises")
    }

    fun getEngine(): TrainingEngine? = currentEngine

    fun start(): TrainingEngine {
        currentExerciseIndex = 0
        _totalRepsCompleted = 0
        _exerciseRepsCompleted.keys.forEach { _exerciseRepsCompleted[it] = 0 }

        val exercise = exercises[currentExerciseIndex]
        _currentExercise.value = exercise

        currentEngine = createEngine(exercise)
        Log.d(TAG, "Started with exercise: ${exercise.getDisplayName()}")
        return currentEngine!!
    }

    /**
     * Legacy workout mode does not auto-limit reps per session.
     */
    fun getRepsForCurrentSession(): Int = Int.MAX_VALUE

    fun onRepsCompleted(reps: Int, correctReps: Int = reps): SwitchResult {
        val currentCompleted = _exerciseRepsCompleted[currentExerciseIndex] ?: 0
        val newCompleted = currentCompleted + reps
        _exerciseRepsCompleted[currentExerciseIndex] = newCompleted
        _totalRepsCompleted += reps
        _totalCorrectReps += correctReps

        val target = _exerciseRepsTargets[currentExerciseIndex] ?: 0
        Log.d(TAG, "Exercise $currentExerciseIndex: $newCompleted/$target reps completed")

        if (newCompleted >= target) {
            return handleExerciseComplete()
        }

        return SwitchResult.Continue
    }

    fun getOverallAccuracy(): Float {
        if (_totalRepsCompleted == 0) return 100f
        return (_totalCorrectReps.toFloat() / _totalRepsCompleted.toFloat()) * 100f
    }

    fun switchToNextExercise(): TrainingEngine? {
        val previousExercise = _currentExercise.value
        val previousName = previousExercise?.getDisplayName() ?: ""

        val nextIndex = currentExerciseIndex + 1
        if (nextIndex >= exercises.size) {
            completeWorkout()
            return null
        }

        currentExerciseIndex = nextIndex
        val nextExercise = exercises[currentExerciseIndex]

        _isSwitching.value = true
        _switchInfo.value = SwitchInfo(
            fromExercise = previousName,
            toExercise = nextExercise.getDisplayName(),
            repsThisSession = getRepsForCurrentSession()
        )

        currentEngine?.stop()
        _currentExercise.value = nextExercise
        currentEngine = createEngine(nextExercise)

        Log.d(TAG, "Hot-swapped from $previousName to ${nextExercise.getDisplayName()}")
        onExerciseSwitched?.invoke(previousName, nextExercise.getDisplayName(), getRepsForCurrentSession())
        _isSwitching.value = false

        return currentEngine
    }

    fun switchToExercise(index: Int): TrainingEngine? {
        if (index < 0 || index >= exercises.size) return null
        currentExerciseIndex = index - 1
        return switchToNextExercise()
    }

    fun getProgressInfo(): WorkoutProgressInfo {
        val totalRepsTarget = _exerciseRepsTargets.values.sum()
        return WorkoutProgressInfo(
            currentExerciseIndex = currentExerciseIndex,
            totalExercises = exercises.size,
            totalRepsCompleted = _totalRepsCompleted,
            totalRepsTarget = totalRepsTarget,
            totalRounds = 1
        )
    }

    fun getCurrentExerciseName(): String = _currentExercise.value?.getDisplayName() ?: ""

    fun stop() {
        currentEngine?.stop()
    }

    private fun handleExerciseComplete(): SwitchResult {
        val nextIndex = currentExerciseIndex + 1
        return if (nextIndex >= exercises.size) {
            completeWorkout()
            SwitchResult.WorkoutComplete(_totalRepsCompleted)
        } else {
            SwitchResult.SwitchNow(exercises[nextIndex].getDisplayName(), getRepsForCurrentSession())
        }
    }

    private fun completeWorkout() {
        _isWorkoutCompleted.value = true
        onWorkoutCompleted?.invoke(_totalRepsCompleted, 1)
    }

    private fun createEngine(exercise: LoadedExercise): TrainingEngine {
        return TrainingEngine(
            exerciseConfig = exercise.config,
            poseVariantIndex = exercise.workoutExercise.variantIndex,
            targetRepsOverride = exercise.getTargetReps(),
            targetDurationMsOverride = exercise.getTargetDurationSec()?.let { it * 1000L }
        )
    }
}

/**
 * Switch info for UI animation
 */
data class SwitchInfo(
    val fromExercise: String,
    val toExercise: String,
    val repsThisSession: Int
)

/**
 * Result of a completed session segment
 */
sealed class SwitchResult {
    object Continue : SwitchResult()

    data class SwitchNow(
        val nextExerciseName: String,
        val repsThisSession: Int
    ) : SwitchResult()

    data class RoundComplete(
        val roundNumber: Int,
        val totalRounds: Int
    ) : SwitchResult()

    data class WorkoutComplete(
        val totalReps: Int
    ) : SwitchResult()
}

/**
 * Workout progress for UI
 */
data class WorkoutProgressInfo(
    val currentExerciseIndex: Int,
    val totalExercises: Int,
    val totalRepsCompleted: Int,
    val totalRepsTarget: Int,
    val totalRounds: Int
)
