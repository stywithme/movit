package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.network.MetricsSummary
import java.io.File

/**
 * ReportRepository — Unified data layer for the reports endpoint.
 *
 * Offline-first: shows cached metrics immediately, then fetches
 * fresh data from the backend (source of truth) in background.
 */
class ReportRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ReportRepository"
        private const val CACHE_DIR = "report_metrics_cache"

        @Volatile
        private var instance: ReportRepository? = null

        fun getInstance(context: Context): ReportRepository {
            return instance ?: synchronized(this) {
                instance ?: ReportRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val api = ApiClient.mobileSyncApi
    private val cacheDir: File by lazy {
        File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
    }

    // ══════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════

    /**
     * Fetch program-level metrics (with week breakdown).
     * Returns cached data first, then network.
     */
    suspend fun getProgramMetrics(programId: String, includeChildren: Boolean = true): MetricsResponse? {
        return fetchMetrics(
            programId = programId,
            scope = "program",
            includeChildren = includeChildren,
            cacheKey = "program_$programId"
        )
    }

    /**
     * Fetch week-level metrics (with day breakdown).
     */
    suspend fun getWeekMetrics(
        programId: String, weekNumber: Int,
        includeChildren: Boolean = true, includeHistory: Boolean = false
    ): MetricsResponse? {
        return fetchMetrics(
            programId = programId,
            scope = "week",
            weekNumber = weekNumber,
            includeChildren = includeChildren,
            includeHistory = includeHistory,
            cacheKey = "week_${programId}_$weekNumber"
        )
    }

    /**
     * Fetch day-level metrics (with session breakdown).
     */
    suspend fun getDayMetrics(
        programId: String, weekNumber: Int, dayNumber: Int,
        includeChildren: Boolean = true
    ): MetricsResponse? {
        return fetchMetrics(
            programId = programId,
            scope = "day",
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            includeChildren = includeChildren,
            cacheKey = "day_${programId}_${weekNumber}_$dayNumber"
        )
    }

    /**
     * Fetch session-level metrics (with exercise breakdown).
     */
    suspend fun getSessionMetrics(
        programId: String, sessionId: String,
        includeChildren: Boolean = true, includeHistory: Boolean = false
    ): MetricsResponse? {
        return fetchMetrics(
            programId = programId,
            scope = "session",
            sessionId = sessionId,
            includeChildren = includeChildren,
            includeHistory = includeHistory,
            cacheKey = "session_${programId}_$sessionId"
        )
    }

    /**
     * Fetch exercise-level metrics (with historical comparison).
     */
    suspend fun getExerciseMetrics(
        programId: String, exerciseSlug: String,
        includeHistory: Boolean = true
    ): MetricsResponse? {
        return fetchMetrics(
            programId = programId,
            scope = "exercise",
            exerciseSlug = exerciseSlug,
            includeHistory = includeHistory,
            cacheKey = "exercise_${programId}_$exerciseSlug"
        )
    }

    /**
     * Get cached metrics (for offline-first display).
     */
    fun getCachedProgramMetrics(programId: String): MetricsResponse? {
        return readCache("program_$programId")
    }

    /**
     * Clear all cached metrics (e.g., on logout).
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cache", e)
        }
    }

    // ══════════════════════════════════════════════════════
    // INTERNAL: Network + Cache
    // ══════════════════════════════════════════════════════

    private suspend fun fetchMetrics(
        programId: String,
        scope: String,
        weekNumber: Int? = null,
        dayNumber: Int? = null,
        sessionId: String? = null,
        exerciseSlug: String? = null,
        includeChildren: Boolean = false,
        includeHistory: Boolean = false,
        cacheKey: String
    ): MetricsResponse? {
        val authHeader = AuthManager.getAuthHeader(context)
        if (authHeader == null) {
            Log.w(TAG, "No auth token — returning cached data")
            return readCache(cacheKey)
        }

        return try {
            val response = api.getMetrics(
                authorization = authHeader,
                programId = programId,
                scope = scope,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                sessionId = sessionId,
                exerciseSlug = exerciseSlug,
                includeHistory = includeHistory,
                includeChildren = includeChildren
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                writeCache(cacheKey, body)
                Log.d(TAG, "Fetched $scope metrics for $programId")
                body
            } else {
                Log.w(TAG, "API error: ${response.code()} — using cache")
                readCache(cacheKey)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network error — using cache: ${e.message}")
            readCache(cacheKey)
        }
    }

    private fun writeCache(key: String, data: MetricsResponse) {
        try {
            val file = File(cacheDir, "$key.json")
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write cache: $key", e)
        }
    }

    private fun readCache(key: String): MetricsResponse? {
        return try {
            val file = File(cacheDir, "$key.json")
            if (!file.exists()) return null
            gson.fromJson(file.readText(), MetricsResponse::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cache: $key", e)
            null
        }
    }
}
