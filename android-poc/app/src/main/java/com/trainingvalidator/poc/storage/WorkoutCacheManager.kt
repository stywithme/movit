package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.network.WorkoutConfigWithMeta
import com.trainingvalidator.poc.training.models.WorkoutConfig
import java.io.File

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
    
    private val cacheDir: File = File(context.filesDir, CACHE_DIR)
    private val workoutsDir: File = File(cacheDir, WORKOUTS_DIR)
    private val metadataFile: File = File(cacheDir, METADATA_FILE)
    
    // In-memory cache for fast access
    private var workoutCache: MutableMap<String, CachedWorkout> = mutableMapOf()
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
        val workoutCount: Int,
        val serverVersion: String,
        val lastSyncSuccessful: Boolean = true
    )
    
    /**
     * Cached workout with metadata
     */
    data class CachedWorkout(
        val id: String,
        val slug: String,
        val updatedAt: String,
        val config: WorkoutConfig
    )
    
    // ==================== Initialization ====================
    
    private fun ensureDirectoriesExist() {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(TAG, "Created cache directory: ${cacheDir.absolutePath}")
        }
        if (!workoutsDir.exists()) {
            workoutsDir.mkdirs()
            Log.d(TAG, "Created workouts directory: ${workoutsDir.absolutePath}")
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
            
            // Load workouts
            workoutsDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.forEach { file ->
                    try {
                        val cached = gson.fromJson(file.readText(), CachedWorkout::class.java)
                        // Restore fileName from slug (since it's @Transient and not serialized)
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
     * Check if cache has any workouts
     */
    fun hasWorkouts(): Boolean {
        if (!isLoaded) loadCache()
        return workoutCache.isNotEmpty()
    }
    
    /**
     * Get all cached workouts
     */
    fun getAllWorkouts(): List<WorkoutConfig> {
        if (!isLoaded) loadCache()
        return workoutCache.values.map { it.config }
    }
    
    /**
     * Get workout by slug
     */
    fun getWorkout(slug: String): WorkoutConfig? {
        if (!isLoaded) loadCache()
        return workoutCache[slug]?.config
    }
    
    /**
     * Get workout by ID
     */
    fun getWorkoutById(id: String): WorkoutConfig? {
        if (!isLoaded) loadCache()
        return workoutCache.values.find { it.id == id }?.config
    }
    
    /**
     * Get all workout slugs (file names)
     */
    fun getWorkoutSlugs(): List<String> {
        if (!isLoaded) loadCache()
        return workoutCache.keys.toList()
    }
    
    /**
     * Get cache statistics
     */
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
    
    // ==================== Write Operations ====================
    
    /**
     * Save workouts from sync response
     * 
     * @param workouts List of workouts with metadata from server
     * @param isFullSync Whether this is a full sync (clears cache first)
     */
    fun saveWorkouts(workouts: List<WorkoutConfigWithMeta>, isFullSync: Boolean) {
        if (isFullSync) {
            clearWorkoutsOnly()
        }
        
        for (workoutMeta in workouts) {
            val cached = CachedWorkout(
                id = workoutMeta.id,
                slug = workoutMeta.slug,
                updatedAt = workoutMeta.updatedAt,
                config = workoutMeta.toWorkoutConfig()
            )
            
            // Save to disk
            val file = File(workoutsDir, "${cached.slug}.json")
            file.writeText(gson.toJson(cached))
            
            // Update memory cache
            workoutCache[cached.slug] = cached
        }
        
        Log.d(TAG, "Saved ${workouts.size} workouts")
    }
    
    /**
     * Remove workouts by IDs
     */
    fun removeWorkouts(ids: List<String>) {
        val toRemove = workoutCache.values.filter { it.id in ids }
        
        for (cached in toRemove) {
            // Delete from disk
            val file = File(workoutsDir, "${cached.slug}.json")
            if (file.exists()) {
                file.delete()
            }
            
            // Remove from memory cache
            workoutCache.remove(cached.slug)
        }
        
        Log.d(TAG, "Removed ${toRemove.size} workouts")
    }
    
    /**
     * Save sync metadata
     */
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
    
    // ==================== Clear Operations ====================
    
    /**
     * Clear workouts only (keep metadata)
     */
    private fun clearWorkoutsOnly() {
        workoutsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }
        
        workoutCache.clear()
        Log.d(TAG, "Cleared workouts cache")
    }
    
    /**
     * Clear entire cache (workouts and metadata)
     */
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
    
    /**
     * Force reload from disk
     */
    fun reload() {
        isLoaded = false
        workoutCache.clear()
        metadata = null
        loadCache()
    }
}
