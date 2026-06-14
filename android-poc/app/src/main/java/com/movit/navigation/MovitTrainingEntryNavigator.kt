package com.movit.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.movit.MovitMainActivity

/**
 * Routes legacy Android training entry points into the KMP shell.
 */
object MovitTrainingEntryNavigator {
    const val EXTRA_SHELL_ROUTE = "movit.shell.route"
    const val EXTRA_SHELL_ARG = "movit.shell.arg"
    const val EXTRA_SHELL_ARG2 = "movit.shell.arg2"
    const val EXTRA_WORKOUT_CONFIG_JSON = "movit.shell.workout_config_json"

    const val ROUTE_WORKOUT_SESSION = "workout_session"
    const val ROUTE_WORKOUT_SESSION_LOCAL = "workout_session_local"
    const val ROUTE_EXERCISE_PREPARE = "exercise_prepare"
    const val ROUTE_ASSESSMENT = "assessment"
    const val ROUTE_PROGRAM_DETAIL = "program_detail"

    fun openPlannedWorkout(
        context: Context,
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkoutId: String,
    ) {
        val sessionKey = "session:$programId:$weekNumber:$dayNumber:$plannedWorkoutId"
        context.startActivity(shellIntent(context, ROUTE_WORKOUT_SESSION, sessionKey))
    }

    fun openWorkoutSession(context: Context, workoutId: String) {
        context.startActivity(shellIntent(context, ROUTE_WORKOUT_SESSION, workoutId))
    }

    fun openWorkoutSessionWithLocalConfig(
        context: Context,
        workoutId: String,
        workoutConfigJson: String,
    ) {
        context.startActivity(
            shellIntent(context, ROUTE_WORKOUT_SESSION_LOCAL, workoutId).apply {
                putExtra(EXTRA_WORKOUT_CONFIG_JSON, workoutConfigJson)
            },
        )
    }

    fun openExercisePrepare(
        context: Context,
        exerciseId: String,
        workoutId: String? = null,
    ) {
        context.startActivity(shellIntent(context, ROUTE_EXERCISE_PREPARE, exerciseId, workoutId))
    }

    fun openAssessment(context: Context) {
        context.startActivity(shellIntent(context, ROUTE_ASSESSMENT, ""))
    }

    fun openProgramDetailWeek(context: Context, programId: String, weekNumber: Int) {
        context.startActivity(
            shellIntent(context, ROUTE_PROGRAM_DETAIL, programId, weekNumber.toString()),
        )
    }

    fun encodePlannedWorkoutSessionKey(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkoutId: String,
    ): String = "session:$programId:$weekNumber:$dayNumber:$plannedWorkoutId"

    private fun shellIntent(
        context: Context,
        route: String,
        arg: String,
        arg2: String? = null,
    ): Intent {
        val target = resolveShellActivityClass()
        return Intent(context, target).apply {
            putExtra(EXTRA_SHELL_ROUTE, route)
            putExtra(EXTRA_SHELL_ARG, arg)
            arg2?.let { putExtra(EXTRA_SHELL_ARG2, it) }
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun resolveShellActivityClass(): Class<out Activity> = MovitMainActivity::class.java
}
