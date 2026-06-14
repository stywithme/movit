package com.movit.host

import android.content.Intent
import com.movit.feature.library.WorkoutFlowCache
import com.movit.feature.library.WorkoutFlowConfigUi
import com.movit.feature.library.WorkoutFlowExerciseUi
import com.movit.feature.shell.MovitInnerRoute
import com.movit.feature.shell.MovitShellPendingNavigation
import com.movit.navigation.MovitTrainingEntryNavigator
import com.google.gson.Gson
import com.movit.core.network.dto.WorkoutFlowConfigDto

/**
 * Parses [MovitTrainingEntryNavigator] intent extras into shell inner routes.
 */
object MovitShellDeepLinkParser {

    fun applyFromIntent(intent: Intent?) {
        val route = intent?.getStringExtra(MovitTrainingEntryNavigator.EXTRA_SHELL_ROUTE) ?: return
        val arg = intent.getStringExtra(MovitTrainingEntryNavigator.EXTRA_SHELL_ARG).orEmpty()
        val arg2 = intent.getStringExtra(MovitTrainingEntryNavigator.EXTRA_SHELL_ARG2)

        val innerRoute = when (route) {
            MovitTrainingEntryNavigator.ROUTE_WORKOUT_SESSION ->
                MovitInnerRoute.WorkoutSession(arg)
            MovitTrainingEntryNavigator.ROUTE_WORKOUT_SESSION_LOCAL -> {
                seedLocalWorkoutCache(intent, arg)
                MovitInnerRoute.WorkoutSession(arg)
            }
            MovitTrainingEntryNavigator.ROUTE_EXERCISE_PREPARE ->
                MovitInnerRoute.ExercisePrepare(exerciseId = arg, workoutId = arg2)
            MovitTrainingEntryNavigator.ROUTE_ASSESSMENT ->
                MovitInnerRoute.Assessment()
            MovitTrainingEntryNavigator.ROUTE_PROGRAM_DETAIL -> {
                val week = arg2?.toIntOrNull() ?: 1
                MovitInnerRoute.ProgramDetail(programId = arg, initialWeekNumber = week)
            }
            else -> return
        }
        MovitShellPendingNavigation.set(listOf(innerRoute))
    }

    private fun seedLocalWorkoutCache(intent: Intent, workoutId: String) {
        val json = intent.getStringExtra(MovitTrainingEntryNavigator.EXTRA_WORKOUT_CONFIG_JSON) ?: return
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
