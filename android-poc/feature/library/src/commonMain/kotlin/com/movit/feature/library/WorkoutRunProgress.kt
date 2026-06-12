package com.movit.feature.library

data class WorkoutRunProgress(
    val exerciseIndex: Int = 0,
    val currentSet: Int = 1,
)

sealed interface WorkoutRunPostNav {
    data object BackToRun : WorkoutRunPostNav
    data object Complete : WorkoutRunPostNav
    data class Rest(
        val restingExerciseId: String,
        val upNextExerciseId: String,
        val restSeconds: Int,
        val nextExerciseIndex: Int,
    ) : WorkoutRunPostNav
}

object WorkoutRunProgressStore {
    private val byWorkoutId = mutableMapOf<String, WorkoutRunProgress>()

    fun read(workoutId: String): WorkoutRunProgress = byWorkoutId[workoutId] ?: WorkoutRunProgress()

    fun write(workoutId: String, progress: WorkoutRunProgress) {
        byWorkoutId[workoutId] = progress
    }

    fun clear(workoutId: String) {
        byWorkoutId.remove(workoutId)
    }

    fun advanceAfterExercise(
        workoutId: String,
        config: WorkoutFlowConfigUi,
        completedExerciseIndex: Int,
    ): WorkoutRunPostNav {
        val progress = read(workoutId)
        val exercise = config.exercises.getOrNull(completedExerciseIndex) ?: return WorkoutRunPostNav.BackToRun
        val sets = exercise.sets.coerceAtLeast(1)
        val completedSet = progress.currentSet.coerceAtLeast(1)

        if (completedExerciseIndex != progress.exerciseIndex) {
            write(workoutId, WorkoutRunProgress(exerciseIndex = completedExerciseIndex, currentSet = 1))
        }

        if (completedSet < sets) {
            val next = WorkoutRunProgress(
                exerciseIndex = completedExerciseIndex,
                currentSet = completedSet + 1,
            )
            write(workoutId, next)
            return WorkoutRunPostNav.Rest(
                restingExerciseId = exercise.id,
                upNextExerciseId = exercise.id,
                restSeconds = config.restBetweenSetsSeconds,
                nextExerciseIndex = completedExerciseIndex,
            )
        }

        if (completedExerciseIndex < config.exercises.lastIndex) {
            val nextExercise = config.exercises[completedExerciseIndex + 1]
            write(
                workoutId,
                WorkoutRunProgress(
                    exerciseIndex = completedExerciseIndex + 1,
                    currentSet = 1,
                ),
            )
            return WorkoutRunPostNav.Rest(
                restingExerciseId = exercise.id,
                upNextExerciseId = nextExercise.id,
                restSeconds = config.restBetweenSetsSeconds,
                nextExerciseIndex = completedExerciseIndex + 1,
            )
        }

        clear(workoutId)
        return WorkoutRunPostNav.Complete
    }
}
