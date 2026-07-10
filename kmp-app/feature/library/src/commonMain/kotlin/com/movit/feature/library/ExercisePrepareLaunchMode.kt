package com.movit.feature.library

/**
 * Why [ExercisePrepareRoute] was opened.
 * Distinct from [ExercisePreparePhase] (Prepare vs Rest UI).
 *
 * - [SoloStart]: Explore/library single exercise.
 * - [WorkoutPreview]: exercise tap inside a workout — preview only; must not change start index.
 * - [WorkoutFirstExercise]: Start from session dock — first exercise of an active/begun run.
 */
sealed interface ExercisePrepareMode {
    data object SoloStart : ExercisePrepareMode

    data class WorkoutPreview(
        val runDraftId: String,
        val exerciseId: String,
    ) : ExercisePrepareMode

    data class WorkoutFirstExercise(
        val runId: String,
    ) : ExercisePrepareMode
}

/** Screen phase: content prepare vs between-exercise rest timer (legacy rest route). */
enum class ExercisePreparePhase {
    Prepare,
    Rest,
}

object ExercisePrepareModeCodec {
    const val SOLO = "solo"
    const val PREVIEW = "preview"
    const val WORKOUT_FIRST = "workout_first"
    const val REST_PHASE = "rest"

    fun encode(mode: ExercisePrepareMode, phase: ExercisePreparePhase = ExercisePreparePhase.Prepare): String {
        if (phase == ExercisePreparePhase.Rest) return REST_PHASE
        return when (mode) {
            ExercisePrepareMode.SoloStart -> SOLO
            is ExercisePrepareMode.WorkoutPreview -> PREVIEW
            is ExercisePrepareMode.WorkoutFirstExercise -> WORKOUT_FIRST
        }
    }

    fun decode(
        raw: String,
        exerciseId: String,
        workoutId: String?,
        runId: String? = null,
    ): Pair<ExercisePrepareMode, ExercisePreparePhase> {
        if (raw == REST_PHASE) {
            val mode = when {
                !runId.isNullOrBlank() -> ExercisePrepareMode.WorkoutFirstExercise(runId)
                !workoutId.isNullOrBlank() -> ExercisePrepareMode.WorkoutPreview(
                    runDraftId = workoutId,
                    exerciseId = exerciseId,
                )
                else -> ExercisePrepareMode.SoloStart
            }
            return mode to ExercisePreparePhase.Rest
        }
        val mode = when (raw) {
            PREVIEW -> ExercisePrepareMode.WorkoutPreview(
                runDraftId = workoutId ?: exerciseId,
                exerciseId = exerciseId,
            )
            WORKOUT_FIRST -> ExercisePrepareMode.WorkoutFirstExercise(
                runId = runId
                    ?: workoutId?.let { WorkoutRunStore.activeRunForWorkout(it)?.runId?.value }
                    ?: workoutId
                    ?: "pending",
            )
            else -> ExercisePrepareMode.SoloStart
        }
        return mode to ExercisePreparePhase.Prepare
    }
}
