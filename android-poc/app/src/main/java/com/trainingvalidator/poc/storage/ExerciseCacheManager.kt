package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.network.ExerciseConfigWithMeta
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessageValueTypeAdapter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * ExerciseCacheManager
 *
 * Manages local caching of exercises synced from the server.
 * Stores exercises as individual JSON files for easy access and updates.
 *
 * Storage structure:
 * /data/data/[package]/files/exercise_cache/
 *   ├── metadata.json          (sync metadata)
 *   ├── exercises/
 *   │   ├── [slug].json       (individual exercise files)
 *   │   └── ...
 */
class ExerciseCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "ExerciseCacheManager"
        private const val CACHE_DIR = "exercise_cache"
        private const val EXERCISES_DIR = "exercises"
        private const val METADATA_FILE = "metadata.json"
    }

    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .registerTypeAdapter(StateMessageValue::class.java, StateMessageValueTypeAdapter())
        .setPrettyPrinting()
        .create()

    private val cachedEntityType =
        TypeToken.getParameterized(CachedEntity::class.java, ExerciseConfig::class.java).type

    private val cacheDir: File = File(context.filesDir, CACHE_DIR)
    private val exercisesDir: File = File(cacheDir, EXERCISES_DIR)
    private val metadataFile: File = File(cacheDir, METADATA_FILE)

    private val exerciseCache: ConcurrentHashMap<String, CachedEntity<ExerciseConfig>> = ConcurrentHashMap()
    private var metadata: CacheMetadata? = null
    private var isLoaded = false

    init {
        ensureDirectoriesExist()
    }

    data class CacheMetadata(
        val lastSyncTimestamp: String,
        val exerciseCount: Int,
        val serverVersion: String,
        val lastSyncSuccessful: Boolean = true
    )

    private fun ensureDirectoriesExist() {
        JsonEntityCacheSupport.ensureDirs(cacheDir, exercisesDir, TAG)
    }

    fun loadCache() {
        if (isLoaded) return

        try {
            if (metadataFile.exists()) {
                metadata = gson.fromJson(metadataFile.readText(), CacheMetadata::class.java)
                Log.d(TAG, "Loaded metadata: $metadata")
            }

            exercisesDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.forEach { file ->
                    try {
                        val cached: CachedEntity<ExerciseConfig> =
                            gson.fromJson(file.readText(), cachedEntityType)
                        val sanitized = cached.copy(config = cached.config.sanitizeGsonDefaults())
                        sanitized.config.fileName = cached.slug
                        val cacheIssues = sanitized.config.validationIssues()
                        if (cacheIssues.isNotEmpty()) {
                            Log.w(
                                TAG,
                                "ExerciseConfig validation for cached slug=${sanitized.slug}: ${cacheIssues.joinToString("; ")}"
                            )
                        }
                        exerciseCache[sanitized.slug] = sanitized
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load exercise: ${file.name}", e)
                    }
                }

            isLoaded = true
            Log.d(TAG, "Loaded ${exerciseCache.size} exercises from cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
        }
    }

    fun getLastSyncTimestamp(): String? {
        if (!isLoaded) loadCache()
        return metadata?.lastSyncTimestamp
    }

    fun hasExercises(): Boolean {
        if (!isLoaded) loadCache()
        return exerciseCache.isNotEmpty()
    }

    fun getExerciseCount(): Int {
        if (!isLoaded) loadCache()
        return exerciseCache.size
    }

    fun getAllExercises(): List<ExerciseConfig> {
        if (!isLoaded) loadCache()
        return exerciseCache.values.map { it.config }
    }

    /**
     * All cached exercises as server-shaped meta for message resolution / re-merge.
     */
    fun getAllCachedAsExerciseMeta(): List<ExerciseConfigWithMeta> {
        if (!isLoaded) loadCache()
        return exerciseCache.values.map { cached ->
            ExerciseConfigWithMeta.fromExerciseConfig(
                id = cached.id,
                slug = cached.slug,
                updatedAt = cached.updatedAt,
                config = cached.config
            )
        }
    }

    fun getExercise(slug: String): ExerciseConfig? {
        if (!isLoaded) loadCache()
        return exerciseCache[slug]?.config
    }

    /** Server UUID for API calls (e.g. exercise preferences). */
    fun getServerIdForSlug(slug: String): String? {
        if (!isLoaded) loadCache()
        return exerciseCache[slug]?.id
    }

    fun getExerciseById(id: String): ExerciseConfig? {
        if (!isLoaded) loadCache()
        return exerciseCache.values.find { it.id == id }?.config
    }

    fun getExerciseSlugs(): List<String> {
        if (!isLoaded) loadCache()
        return exerciseCache.keys.toList()
    }

    fun getCacheStats(): CacheStats {
        if (!isLoaded) loadCache()

        val totalSize = exercisesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sumOf { it.length() } ?: 0L

        return CacheStats(
            exerciseCount = exerciseCache.size,
            totalSizeBytes = totalSize + (metadataFile.takeIf { it.exists() }?.length() ?: 0),
            lastSyncTimestamp = metadata?.lastSyncTimestamp,
            serverVersion = metadata?.serverVersion
        )
    }

    data class CacheStats(
        val exerciseCount: Int,
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

    fun saveExercises(exercises: List<ExerciseConfigWithMeta>, isFullSync: Boolean) {
        if (isFullSync) {
            clearExercisesOnly()
        }

        for (exerciseMeta in exercises) {
            try {
                val cached = CachedEntity(
                    id = exerciseMeta.id,
                    slug = exerciseMeta.slug,
                    updatedAt = exerciseMeta.updatedAt,
                    config = exerciseMeta.toExerciseConfig()
                )

                val file = File(exercisesDir, "${cached.slug}.json")
                file.writeText(gson.toJson(cached))

                exerciseCache[cached.slug] = cached
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid exercise payload: slug=${exerciseMeta.slug}", e)
            }
        }

        Log.d(TAG, "Saved ${exercises.size} exercises")
    }

    fun removeExercises(ids: List<String>) {
        val toRemove = exerciseCache.values.filter { it.id in ids }

        for (cached in toRemove) {
            val file = File(exercisesDir, "${cached.slug}.json")
            if (file.exists()) {
                file.delete()
            }

            exerciseCache.remove(cached.slug)
        }

        Log.d(TAG, "Removed ${toRemove.size} exercises")
    }

    fun saveMetadata(
        timestamp: String,
        exerciseCount: Int,
        serverVersion: String,
        successful: Boolean = true
    ) {
        metadata = CacheMetadata(
            lastSyncTimestamp = timestamp,
            exerciseCount = exerciseCount,
            serverVersion = serverVersion,
            lastSyncSuccessful = successful
        )

        metadataFile.writeText(gson.toJson(metadata))
        Log.d(TAG, "Saved metadata: $metadata")
    }

    private fun clearExercisesOnly() {
        exercisesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }

        exerciseCache.clear()
        Log.d(TAG, "Cleared exercises cache")
    }

    fun clearCache() {
        exercisesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }

        if (metadataFile.exists()) {
            metadataFile.delete()
        }

        exerciseCache.clear()
        metadata = null
        isLoaded = false

        Log.d(TAG, "Cleared entire cache")
    }

    fun reload() {
        isLoaded = false
        exerciseCache.clear()
        metadata = null
        loadCache()
    }
}
