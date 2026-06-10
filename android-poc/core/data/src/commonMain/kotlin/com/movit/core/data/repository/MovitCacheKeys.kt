package com.movit.core.data.repository

object MovitCacheKeys {
    const val EXPLORE_STORE = "explore_cache"
    const val EXPLORE_DATA = "explore_data_json"
    const val EXPLORE_LAST_SYNC = "explore_last_sync"

    const val HOME_STORE = "home_cache"
    const val HOME_DATA = "home_data_json"

    const val REPORTS_STORE = "reports_cache"
    const val REPORTS_DASHBOARD = "reports_dashboard_json"

    fun reportsExerciseKey(exerciseSlug: String): String = "reports_exercise_$exerciseSlug"

    const val SESSION_STORE = "session_cache"

    const val PROGRAM_STORE = "program_cache"

    fun programKey(programId: String): String = "program_export_$programId"

    fun effectivePlanKey(userProgramId: String, weekNumber: Int, dayNumber: Int): String =
        "effective_plan_${userProgramId}_${weekNumber}_$dayNumber"
}
