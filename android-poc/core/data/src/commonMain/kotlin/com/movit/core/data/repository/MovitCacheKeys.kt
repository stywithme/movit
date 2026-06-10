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

    const val PREFERENCES_STORE = "exercise_preferences_cache"

    const val PROGRAM_STORE = "program_cache"

    fun exercisePreferenceKey(exerciseId: String): String = "pref_$exerciseId"

    const val SYNC_STORE = "sync_manager_prefs"
    const val SYNC_LAST_TIMESTAMP = "last_sync_timestamp"
    const val SYNC_SERVER_VERSION = "server_version"
    const val SYNC_LOCAL_EXERCISES = "local_exercise_count"
    const val SYNC_LOCAL_WORKOUTS = "local_workout_count"
    const val SYNC_LOCAL_PROGRAMS = "local_program_count"
    const val SYNC_MSG_COUNT = "cached_message_count"
    const val SYNC_MSG_AUDIO = "cached_message_audio_count"
    const val SYNC_MSG_ASSIGNMENTS = "cached_message_assignments"
    const val SYNC_MSG_FINGERPRINT = "cached_message_fingerprint"

    const val AUDIO_STORE = "audio_manifest_cache"
    const val AUDIO_BASE_URL = "audio_base_url"
    const val AUDIO_MANIFEST_JSON = "audio_manifest_json"

    fun programKey(programId: String): String = "program_export_$programId"

    fun effectivePlanKey(userProgramId: String, weekNumber: Int, dayNumber: Int): String =
        "effective_plan_${userProgramId}_${weekNumber}_$dayNumber"
}
