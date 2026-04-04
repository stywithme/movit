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
    
    private val cacheDir: File = File(context.filesDir, CACHE_DIR)
    private val exercisesDir: File = File(cacheDir, EXERCISES_DIR)
    private val metadataFile: File = File(cacheDir, METADATA_FILE)
    
    // In-memory cache for fast access
    private var exerciseCache: MutableMap<String, CachedExercise> = mutableMapOf()
    private var metadata: CacheMetadata? = null
    private var isLoaded = false
    
    init {
        ensureDirectoriesExist()
    }
    
    // ==================== Data Classes ====================
    
    /**
     * Cache metadata for tracking sync state
     */
    data class CacheMetadata(
        val lastSyncTimestamp: String,
        val exerciseCount: Int,
        val serverVersion: String,
        val lastSyncSuccessful: Boolean = true
    )
    
    /**
     * Cached exercise with metadata
     */
    data class CachedExercise(
        val id: String,
        val slug: String,
        val updatedAt: String,
        val config: ExerciseConfig
    )
    
    // ==================== Initialization ====================
    
    private fun ensureDirectoriesExist() {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(TAG, "Created cache directory: ${cacheDir.absolutePath}")
        }
        if (!exercisesDir.exists()) {
            exercisesDir.mkdirs()
            Log.d(TAG, "Created exercises directory: ${exercisesDir.absolutePath}")
        }
    }
    
    /**
     * Load cache from disk into memory
     */
    fun loadCache() {
        if (isLoaded) return
        
        try {
            // Load metadata
            if (metadataFile.exists()) {
                metadata = gson.fromJson(metadataFile.readText(), CacheMetadata::class.java)
                Log.d(TAG, "Loaded metadata: $metadata")
            }
            
            // Load exercises
            exercisesDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.forEach { file ->
                    try {
                        val cached = gson.fromJson(file.readText(), CachedExercise::class.java)
                        // Sanitize Gson-null fields (Gson ignores Kotlin default values)
                        val sanitized = cached.copy(config = cached.config.sanitizeGsonDefaults())
                        // Restore fileName from slug (since it's @Transient and not serialized)
                        sanitized.config.fileName = cached.slug
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
    
    // ==================== Read Operations ====================
    
    /**
     * Get the last sync timestamp
     * @return ISO timestamp or null if never synced
     */
    fun getLastSyncTimestamp(): String? {
        if (!isLoaded) loadCache()
        return metadata?.lastSyncTimestamp
    }
    
    /**
     * Check if cache has any exercises
     */
    fun hasExercises(): Boolean {
        if (!isLoaded) loadCache()
        return exerciseCache.isNotEmpty()
    }

    fun getExerciseCount(): Int {
        if (!isLoaded) loadCache()
        return exerciseCache.size
    }
    
    /**
     * Get all cached exercises
     */
    fun getAllExercises(): List<ExerciseConfig> {
        if (!isLoaded) loadCache()
        return exerciseCache.values.map { it.config }
    }
    
    /**
     * Get exercise by slug
     */
    fun getExercise(slug: String): ExerciseConfig? {
        if (!isLoaded) loadCache()
        return exerciseCache[slug]?.config
    }
    
    /**
     * Get exercise by ID
     */
    fun getExerciseById(id: String): ExerciseConfig? {
        if (!isLoaded) loadCache()
        return exerciseCache.values.find { it.id == id }?.config
    }
    
    /**
     * Get all exercise slugs (file names)
     */
    fun getExerciseSlugs(): List<String> {
        if (!isLoaded) loadCache()
        return exerciseCache.keys.toList()
    }
    
    /**
     * Get cache statistics
     */
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
    
    // ==================== Write Operations ====================
    
    /**
     * Save exercises from sync response
     * 
     * @param exercises List of exercises with metadata from server
     * @param isFullSync Whether this is a full sync (clears cache first)
     */
    fun saveExercises(exercises: List<ExerciseConfigWithMeta>, isFullSync: Boolean) {
        if (isFullSync) {
            clearExercisesOnly()
        }
        
        for (exerciseMeta in exercises) {
            // Debug: Log reportMetrics from server
            Log.d(TAG, "Exercise ${exerciseMeta.slug} - reportMetrics from server: ${exerciseMeta.reportMetrics}")
            Log.d(TAG, "  - excluded: ${exerciseMeta.reportMetrics?.excluded}")
            
            val cached = CachedExercise(
                id = exerciseMeta.id,
                slug = exerciseMeta.slug,
                updatedAt = exerciseMeta.updatedAt,
                config = exerciseMeta.toExerciseConfig()
            )
            
            // Debug: Log reportMetrics after conversion
            Log.d(TAG, "  - after toExerciseConfig: ${cached.config.reportMetrics}")
            Log.d(TAG, "  - excluded after: ${cached.config.reportMetrics?.excluded}")
            
            // Save to disk
            val file = File(exercisesDir, "${cached.slug}.json")
            file.writeText(gson.toJson(cached))
            
            // Update memory cache
            exerciseCache[cached.slug] = cached
        }
        
        Log.d(TAG, "Saved ${exercises.size} exercises")
    }
    
    /**
     * Remove exercises by IDs
     */
    fun removeExercises(ids: List<String>) {
        val toRemove = exerciseCache.values.filter { it.id in ids }
        
        for (cached in toRemove) {
            // Delete from disk
            val file = File(exercisesDir, "${cached.slug}.json")
            if (file.exists()) {
                file.delete()
            }
            
            // Remove from memory cache
            exerciseCache.remove(cached.slug)
        }
        
        Log.d(TAG, "Removed ${toRemove.size} exercises")
    }
    
    /**
     * Save sync metadata
     */
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
    
    // ==================== Clear Operations ====================
    
    /**
     * Clear exercises only (keep metadata)
     */
    private fun clearExercisesOnly() {
        exercisesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }
        
        exerciseCache.clear()
        Log.d(TAG, "Cleared exercises cache")
    }
    
    /**
     * Clear entire cache (exercises and metadata)
     */
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
    
    /**
     * Force reload from disk
     */
    fun reload() {
        isLoaded = false
        exerciseCache.clear()
        metadata = null
        loadCache()
    }
}
