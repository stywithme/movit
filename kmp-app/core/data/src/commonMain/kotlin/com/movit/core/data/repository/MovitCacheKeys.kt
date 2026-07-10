package com.movit.core.data.repository

object MovitCacheKeys {
    const val EXPLORE_STORE = "explore_cache"
    const val EXPLORE_DATA = "explore_data_json"
    const val EXPLORE_LAST_SYNC = "explore_last_sync"

    const val HOME_STORE = "home_cache"
    const val HOME_DATA = "home_data_json"
    /** Weak ETag from last successful GET /mobile/home (P2.4). */
    const val HOME_ETAG = "home_etag"
    /** Set when complete/plan hit FAILED_PERMANENT — feeds UX.6 Home alert. */
    const val HOME_UPLOAD_FAILED = "home_upload_failed"

    const val REPORTS_STORE = "reports_cache"
    const val REPORTS_DASHBOARD = "reports_dashboard_json"
    const val PLANNED_WORKOUT_REPORTS_INDEX = "planned_workout_reports_index"

    fun reportsExerciseKey(exerciseSlug: String): String = "reports_exercise_$exerciseSlug"

    fun reportsMetricsKey(
        scope: String,
        programId: String,
        weekNumber: Int? = null,
        dayNumber: Int? = null,
        plannedWorkoutId: String? = null,
        exerciseSlug: String? = null,
    ): String {
        val parts = listOf(
            scope,
            programId,
            weekNumber?.toString().orEmpty(),
            dayNumber?.toString().orEmpty(),
            plannedWorkoutId.orEmpty(),
            exerciseSlug.orEmpty(),
        )
        return "reports_metrics_${parts.joinToString("_")}"
    }

    fun plannedWorkoutReportKey(plannedWorkoutId: String): String =
        "planned_workout_report_$plannedWorkoutId"

    fun postTrainingReportKey(reportId: String): String = "post_training_report_$reportId"

    fun sessionReportKey(reportId: String): String = "session_report_$reportId"

    fun exerciseSetReportsIndexKey(sessionExerciseKey: String): String =
        "exercise_set_reports_$sessionExerciseKey"

    fun reportSessionExerciseKey(reportId: String): String = "report_session_exercise_$reportId"

    const val SESSION_STORE = "session_cache"

    const val SESSION_JOURNAL_STORE = "session_journal_cache"

    fun sessionJournalKey(sessionId: String): String = "session_journal_$sessionId"

    const val DAY_CUSTOMIZATION_STORE = "day_customization_cache"

    /** Legacy Android prefs namespace for day overrides (keys used [programId], not [userProgramId]). */
    const val LEGACY_DAY_CUSTOMIZATION_STORE = "day_customization_store"

    const val PREFERENCES_STORE = "exercise_preferences_cache"

    /** One-time marker: legacy programId / slug preference keys were rewritten to canonical ids. */
    const val CANONICAL_CACHE_KEYS_MIGRATED = "canonical_cache_keys_migrated_v1"

    const val PROGRAM_STORE = "program_cache"
    const val PROGRAM_ID_INDEX = "program_id_index"
    const val ACTIVE_USER_PROGRAM_ID = "active_user_program_id"

    const val CATALOG_INDEX_STORE = "catalog_index_cache"
    const val WORKOUT_TEMPLATE_STORE = "workout_template_cache"
    const val WORKOUT_TEMPLATE_ID_INDEX = "workout_template_id_index"

    const val USER_PROGRAM_ENROLLMENT_STORE = "user_program_enrollment_cache"
    const val USER_PROGRAM_ENROLLMENT_INDEX = "user_program_enrollment_index"

    fun userProgramEnrollmentKey(userProgramId: String): String =
        "user_program_enrollment_$userProgramId"

    /** Legacy Android prefs store for [UserProgramStore] — migrated on install (WS-4). */
    const val LEGACY_USER_PROGRAM_STORE = "user_program_store"
    const val LEGACY_USER_PROGRAMS_KEY = "user_programs"

    /** Legacy Android prefs store for [UserExercisePreferenceStore] — keys copied to [PREFERENCES_STORE]. */
    const val LEGACY_USER_EXERCISE_PREFERENCES_STORE = "user_exercise_preferences"

    fun exercisePreferenceKey(exerciseId: String): String = "pref_$exerciseId"

    fun dayCustomizationKey(userProgramId: String, weekNumber: Int, dayNumber: Int): String =
        "day_${userProgramId}_${weekNumber}_$dayNumber"

    /** Set by [com.movit.core.data.local.MigratingMovitLocalStore] after one-time platform→SQLDelight migration. */
    const val LEGACY_CUTOVER_V1 = "legacy_cutover_v1"

    /** Survives [com.movit.core.data.local.MovitLocalStore.clearReadCaches]; cleared on logout. */
    const val AUTH_LIFECYCLE_STORE = "auth_lifecycle_prefs"
    const val LAST_KNOWN_USER_ID = "last_known_user_id"

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

    const val SYNC_DIAG_LAST_CYCLE = "sync_diag_last_cycle_json"
    const val SYNC_DIAG_ERROR_DECODE_COUNT = "sync_error_decode_count"
    const val SYNC_DIAG_OUTBOX_FAILED_PERMANENT = "outbox_failed_permanent_count"
    const val SYNC_DIAG_OUTBOX_RETRY_EXHAUSTED = "outbox_retry_exhausted_count"
    const val SYNC_DIAG_CATALOG_INCOMPLETE = "sync_catalog_graph_incomplete_count"

    const val AUDIO_STORE = "audio_manifest_cache"
    const val AUDIO_BASE_URL = "audio_base_url"
    const val AUDIO_MANIFEST_JSON = "audio_manifest_json"

    const val SYSTEM_MESSAGE_STORE = "system_message_cache"
    const val SYSTEM_MESSAGES_JSON = "system_messages_json"

    const val MESSAGE_LIBRARY_STORE = "message_library_cache"
    const val MESSAGE_LIBRARY_JSON = "message_library_json"

    fun programKey(programId: String): String = "program_export_$programId"

    fun workoutTemplateExportKey(templateId: String): String = "workout_export_$templateId"

    fun effectivePlanKey(userProgramId: String, weekNumber: Int, dayNumber: Int): String =
        "effective_plan_${userProgramId}_${weekNumber}_$dayNumber"

    fun workoutTemplateTrainingConfigKey(templateId: String): String =
        "workout_template_training_config_$templateId"

    const val EXERCISE_CONFIG_STORE = "exercise_config_cache"
    const val EXERCISE_CONFIG_SLUG_INDEX = "exercise_config_slug_index"
    const val EXERCISE_CONFIG_SLUG_ALIASES = "exercise_config_slug_aliases"
    const val TRAINING_PREFERENCES_STORE = "training_preferences_cache"
    const val TRAINING_PREFERENCES_JSON = "training_preferences_json"

    fun exerciseConfigKey(slug: String): String = "exercise_config_$slug"

    fun exerciseIdToSlugKey(id: String): String = "exercise_id_slug_$id"
}
