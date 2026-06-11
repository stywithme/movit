package com.movit.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.movit.MovitMainActivity
import com.trainingvalidator.poc.BuildConfig

/**
 * WS-10 — routes legacy Android training entry points into the KMP shell.
 */
object MovitTrainingEntryNavigator {
    const val EXTRA_SHELL_ROUTE = "movit.shell.route"
    const val EXTRA_SHELL_ARG = "movit.shell.arg"
    const val EXTRA_SHELL_ARG2 = "movit.shell.arg2"
    const val EXTRA_WORKOUT_CONFIG_JSON = "movit.shell.workout_config_json"

    const val ROUTE_WORKOUT_SESSION = "workout_session"
    const val ROUTE_WORKOUT_RUN = "workout_run"
    const val ROUTE_WORKOUT_RUN_LOCAL = "workout_run_local"
    const val ROUTE_EXERCISE_PREPARE = "exercise_prepare"
    const val ROUTE_ASSESSMENT = "assessment"
    const val ROUTE_PROGRAM_WEEK_PLAN = "program_week_plan"

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

    fun openWorkoutRun(context: Context, workoutId: String) {
        context.startActivity(shellIntent(context, ROUTE_WORKOUT_RUN, workoutId))
    }

    fun openWorkoutRunWithLocalConfig(
        context: Context,
        workoutId: String,
        workoutConfigJson: String,
    ) {
        context.startActivity(
            shellIntent(context, ROUTE_WORKOUT_RUN_LOCAL, workoutId).apply {
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

    fun openProgramWeekPlan(context: Context, programId: String, weekNumber: Int) {
        context.startActivity(
            shellIntent(context, ROUTE_PROGRAM_WEEK_PLAN, programId, weekNumber.toString()),
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

    private fun resolveShellActivityClass(): Class<out Activity> {
        if (BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED) {
            return MovitMainActivity::class.java
        }
        if (BuildConfig.DEBUG) {
            @Suppress("UNCHECKED_CAST")
            return Class.forName("com.movit.debug.MovitShellPilotActivity") as Class<out Activity>
        }
        return MovitMainActivity::class.java
    }
}
