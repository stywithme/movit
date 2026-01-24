package com.trainingvalidator.poc.storage

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.ExerciseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ExerciseRepository
 * 
 * Single source of truth for exercise data in the app.
 * Prioritizes cached data from server sync, falls back to bundled assets.
 * 
 * Data Source Priority:
 * 1. Cached exercises (from server sync)
 * 2. Bundled assets (for offline-first experience)
 * 
 * Usage:
 * ```kotlin
 * val repository = ExerciseRepository.getInstance(context)
 * 
 * // Get all exercises
 * val exercises = repository.getAllExercises()
 * 
 * // Get specific exercise
 * val squat = repository.getExercise("squat")
 * 
 * // Observe loading state
 * repository.isLoading.collect { loading -> ... }
 * ```
 */
class ExerciseRepository private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ExerciseRepository"
        
        @Volatile
        private var instance: ExerciseRepository? = null
        
        /**
         * Get singleton instance
         */
        fun getInstance(context: Context): ExerciseRepository {
            return instance ?: synchronized(this) {
                instance ?: ExerciseRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    // Cache managers
    private val exerciseCache = ExerciseCacheManager(context)
    private val audioCache = AudioCacheManager(context)
    private val syncManager = SyncManager(context, exerciseCache, audioCache)
    
    // State flows for UI observation
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _exercises = MutableStateFlow<List<ExerciseConfig>>(emptyList())
    val exercises: StateFlow<List<ExerciseConfig>> = _exercises
    
    private val _lastSyncResult = MutableStateFlow<SyncManager.SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncManager.SyncResult?> = _lastSyncResult
    
    // In-memory cache for fast lookups
    private var exerciseMap: Map<String, ExerciseConfig> = emptyMap()
    private var isInitialized = false
    
    // ==================== Initialization ====================
    
    /**
     * Initialize repository and load exercises.
     * Call this on app startup.
     * 
     * @param autoSync Whether to attempt server sync
     */
    suspend fun initialize(autoSync: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        _isLoading.value = true
        
        try {
            // First, try to load from cache for fast startup
            exerciseCache.loadCache()
            
            if (exerciseCache.hasExercises()) {
                loadFromCache()
                Log.d(TAG, "Loaded ${_exercises.value.size} exercises from cache")
            } else {
                // Fall back to bundled assets
                loadFromAssets()
                Log.d(TAG, "Loaded ${_exercises.value.size} exercises from assets")
            }
            
            isInitialized = true
            
            // Attempt sync in background
            if (autoSync) {
                val result = syncManager.syncIfNeeded()
                _lastSyncResult.value = result
                
                // Reload if sync was successful
                if (result is SyncManager.SyncResult.Success && result.exercisesUpdated > 0) {
                    loadFromCache()
                    Log.d(TAG, "Reloaded ${_exercises.value.size} exercises after sync")
                }
                
                // Start background audio download (non-blocking)
                if (syncManager.hasPendingAudioDownloads()) {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val downloaded = syncManager.downloadPendingAudio()
                            Log.d(TAG, "Background audio download completed: $downloaded files")
                        } catch (e: Exception) {
                            Log.w(TAG, "Background audio download failed", e)
                        }
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            
            // Last resort: load from assets
            if (_exercises.value.isEmpty()) {
                loadFromAssets()
            }
            
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    // ==================== Read Operations ====================
    
    /**
     * Get all available exercises
     */
    fun getAllExercises(): List<ExerciseConfig> {
        return _exercises.value
    }
    
    /**
     * Get exercise by slug/fileName
     */
    fun getExercise(slug: String): ExerciseConfig? {
        return exerciseMap[slug]
    }
    
    /**
     * Get exercise by ID (from server)
     */
    fun getExerciseById(id: String): ExerciseConfig? {
        return exerciseCache.getExerciseById(id)
    }
    
    /**
     * Get list of available exercise names (slugs)
     */
    fun getExerciseNames(): List<String> {
        return exerciseMap.keys.toList()
    }
    
    /**
     * Check if an exercise exists
     */
    fun hasExercise(slug: String): Boolean {
        return exerciseMap.containsKey(slug)
    }
    
    /**
     * Get exercises by category
     */
    fun getExercisesByCategory(categoryCode: String): List<ExerciseConfig> {
        return _exercises.value.filter { it.category.code == categoryCode }
    }
    
    /**
     * Search exercises by name
     */
    fun searchExercises(query: String): List<ExerciseConfig> {
        val lowerQuery = query.lowercase()
        return _exercises.value.filter { exercise ->
            exercise.name.en.lowercase().contains(lowerQuery) ||
            exercise.name.ar.contains(query)
        }
    }
    
    // ==================== Sync Operations ====================
    
    /**
     * Perform manual refresh (full sync)
     * 
     * This performs a full sync and downloads any new audio files.
     */
    suspend fun refresh(): SyncManager.SyncResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        
        try {
            val result = syncManager.fullRefresh()
            _lastSyncResult.value = result
            
            if (result is SyncManager.SyncResult.Success) {
                // Reload exercises from cache
                loadFromCache()
                Log.d(TAG, "Reloaded ${_exercises.value.size} exercises after refresh")
                
                // Download new audio files (non-blocking background task)
                if (syncManager.hasPendingAudioDownloads()) {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val downloaded = syncManager.downloadPendingAudio()
                            Log.d(TAG, "Downloaded $downloaded audio files after refresh")
                        } catch (e: Exception) {
                            Log.w(TAG, "Audio download after refresh failed", e)
                        }
                    }
                }
            }
            
            result
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Check for updates (incremental sync)
     * 
     * Forces an immediate sync check, bypassing the minimum interval.
     * Downloads any new audio files in the background.
     */
    suspend fun checkForUpdates(): SyncManager.SyncResult = withContext(Dispatchers.IO) {
        val result = syncManager.syncIfNeeded(forceCheck = true)
        _lastSyncResult.value = result
        
        if (result is SyncManager.SyncResult.Success && result.exercisesUpdated > 0) {
            loadFromCache()
            Log.d(TAG, "Reloaded ${_exercises.value.size} exercises after update check")
        }
        
        // Download any new audio files
        if (result is SyncManager.SyncResult.Success && syncManager.hasPendingAudioDownloads()) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    val downloaded = syncManager.downloadPendingAudio()
                    Log.d(TAG, "Downloaded $downloaded audio files after update check")
                } catch (e: Exception) {
                    Log.w(TAG, "Audio download after update check failed", e)
                }
            }
        }
        
        result
    }
    
    /**
     * Get sync status for UI
     */
    fun getSyncStatus(): SyncManager.SyncStatus {
        return syncManager.getSyncStatus()
    }
    
    // ==================== Audio Operations ====================
    
    /**
     * Get AudioCacheManager for audio playback
     */
    fun getAudioCache(): AudioCacheManager = audioCache
    
    // ==================== Private Methods ====================
    
    /**
     * Load exercises from cache into memory
     */
    private fun loadFromCache() {
        val cached = exerciseCache.getAllExercises()
        _exercises.value = cached
        exerciseMap = cached.associateBy { it.fileName }
    }
    
    /**
     * Load exercises from bundled assets
     */
    private fun loadFromAssets() {
        val assets: AssetManager = context.assets
        val exercises = ExerciseLoader.loadAll(assets)
        _exercises.value = exercises
        exerciseMap = exercises.associateBy { it.fileName }
    }
    
    // ==================== Cache Management ====================
    
    /**
     * Clear all caches and reload from assets
     */
    suspend fun clearCacheAndReload() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        
        try {
            exerciseCache.clearCache()
            audioCache.clearCache()
            loadFromAssets()
            isInitialized = false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val exerciseStats = exerciseCache.getCacheStats()
        val audioStats = audioCache.getCacheStats()
        
        return CacheStats(
            exerciseCount = exerciseStats.exerciseCount,
            exerciseCacheSize = exerciseStats.totalSizeBytes,
            audioFileCount = audioStats.totalFiles,
            audioCacheSize = audioStats.totalSizeBytes,
            lastSyncTimestamp = exerciseStats.lastSyncTimestamp
        )
    }
    
    data class CacheStats(
        val exerciseCount: Int,
        val exerciseCacheSize: Long,
        val audioFileCount: Int,
        val audioCacheSize: Long,
        val lastSyncTimestamp: String?
    ) {
        fun getFormattedTotalSize(): String {
            val total = exerciseCacheSize + audioCacheSize
            return when {
                total < 1024 -> "$total B"
                total < 1024 * 1024 -> "${total / 1024} KB"
                else -> String.format("%.1f MB", total / (1024.0 * 1024.0))
            }
        }
    }
}
