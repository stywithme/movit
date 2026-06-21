package com.movit.host

import android.content.Intent
import com.movit.feature.library.WorkoutFlowCache
import com.movit.feature.library.WorkoutFlowConfigUi
import com.movit.feature.library.WorkoutFlowExerciseUi
import com.movit.feature.shell.MovitInnerRoute
import com.movit.feature.shell.MovitShellPendingNavigation
import com.google.gson.Gson
import com.movit.core.network.dto.WorkoutFlowConfigDto

/**
 * Parses external launch intents (deep links / QA adb) into shell inner routes.
 */
object MovitShellDeepLinkParser {

    const val EXTRA_SHELL_ROUTE = "movit.shell.route"
    const val EXTRA_SHELL_ARG = "movit.shell.arg"
    const val EXTRA_SHELL_ARG2 = "movit.shell.arg2"
    const val EXTRA_WORKOUT_CONFIG_JSON = "movit.shell.workout_config_json"

    const val ROUTE_WORKOUT_SESSION = "workout_session"
    const val ROUTE_WORKOUT_SESSION_LOCAL = "workout_session_local"
    const val ROUTE_EXERCISE_PREPARE = "exercise_prepare"
    const val ROUTE_ASSESSMENT = "assessment"
    const val ROUTE_PROGRAM_DETAIL = "program_detail"

    fun applyFromIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_SHELL_ROUTE) ?: return
        val arg = intent.getStringExtra(EXTRA_SHELL_ARG).orEmpty()
        val arg2 = intent.getStringExtra(EXTRA_SHELL_ARG2)

        val innerRoute = when (route) {
            ROUTE_WORKOUT_SESSION ->
                MovitInnerRoute.WorkoutSession(arg)
            ROUTE_WORKOUT_SESSION_LOCAL -> {
                seedLocalWorkoutCache(intent, arg)
                MovitInnerRoute.WorkoutSession(arg)
            }
            ROUTE_EXERCISE_PREPARE ->
                MovitInnerRoute.ExercisePrepare(exerciseId = arg, workoutId = arg2)
            ROUTE_ASSESSMENT ->
                MovitInnerRoute.Assessment()
            ROUTE_PROGRAM_DETAIL -> {
                val week = arg2?.toIntOrNull() ?: 1
                MovitInnerRoute.ProgramDetail(programId = arg, initialWeekNumber = week)
            }
            else -> return
        }
        MovitShellPendingNavigation.set(listOf(innerRoute))
    }

    private fun seedLocalWorkoutCache(intent: Intent, workoutId: String) {
        val json = intent.getStringExtra(EXTRA_WORKOUT_CONFIG_JSON) ?: return
        val config = runCatching {
            Gson().fromJson(json, WorkoutFlowConfigDto::class.java)
        }.getOrNull() ?: return
        val language = "en"
        val items = config.effectiveExercises()
        val title = config.name.display(language).ifBlank { workoutId }
        WorkoutFlowCache.put(
            WorkoutFlowConfigUi(
                workoutId = workoutId,
                title = title,
                subtitle = "${items.size} exercises",
                exercises = items.mapIndexed { index, exercise ->
                    WorkoutFlowExerciseUi(
                        id = "ex-$index",
                        exerciseSlug = exercise.exercise,
                        name = exercise.exercise.replace('-', ' ').replaceFirstChar { it.uppercase() },
                        sets = exercise.sets.coerceAtLeast(1),
                        reps = exercise.targetReps,
                        durationSeconds = exercise.targetDuration,
                        variantIndex = exercise.variantIndex,
                    )
                },
                restBetweenSetsSeconds = items.firstOrNull()
                    ?.restBetweenSetsMs?.let { (it / 1000).toInt() } ?: 60,
            ),
        )
    }
}
