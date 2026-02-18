package com.trainingvalidator.poc.assessment.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.assessment.models.FitnessLevel
import com.trainingvalidator.poc.network.ApiClient

/**
 * LevelThresholdsManager - Dynamic level threshold management.
 * 
 * Fetches level definitions from the server and caches them locally.
 * FitnessLevel.fromBodyScore() delegates to this manager for dynamic thresholds.
 */
object LevelThresholdsManager {
    private const val TAG = "LevelThresholds"
    private const val PREFS_NAME = "level_thresholds_cache"
    private const val KEY_THRESHOLDS_JSON = "cached_thresholds"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class LevelThreshold(
        val number: Int,
        val code: String,
        val nameEn: String,
        val nameAr: String,
        val entryThreshold: Float,
        val maxThreshold: Float?,
        val color: String?
    )

    private var cachedThresholds: List<LevelThreshold>? = null

    // Default thresholds matching original hardcoded values
    private val DEFAULT_THRESHOLDS = listOf(
        LevelThreshold(1, "foundation", "Foundation", "التأسيس", 0f, 25f, "#9E9E9E"),
        LevelThreshold(2, "building", "Building", "البناء", 25f, 45f, "#FF9800"),
        LevelThreshold(3, "intermediate", "Intermediate", "متوسط", 45f, 65f, "#FFC107"),
        LevelThreshold(4, "advanced", "Advanced", "متقدم", 65f, 85f, "#8BC34A"),
        LevelThreshold(5, "elite", "Elite", "متميز", 85f, null, "#4CAF50")
    )

    /**
     * Sync levels from server. Call on login or app start.
     */
    suspend fun syncFromServer(context: Context, authToken: String) {
        try {
            val response = ApiClient.mobileSyncApi.getLevels("Bearer $authToken")
            if (response.isSuccessful && response.body()?.success == true) {
                val levels = response.body()!!.data
                if (levels != null && levels.isNotEmpty()) {
                    val thresholds = levels.map { level ->
                        LevelThreshold(
                            number = level.number,
                            code = level.code,
                            nameEn = level.name.en,
                            nameAr = level.name.ar,
                            entryThreshold = level.entryThreshold.toFloat(),
                            maxThreshold = level.maxThreshold?.toFloat(),
                            color = level.color
                        )
                    }.sortedByDescending { it.entryThreshold }
                    
                    cachedThresholds = thresholds
                    saveToCache(context, thresholds)
                    Log.d(TAG, "Synced ${thresholds.size} levels from server")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync levels from server", e)
        }
    }

    /**
     * Load from cache (call on app start before sync).
     */
    fun loadFromCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_THRESHOLDS_JSON, null) ?: return
            val type = object : TypeToken<List<LevelThreshold>>() {}.type
            cachedThresholds = Gson().fromJson(json, type)
            Log.d(TAG, "Loaded ${cachedThresholds?.size ?: 0} levels from cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached thresholds", e)
        }
    }

    /**
     * Get thresholds (cached → defaults).
     */
    fun getThresholds(): List<LevelThreshold> = cachedThresholds ?: DEFAULT_THRESHOLDS

    /**
     * Determine fitness level from body score using dynamic thresholds.
     */
    fun fromBodyScore(score: Float): FitnessLevel {
        val thresholds = getThresholds().sortedByDescending { it.entryThreshold }
        for (threshold in thresholds) {
            if (score >= threshold.entryThreshold) {
                return codeToFitnessLevel(threshold.code)
            }
        }
        return FitnessLevel.NEEDS_REHAB
    }

    /**
     * Get the level number from body score.
     */
    fun getLevelNumber(score: Float): Int {
        val thresholds = getThresholds().sortedByDescending { it.entryThreshold }
        for (threshold in thresholds) {
            if (score >= threshold.entryThreshold) {
                return threshold.number
            }
        }
        return 1
    }

    private fun codeToFitnessLevel(code: String): FitnessLevel = when (code) {
        "elite" -> FitnessLevel.EXCELLENT
        "advanced" -> FitnessLevel.GOOD
        "intermediate" -> FitnessLevel.AVERAGE
        "building" -> FitnessLevel.LIMITED
        "foundation" -> FitnessLevel.NEEDS_REHAB
        else -> {
            // For custom levels, map based on position
            val thresholds = getThresholds()
            val levelIndex = thresholds.indexOfFirst { it.code == code }
            val ratio = if (thresholds.size > 1) levelIndex.toFloat() / (thresholds.size - 1) else 0f
            when {
                ratio >= 0.8f -> FitnessLevel.EXCELLENT
                ratio >= 0.6f -> FitnessLevel.GOOD
                ratio >= 0.4f -> FitnessLevel.AVERAGE
                ratio >= 0.2f -> FitnessLevel.LIMITED
                else -> FitnessLevel.NEEDS_REHAB
            }
        }
    }

    private fun saveToCache(context: Context, thresholds: List<LevelThreshold>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = Gson().toJson(thresholds)
            prefs.edit()
                .putString(KEY_THRESHOLDS_JSON, json)
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache thresholds", e)
        }
    }
}
