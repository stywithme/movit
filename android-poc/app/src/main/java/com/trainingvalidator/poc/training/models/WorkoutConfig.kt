package com.trainingvalidator.poc.training.models

import com.google.gson.annotations.SerializedName

/**
 * WorkoutConfig - Complete workout/set configuration from JSON
 * 
 * A workout is a sequence of exercises with optional rest periods.
 * Supports multiple rounds (circuits), different exercise types (reps/hold),
 * and target overrides per exercise.
 * 
 * Use cases:
 * - Alternating exercises (left arm, then right arm)
 * - Circuit training (multiple exercises in sequence)
 * - Super sets (pairs of exercises)
 * - Mixed workouts (combining Hold + Up&Down + Push&Pull)
 */
data class WorkoutConfig(
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val type: WorkoutType = WorkoutType.CIRCUIT,
    val executionMode: ExecutionMode = ExecutionMode.SEQUENTIAL,  // NEW: sequential or alternating
    val repsPerSwitch: Int = 0,           // NEW: 0 = complete exercise, 1+ = switch after N reps
    val rounds: Int = 1,
    val restBetweenExercisesMs: Long = 10000,  // 10 seconds default
    val restBetweenRoundsMs: Long = 60000,     // 60 seconds default
    val restBetweenSwitchMs: Long = 0,         // NEW: rest between alternating switches (0 = no rest)
    val exercises: List<WorkoutExercise> = emptyList(),
    // Runtime field - set by WorkoutLoader
    @Transient
    var fileName: String = ""
) {
    /**
     * Get total number of exercises across all rounds
     */
    fun getTotalExerciseCount(): Int = exercises.size * rounds
    
    /**
     * Get exercise by index (within a single round)
     */
    fun getExercise(index: Int): WorkoutExercise? = exercises.getOrNull(index)
    
    /**
     * Check if workout is valid (has at least one exercise)
     */
    fun isValid(): Boolean = exercises.isNotEmpty()
    
    /**
     * Get estimated total duration in milliseconds (rough estimate)
     * Based on average rep time and rest periods
     */
    fun getEstimatedDurationMs(): Long {
        val exerciseDuration = exercises.sumOf { exercise ->
            when {
                exercise.target.durationSec != null -> exercise.target.durationSec * 1000L
                exercise.target.reps != null -> exercise.target.reps * 3000L // ~3 sec per rep estimate
                else -> 30000L // default 30 sec
            }
        }
        val restDuration = (exercises.size - 1) * restBetweenExercisesMs
        val roundRestDuration = (rounds - 1) * restBetweenRoundsMs
        
        return (exerciseDuration + restDuration) * rounds + roundRestDuration
    }
    
    /**
     * Check if this workout uses alternating execution mode
     */
    fun isAlternating(): Boolean = executionMode == ExecutionMode.ALTERNATING
    
    /**
     * Get effective reps per switch
     * Returns 1 if alternating mode with repsPerSwitch <= 0
     * Returns Int.MAX_VALUE if sequential mode (complete full exercise)
     */
    fun getEffectiveRepsPerSwitch(): Int {
        return when {
            executionMode == ExecutionMode.SEQUENTIAL -> Int.MAX_VALUE
            repsPerSwitch <= 0 -> 1  // Default to 1 rep per switch in alternating mode
            else -> repsPerSwitch
        }
    }
    
    /**
     * Get total reps needed across all exercises for one complete round
     * Used in alternating mode to track progress
     */
    fun getTotalRepsInRound(): Int {
        return exercises.sumOf { it.target.reps ?: 10 }
    }
}

/**
 * Workout type - determines the training style
 */
enum class WorkoutType {
    @SerializedName("circuit")
    CIRCUIT,        // Complete all exercises, then repeat for rounds
    
    @SerializedName("super_set")
    SUPER_SET,      // Pairs of exercises with minimal rest between pair
    
    @SerializedName("amrap")
    AMRAP,          // As Many Rounds As Possible (timed workout)
    
    @SerializedName("emom")
    EMOM            // Every Minute On the Minute
}

/**
 * Execution mode - determines how exercises are executed within a round
 * 
 * SEQUENTIAL: Complete all reps of Exercise 1, then all reps of Exercise 2, etc.
 *   Ex1 (10 reps) → Rest → Ex2 (10 reps) → Rest → Ex3 (10 reps)
 * 
 * ALTERNATING: Alternate between exercises based on repsPerSwitch
 *   repsPerSwitch=1: Ex1 (1 rep) → Ex2 (1 rep) → Ex3 (1 rep) → Ex1 (1 rep) → ...
 *   repsPerSwitch=3: Ex1 (3 reps) → Ex2 (3 reps) → Ex1 (3 reps) → ...
 */
enum class ExecutionMode {
    @SerializedName("sequential")
    SEQUENTIAL,     // Complete each exercise fully before moving to next (default)
    
    @SerializedName("alternating")
    ALTERNATING     // Alternate between exercises based on repsPerSwitch
}

/**
 * Single exercise within a workout
 * 
 * @param exercise The exercise file name (without .json extension)
 * @param variantIndex Which pose variant to use (default 0)
 * @param target Target reps or duration (overrides exercise default)
 */
