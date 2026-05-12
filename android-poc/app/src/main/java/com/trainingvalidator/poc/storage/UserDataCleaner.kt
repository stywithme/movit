package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.assessment.engine.AssessmentTemplateManager

/**
 * UserDataCleaner — Central cache invalidation on logout / user switch
 *
 * Clears every piece of user-specific cached data so that a new user
 * who logs in on the same device never sees stale data from a previous session.
 *
 * Call [clearAll] from the logout flow BEFORE navigating to SignInActivity.
 *
 * What is cleared (user-specific):
 *   - HomeRepository cache            (home_cache)
 *   - ExploreRepository cache         (explore_cache)
 *   - DayCustomizationStore           (day_customization_store)
 *   - UserProgramStore                (user_program_store)
 *   - ProgramSessionReportStore       (program_session_report_store)
 *   - UserExercisePreferenceStore     (user_exercise_preferences)
 *   - AssessmentTemplateManager cache (assessment_template_cache)
 *   - HomeRepository singleton        (in-memory)
 *   - ExploreRepository singleton     (in-memory)
 *   - Exercise/Workout/Program repository singletons (in-memory only)
 *
 * What is NOT cleared:
 *   - AuthManager (app_prefs) — managed separately by the caller
 *   - ExerciseCacheManager / AudioCacheManager — not user-specific
 *   - WorkoutCacheManager — public workout data, not user-specific
 */
object UserDataCleaner {

    private const val TAG = "UserDataCleaner"

    private const val PREFS_HOME = "home_cache"
    private const val PREFS_EXPLORE = "explore_cache"
    private const val PREFS_DAY_CUSTOMIZATION = "day_customization_store"
    private const val PREFS_USER_PROGRAM = "user_program_store"
    private const val PREFS_SESSION_REPORT_LEGACY = "program_session_report_store"
    private const val PREFS_ASSESSMENT_TEMPLATE = "assessment_template_cache"
    private const val PREFS_USER_EXERCISE_PREFS = UserExercisePreferenceStore.PREFS_NAME

    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        Log.i(TAG, "Clearing all user caches...")

        clearPrefs(appContext, PREFS_HOME)
        clearPrefs(appContext, PREFS_EXPLORE)
        clearPrefs(appContext, PREFS_DAY_CUSTOMIZATION)
        clearPrefs(appContext, PREFS_USER_PROGRAM)
        clearPrefs(appContext, PREFS_SESSION_REPORT_LEGACY)
        clearPrefs(appContext, PREFS_USER_EXERCISE_PREFS)
        clearPrefs(appContext, PREFS_ASSESSMENT_TEMPLATE)

        HomeRepository.resetInstance()
        ExploreRepository.resetInstance()
        AssessmentTemplateManager.resetCache()
        ExerciseRepository.resetInstance()
        WorkoutRepository.resetInstance()
        ProgramRepository.resetInstance()

        Log.i(TAG, "All user caches cleared.")
    }

    private fun clearPrefs(context: Context, prefsName: String) {
        try {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            Log.d(TAG, "Cleared: $prefsName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear $prefsName", e)
        }
    }
}
