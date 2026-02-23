package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ExploreData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ExploreRepository
 *
 * Offline-first cache for `/api/mobile/explore`.
 * - Returns cached content immediately.
 * - Applies incremental updates from backend in background.
 */
class ExploreRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ExploreRepository"
        private const val PREFS = "explore_cache"
        private const val KEY_DATA = "explore_data_json"
        private const val KEY_LAST_SYNC = "explore_last_sync"

        @Volatile
        private var instance: ExploreRepository? = null

        fun getInstance(context: Context): ExploreRepository {
            return instance ?: synchronized(this) {
                instance ?: ExploreRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val gson = Gson()

    fun getCachedData(): ExploreData? {
        val raw = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            gson.fromJson(raw, ExploreData::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Invalid explore cache, clearing", e)
            clear()
            null
        }
    }

    suspend fun syncFromServer(limit: Int = 6): ExploreData? = withContext(Dispatchers.IO) {
        val authHeader = AuthManager.getAuthHeader(context)
        val updatedAfter = prefs.getString(KEY_LAST_SYNC, null)
        val response = ApiClient.mobileSyncApi.getExplore(
            authorization = authHeader,
            updatedAfter = updatedAfter,
            limit = limit
        )

        if (!response.isSuccessful || response.body()?.success != true) {
            Log.w(TAG, "Explore sync failed: ${response.code()}")
            return@withContext getCachedData()
        }

        val body = response.body() ?: return@withContext getCachedData()
        val incoming = body.data ?: return@withContext getCachedData()
        val merged = merge(getCachedData(), incoming, body.meta?.isFullSync == true || updatedAfter == null)

        prefs.edit()
            .putString(KEY_DATA, gson.toJson(merged))
            .putString(KEY_LAST_SYNC, body.timestamp)
            .apply()

        merged
    }

    private fun merge(old: ExploreData?, incoming: ExploreData, isFullSync: Boolean): ExploreData {
        if (old == null || isFullSync) return incoming

        val mergedPrograms = old.programs
            .associateBy { it.id }
            .toMutableMap()
            .apply {
                incoming.deletedProgramIds.forEach { remove(it) }
                incoming.programs.forEach { put(it.id, it) }
            }
            .values
            .sortedByDescending { it.updatedAt }

        val mergedWorkouts = old.workouts
            .associateBy { it.id }
            .toMutableMap()
            .apply {
                incoming.deletedWorkoutIds.forEach { remove(it) }
                incoming.workouts.forEach { put(it.id, it) }
            }
            .values
            .sortedByDescending { it.updatedAt }

        val mergedExercises = old.exercises
            .associateBy { it.id }
            .toMutableMap()
            .apply {
                incoming.deletedExerciseIds.forEach { remove(it) }
                incoming.exercises.forEach { put(it.id, it) }
            }
            .values
            .sortedByDescending { it.updatedAt }

        return ExploreData(
            levels = if (incoming.levels.isNotEmpty()) incoming.levels else old.levels,
            programs = mergedPrograms,
            workouts = mergedWorkouts,
            exercises = mergedExercises,
            deletedProgramIds = emptyList(),
            deletedWorkoutIds = emptyList(),
            deletedExerciseIds = emptyList()
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
