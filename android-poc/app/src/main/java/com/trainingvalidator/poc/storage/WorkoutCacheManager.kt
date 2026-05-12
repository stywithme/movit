package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.network.WorkoutConfigWithMeta
import com.trainingvalidator.poc.training.models.WorkoutConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * WorkoutCacheManager
 *
 * Manages local caching of workouts synced from the server.
 * Stores workouts as individual JSON files for easy access and updates.
 *
 * Storage structure:
 * /data/data/[package]/files/workout_cache/
 *   ├── metadata.json          (sync metadata)
 *   ├── workouts/
 *   │   ├── [slug].json       (individual workout files)
 *   │   └── ...
 */
class WorkoutCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "WorkoutCacheManager"
        private const val CACHE_DIR = "workout_cache"
        private const val WORKOUTS_DIR = "workouts"
        private const val METADATA_FILE = "metadata.json"
    }

    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create()

    private val cachedEntityType =
        TypeToken.getParameterized(CachedEntity::class.java, WorkoutConfig::class.java).type

    private val cacheDir: File = File(context.filesDir, CACHE_DIR)
    private val workoutsDir: File = File(cacheDir, WORKOUTS_DIR)
    private val metadataFile: File = File(cacheDir, METADATA_FILE)

    private val workoutCache: ConcurrentHashMap<String, CachedEntity<WorkoutConfig>> = ConcurrentHashMap()
    private var metadata: CacheMetadata? = null
    private var isLoaded = false

    init {
        ensureDirectoriesExist()
    }

    data class CacheMetadata(
        val lastSyncTimestamp: String,
        val workoutCount: Int,
        val serverVersion: String,
        val lastSyncSuccessful: Boolean = true
    )

    private fun ensureDirectoriesExist() {
        JsonEntityCacheSupport.ensureDirs(cacheDir, workoutsDir, TAG)
    }

    fun loadCache() {
        if (isLoaded) return

        try {
            if (metadataFile.exists()) {
                metadata = gson.fromJson(metadataFile.readText(), CacheMetadata::class.java)
                Log.d(TAG, "Loaded metadata: $metadata")
            }

            workoutsDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.forEach { file ->
                    try {
                        val cached: CachedEntity<WorkoutConfig> =
                            gson.fromJson(file.readText(), cachedEntityType)
                        cached.config.fileName = cached.slug
                        workoutCache[cached.slug] = cached
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load workout: ${file.name}", e)
                    }
                }

            isLoaded = true
            Log.d(TAG, "Loaded ${workoutCache.size} workouts from cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
        }
    }

    fun getLastSyncTimestamp(): String? {
        if (!isLoaded) loadCache()
        return metadata?.lastSyncTimestamp
    }

    fun hasWorkouts(): Boolean {
        if (!isLoaded) loadCache()
        return workoutCache.isNotEmpty()
    }

    fun getWorkoutCount(): Int {
        if (!isLoaded) loadCache()
        return workoutCache.size
    }

    fun getAllWorkouts(): List<WorkoutConfig> {
        if (!isLoaded) loadCache()
        return workoutCache.values.map { it.config }
    }

    fun getWorkout(slug: String): WorkoutConfig? {
        if (!isLoaded) loadCache()
        return workoutCache[slug]?.config
    }

    fun getWorkoutById(id: String): WorkoutConfig? {
        if (!isLoaded) loadCache()
        return workoutCache.values.find { it.id == id }?.config
    }

    fun getWorkoutSlugs(): List<String> {
        if (!isLoaded) loadCache()
        return workoutCache.keys.toList()
    }

    fun getCacheStats(): CacheStats {
        if (!isLoaded) loadCache()

        val totalSize = workoutsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sumOf { it.length() } ?: 0L

        return CacheStats(
            workoutCount = workoutCache.size,
            totalSizeBytes = totalSize + (metadataFile.takeIf { it.exists() }?.length() ?: 0),
            lastSyncTimestamp = metadata?.lastSyncTimestamp,
            serverVersion = metadata?.serverVersion
        )
    }

    data class CacheStats(
        val workoutCount: Int,
        val totalSizeBytes: Long,
        val lastSyncTimestamp: String?,
        val serverVersion: String?
    ) {
        fun getFormattedSize(): String {
            return when {
                totalSizeBytes < 1024 -> "$totalSizeBytes B"
                totalSizeBytes < 1024 * 1024 -> "${totalSizeBytes / 1024} KB"
                else -> "${totalSizeBytes / (1024 * 1024)} MB"
            }
        }
    }

    fun saveWorkouts(workouts: List<WorkoutConfigWithMeta>, isFullSync: Boolean) {
        if (isFullSync) {
            clearWorkoutsOnly()
        }

        for (workoutMeta in workouts) {
            try {
                if (!isFullSync) {
                    workoutCache.values.find { it.id == workoutMeta.id }?.let { old ->
                        if (old.slug != workoutMeta.slug) {
                            val oldFile = File(workoutsDir, "${old.slug}.json")
                            if (oldFile.exists()) oldFile.delete()
                            workoutCache.remove(old.slug)
                        }
                    }
                }

                val cached = CachedEntity(
                    id = workoutMeta.id,
                    slug = workoutMeta.slug,
                    updatedAt = workoutMeta.updatedAt,
                    config = workoutMeta.toWorkoutConfig()
                )

                val file = File(workoutsDir, "${cached.slug}.json")
                file.writeText(gson.toJson(cached))

                workoutCache[cached.slug] = cached
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid workout payload: slug=${workoutMeta.slug}", e)
            }
        }

        Log.d(TAG, "Saved ${workouts.size} workouts")
    }

    fun removeWorkouts(ids: List<String>) {
        val toRemove = workoutCache.values.filter { it.id in ids }

        for (cached in toRemove) {
            val file = File(workoutsDir, "${cached.slug}.json")
            if (file.exists()) {
                file.delete()
            }

            workoutCache.remove(cached.slug)
        }

        Log.d(TAG, "Removed ${toRemove.size} workouts")
    }

    fun saveMetadata(
        timestamp: String,
        workoutCount: Int,
        serverVersion: String,
        successful: Boolean = true
    ) {
        metadata = CacheMetadata(
            lastSyncTimestamp = timestamp,
            workoutCount = workoutCount,
            serverVersion = serverVersion,
            lastSyncSuccessful = successful
        )

        metadataFile.writeText(gson.toJson(metadata))
        Log.d(TAG, "Saved metadata: $metadata")
    }

    private fun clearWorkoutsOnly() {
        workoutsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }

        workoutCache.clear()
        Log.d(TAG, "Cleared workouts cache")
    }

    fun clearCache() {
        workoutsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }

        if (metadataFile.exists()) {
            metadataFile.delete()
        }

        workoutCache.clear()
        metadata = null
        isLoaded = false

        Log.d(TAG, "Cleared entire cache")
    }

    fun reload() {
        isLoaded = false
        workoutCache.clear()
        metadata = null
        loadCache()
    }
}
