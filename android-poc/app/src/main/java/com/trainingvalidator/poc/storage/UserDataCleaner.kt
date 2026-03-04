package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log

/**
 * UserDataCleaner — Central cache invalidation on logout / user switch
 *
 * Clears every piece of user-specific cached data so that a new user
 * who logs in on the same device never sees stale data from a previous session.
 *
 * Call [clearAll] from the logout flow BEFORE navigating to SignInActivity.
 *
 * What is cleared (user-specific):
 *   - HomeRepository cache      (home_cache)
 *   - ExploreRepository cache   (explore_cache)
 *   - DayCustomizationStore     (day_customization_store)
 *   - UserProgramStore          (user_program_store)
 *   - ProgramSessionReportStore (program_session_report_store)
 *   - HomeRepository singleton  (in-memory)
 *   - ExploreRepository singleton (in-memory)
 *
 * What is NOT cleared:
 *   - AuthManager (app_prefs) — managed separately by the caller
 *   - ExerciseCacheManager / AudioCacheManager — not user-specific
 *   - WorkoutCacheManager — public workout data, not user-specific
 */
object UserDataCleaner {

    private const val TAG = "UserDataCleaner"

    /** SharedPrefs names — kept in sync with each store's companion constant */
    private const val PREFS_HOME = "home_cache"
    private const val PREFS_EXPLORE = "explore_cache"
    private const val PREFS_DAY_CUSTOMIZATION = "day_customization_store"
    private const val PREFS_USER_PROGRAM = "user_program_store"
    private const val PREFS_SESSION_REPORT_LEGACY = "program_session_report_store"

    /**
     * Clears all user-specific caches synchronously.
     * Safe to call from any thread.
     */
    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        Log.i(TAG, "Clearing all user caches...")

        clearPrefs(appContext, PREFS_HOME)
        clearPrefs(appContext, PREFS_EXPLORE)
        clearPrefs(appContext, PREFS_DAY_CUSTOMIZATION)
        clearPrefs(appContext, PREFS_USER_PROGRAM)
        clearPrefs(appContext, PREFS_SESSION_REPORT_LEGACY)

        // Reset in-memory singletons so stale data is not served from memory
        HomeRepository.resetInstance()
        ExploreRepository.resetInstance()

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
