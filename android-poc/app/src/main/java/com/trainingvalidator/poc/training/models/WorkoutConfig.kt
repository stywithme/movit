package com.trainingvalidator.poc.training.models

import com.google.gson.annotations.SerializedName

/**
 * WorkoutConfig - Simplified workout template
 *
 * A workout is a simple sequence of exercises with optional rest.
 * Sets are defined per exercise (not per workout).
 */
data class WorkoutConfig(
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val coverImageUrl: String? = null,
    val levelId: String? = null,
    val level: WorkoutLevelConfig? = null,
    val estimatedDurationMin: Int? = null,
    val tags: List<String> = emptyList(),
    val exercises: List<WorkoutExercise> = emptyList(),
    val phases: List<WorkoutPhaseConfig> = emptyList(),
    // Runtime field - set by WorkoutLoader
    @Transient
    var fileName: String = ""
) {
    fun effectiveExercises(): List<WorkoutExercise> =
        if (phases.isNotEmpty()) phases.sortedBy { it.sortOrder }.flatMap { it.exercises } else exercises

    /**
     * Get total number of exercises in this workout
     */
    fun getTotalExerciseCount(): Int = effectiveExercises().size
    
    /**
     * Get exercise by index (within a single round)
     */
    fun getExercise(index: Int): WorkoutExercise? = effectiveExercises().getOrNull(index)
    
    /**
     * Check if workout is valid (has at least one exercise)
     */
    fun isValid(): Boolean = effectiveExercises().isNotEmpty()
    
    /**
     * Get estimated total duration in milliseconds (rough estimate)
     * Based on average rep time and rest periods per exercise
     */
    fun getEstimatedDurationMs(): Long {
        val items = effectiveExercises()
        val exerciseDuration = items.sumOf { exercise ->
            val perSetDuration = when {
                exercise.targetDurationSec != null -> exercise.targetDurationSec * 1000L
                exercise.targetReps != null -> exercise.targetReps * 3000L
                else -> 30000L
            }
            val sets = exercise.sets.coerceAtLeast(1)
            val restBetweenSets = (sets - 1) * exercise.restBetweenSetsMs
            (perSetDuration * sets) + restBetweenSets
        }
        val restDuration = items.sumOf { it.restAfterExerciseMs }
        return exerciseDuration + restDuration
    }
}

data class WorkoutLevelConfig(
    val id: String,
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedText = LocalizedText()
)

data class WorkoutPhaseConfig(
    val id: String? = null,
    val phaseId: String? = null,
    val slug: String? = null,
    val role: String = "MAIN",
    val name: LocalizedText = LocalizedText(en = "Phase", ar = "مرحلة"),
    val description: LocalizedText? = null,
    val canSkip: Boolean = false,
    val canContinue: Boolean = true,
    val maxContinueTimeMs: Long? = null,
    val sortOrder: Int = 0,
    val exercises: List<WorkoutExercise> = emptyList()
)

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
    val targetReps: Int? = null,
    val targetRepsPerSet: List<Int>? = null,
    @SerializedName("targetDuration")
    val targetDurationSec: Int? = null,
    val sets: Int = 1,
    val restBetweenSetsMs: Long = 30000,
    val restBetweenSetsPerSetMs: List<Long>? = null,
    val restAfterExerciseMs: Long = 60000,
    val weightPerSet: List<Float>? = null,
    val notes: LocalizedText? = null                // Optional notes/tips for this exercise in workout
) {
    fun expandedRepsPerSet(): List<Int>? =
        PerSetValues.expandInts(targetRepsPerSet, sets, targetReps)

    fun expandedRestBetweenSetsMs(): List<Long>? =
        PerSetValues.expandLongs(restBetweenSetsPerSetMs, sets, restBetweenSetsMs)

    fun expandedWeightPerSet(): List<Float>? =
        PerSetValues.expandFloats(weightPerSet, sets)

    /**
     * Check if this exercise has a valid target
     */
    fun hasValidTarget(): Boolean =
        targetReps != null ||
            !targetRepsPerSet.isNullOrEmpty() ||
            targetDurationSec != null
}

/**
 * Workout progress tracking
 * 
 * Supports both sequential and alternating modes:
 * - Sequential: tracks which exercise is current
 * - Alternating: tracks reps completed per exercise
 */
data class WorkoutProgress(
    val currentExerciseIndex: Int = 0,
    val totalExercises: Int = 0,
    val currentSetIndex: Int = 1,
    val totalSetsForExercise: Int = 1,
    val completedSets: Int = 0,
    val totalSets: Int = 0,
    val isResting: Boolean = false,
    val isCompleted: Boolean = false,
    val currentExerciseName: String = ""
) {
    /**
     * Get overall progress percentage (0.0 - 1.0)
     */
    fun getOverallProgress(): Float {
        if (totalSets == 0) return 0f
        return completedSets.toFloat() / totalSets.toFloat()
    }

    /**
     * Get display string for current position
     */
    fun getPositionDisplay(): String {
        return "Exercise ${currentExerciseIndex + 1}/$totalExercises • Set $currentSetIndex/$totalSetsForExercise"
    }
}

/**
 * Workout state enum
 */
enum class WorkoutState {
    IDLE,           // Not started
    PREPARING,      // Countdown before exercise
    EXERCISING,     // Currently doing exercise
    RESTING,        // Rest between sets/exercises
    COMPLETED,      // Workout finished
    PAUSED          // User paused
}

/**
 * Result of a completed workout
 */
data class WorkoutResult(
    val workoutName: String,
    val totalExercises: Int,
    val totalSets: Int,
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
    val setNumber: Int,
    val targetReps: Int?,
    val completedReps: Int?,
    val targetDurationMs: Long?,
    val actualDurationMs: Long?,
    val accuracy: Float,
    val isCompleted: Boolean
)