data class WorkoutExercise(
    val exercise: String,                           // Exercise file name
    val variantIndex: Int = 0,                      // Pose variant index
    val target: ExerciseTarget = ExerciseTarget(),  // Target reps or duration
    val notes: LocalizedText? = null                // Optional notes/tips for this exercise in workout
) {
    /**
     * Check if this exercise has a valid target
     */
    fun hasValidTarget(): Boolean = target.reps != null || target.durationSec != null
}

/**
 * Target for an exercise - either reps or duration
 * 
 * For rep-based exercises (up_down, push_pull): use reps
 * For hold exercises: use durationSec
 * If neither specified, falls back to exercise JSON defaults
 */
data class ExerciseTarget(
    val reps: Int? = null,
    val durationSec: Int? = null
) {
    /**
     * Check if this is a time-based target
     */
    fun isTimeBased(): Boolean = durationSec != null
    
    /**
     * Check if this is a rep-based target
     */
    fun isRepBased(): Boolean = reps != null
    
    /**
     * Get duration in milliseconds (for hold exercises)
     */
    fun getDurationMs(): Long? = durationSec?.let { it * 1000L }
}

/**
 * Workout progress tracking
 * 
 * Supports both sequential and alternating modes:
 * - Sequential: tracks which exercise is current
 * - Alternating: tracks reps completed per exercise
 */
data class WorkoutProgress(
    val currentRound: Int = 1,
    val totalRounds: Int = 1,
    val currentExerciseIndex: Int = 0,
    val totalExercises: Int = 0,
    val completedExercises: Int = 0,
    val isResting: Boolean = false,
    val isCompleted: Boolean = false,
    // Alternating mode fields
    val isAlternating: Boolean = false,
    val exerciseRepsCompleted: Map<Int, Int> = emptyMap(),  // exerciseIndex -> reps done
    val exerciseRepsTargets: Map<Int, Int> = emptyMap(),    // exerciseIndex -> target reps
    val currentExerciseName: String = "",
    val totalRepsCompleted: Int = 0,
    val totalRepsTarget: Int = 0
) {
    /**
     * Get overall progress percentage (0.0 - 1.0)
     */
    fun getOverallProgress(): Float {
        return if (isAlternating) {
            // In alternating mode, use total reps progress
            if (totalRepsTarget == 0) 0f
            else (totalRepsCompleted.toFloat() / totalRepsTarget.toFloat() / totalRounds)
                .coerceIn(0f, 1f)
        } else {
            // Sequential mode: use exercise count
            val totalItems = totalExercises * totalRounds
            if (totalItems == 0) 0f
            else completedExercises.toFloat() / totalItems.toFloat()
        }
    }
    
    /**
     * Get current round progress percentage (0.0 - 1.0)
     */
    fun getRoundProgress(): Float {
        return if (isAlternating) {
            if (totalRepsTarget == 0) 0f
            else totalRepsCompleted.toFloat() / totalRepsTarget.toFloat()
        } else {
            if (totalExercises == 0) 0f
            else currentExerciseIndex.toFloat() / totalExercises.toFloat()
        }
    }
    
    /**
     * Get display string for current position
     * Sequential: "Exercise 2/5 • Round 1/3"
     * Alternating: "Bicep Curl Left (3/10) • Round 1/3"
     */
    fun getPositionDisplay(): String {
        return if (isAlternating) {
            val currentReps = exerciseRepsCompleted[currentExerciseIndex] ?: 0
            val targetReps = exerciseRepsTargets[currentExerciseIndex] ?: 0
            "$currentExerciseName ($currentReps/$targetReps) • Round $currentRound/$totalRounds"
        } else {
            "Exercise ${currentExerciseIndex + 1}/$totalExercises • Round $currentRound/$totalRounds"
        }
    }
    
    /**
     * Get reps remaining for current exercise (alternating mode)
     */
    fun getCurrentExerciseRepsRemaining(): Int {
        val completed = exerciseRepsCompleted[currentExerciseIndex] ?: 0
        val target = exerciseRepsTargets[currentExerciseIndex] ?: 0
        return (target - completed).coerceAtLeast(0)
    }
}

/**
 * Workout state enum
 */
enum class WorkoutState {
    IDLE,           // Not started
    PREPARING,      // Countdown before exercise
    EXERCISING,     // Currently doing exercise
    RESTING,        // Rest between exercises
    ROUND_REST,     // Rest between rounds
    COMPLETED,      // Workout finished
    PAUSED          // User paused
}

/**
 * Result of a completed workout
 */
data class WorkoutResult(
    val workoutName: String,
    val totalRounds: Int,
    val completedRounds: Int,
    val totalExercises: Int,
    val exerciseResults: List<WorkoutExerciseResult>,
    val totalDurationMs: Long,
    val startTime: Long,
    val endTime: Long
) {
    /**
     * Get overall accuracy across all exercises
     */
    fun getOverallAccuracy(): Float {
        if (exerciseResults.isEmpty()) return 0f
        return exerciseResults.map { it.accuracy }.average().toFloat()
    }
}

/**
 * Result of a single exercise within a workout
 */
data class WorkoutExerciseResult(
    val exerciseName: String,
    val round: Int,
    val targetReps: Int?,
    val completedReps: Int?,
    val targetDurationMs: Long?,
    val actualDurationMs: Long?,
    val accuracy: Float,
    val isCompleted: Boolean
)
