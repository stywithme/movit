package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trainingvalidator.poc.network.UserExercisePreferenceSync

/**
 * Per-user local storage for standalone exercise targets (reps, hold duration, weight).
 * Synced with backend via [UserExercisePreferenceSync] on mobile sync.
 */
class UserExercisePreferenceStore(private val context: Context) {

    companion object {
        private const val TAG = "UserExercisePreferenceStore"
        const val PREFS_NAME = "user_exercise_preferences"
        private const val KEY_PREFIX = "pref_"
    }

    private val gson = Gson()
    private val prefs
        get() = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Stored(
        val customReps: Int? = null,
        val customDurationSec: Int? = null,
        val customWeightKg: Float? = null,
        val updatedAt: String? = null
    )

    private fun key(slug: String) = "$KEY_PREFIX$slug"

    fun get(slug: String): Stored? {
        val json = prefs.getString(key(slug), null) ?: return null
        return try {
            gson.fromJson(json, Stored::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse preference for $slug", e)
            null
        }
    }

    fun save(slug: String, values: Stored) {
        prefs.edit().putString(key(slug), gson.toJson(values)).apply()
    }

    fun remove(slug: String) {
        prefs.edit().remove(key(slug)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Replace local store with server list (authenticated sync).
     */
    fun hydrateFromSync(rows: List<UserExercisePreferenceSync>) {
        prefs.edit().clear().apply()
        rows.forEach { row ->
            val hasAny = row.customReps != null ||
                row.customDurationSec != null ||
                row.customWeightKg != null
            if (!hasAny) return@forEach
            save(
                row.exerciseSlug,
                Stored(
                    customReps = row.customReps,
                    customDurationSec = row.customDurationSec,
                    customWeightKg = row.customWeightKg?.toFloat(),
                    updatedAt = row.updatedAt
                )
            )
        }
        Log.d(TAG, "Hydrated ${rows.size} preference row(s) from sync")
    }
}
