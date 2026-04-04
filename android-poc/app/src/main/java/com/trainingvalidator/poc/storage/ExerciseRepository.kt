package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.training.models.ExerciseConfig
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ExerciseRepository
 * 
 * Single source of truth for exercise data in the app.
 * Uses offline-first cache strategy with backend sync.
 * 
 * Data Source Priority:
 * 1. Cached exercises (from server sync) - offline first
 * 2. Fetch from backend and update cache
 * 3. If both fail, no exercises available
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
    
    // Get workout cache from WorkoutRepository for unified sync
    private val workoutCache: WorkoutCacheManager by lazy {
        WorkoutRepository.getInstance(context).getCacheManager()
    }

    private val programCache: ProgramCacheManager by lazy {
        ProgramRepository.getInstance(context).getCacheManager()
    }
    
    private val syncManager: SyncManager by lazy {
        SyncManager(context, exerciseCache, audioCache, workoutCache, programCache)
    }
    
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
     * Strategy: Cache First, then Backend Sync
     * - If cache has data: load from cache, sync in background
     * - If cache empty: attempt sync from backend
     * - If both fail: return false (no exercises available)
     * 
     * @param autoSync Whether to attempt server sync
     * @return true if exercises are available, false if no exercises found
     */
    suspend fun initialize(autoSync: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && _exercises.value.isNotEmpty()) return@withContext true
        
        _isLoading.value = true
        
        try {
            // First, try to load from cache for fast startup
            exerciseCache.loadCache()
            
            if (exerciseCache.hasExercises()) {
                loadFromCache()
                Log.d(TAG, "Loaded ${_exercises.value.size} exercises from cache")
                isInitialized = true
            } else {
                Log.d(TAG, "Cache is empty, will attempt sync from backend")
            }
            
            // Attempt sync from backend
            if (autoSync) {
                var result = syncManager.syncIfNeeded()

                // Auto-reconcile stale cache
                if (result is SyncManager.SyncResult.NeedsFullRefresh) {
                    Log.d(TAG, "Cache drift detected — performing full refresh")
                    result = syncManager.fullRefresh()
                }

                _lastSyncResult.value = result
                
                // Reload if sync was successful
                if (result is SyncManager.SyncResult.Success) {
                    if (result.exercisesUpdated > 0 || !isInitialized) {
                        loadFromCache()
                        Log.d(TAG, "Loaded ${_exercises.value.size} exercises after sync")
                    }
                    // Reload workouts if updated
                    if (result.workoutsUpdated > 0) {
                        WorkoutRepository.getInstance(context).reloadFromCache()
                        Log.d(TAG, "Triggered workout reload after sync")
                    }
                    if (result.programsUpdated > 0) {
                        ProgramRepository.getInstance(context).reloadFromCache()
                        Log.d(TAG, "Triggered program reload after sync")
                    }
                    isInitialized = true
                }
                
                // Start background audio download (non-blocking)
                if (syncManager.hasPendingAudioDownloads()) {
                    @OptIn(DelicateCoroutinesApi::class)
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
            
            // If cache was empty and sync didn't work, try one more sync
            if (_exercises.value.isEmpty() && !autoSync) {
                Log.w(TAG, "No exercises in cache and autoSync disabled, attempting forced sync")
                val result = syncManager.syncIfNeeded(forceCheck = true)
                _lastSyncResult.value = result
                if (result is SyncManager.SyncResult.Success) {
                    loadFromCache()
                    isInitialized = true
                }
            }
            
            isInitialized = true
            
            // Return false if no exercises available
            if (_exercises.value.isEmpty()) {
                Log.e(TAG, "No exercises available - cache empty and backend sync failed")
                return@withContext false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            
            // If we have cached data, still return true
            if (_exercises.value.isNotEmpty()) {
                isInitialized = true
                return@withContext true
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
                
                // Reload workouts if updated
                if (result.workoutsUpdated > 0) {
                    WorkoutRepository.getInstance(context).reloadFromCache()
                    Log.d(TAG, "Triggered workout reload after refresh")
                }
                if (result.programsUpdated > 0) {
                    ProgramRepository.getInstance(context).reloadFromCache()
                    Log.d(TAG, "Triggered program reload after refresh")
                }
                
                // Download new audio files (non-blocking background task)
                if (syncManager.hasPendingAudioDownloads()) {
                    @OptIn(DelicateCoroutinesApi::class)
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
        var result = syncManager.syncIfNeeded(forceCheck = true)

        if (result is SyncManager.SyncResult.NeedsFullRefresh) {
            Log.d(TAG, "Cache drift detected — performing full refresh")
            result = syncManager.fullRefresh()
        }

        _lastSyncResult.value = result
        
        if (result is SyncManager.SyncResult.Success) {
            if (result.exercisesUpdated > 0) {
                loadFromCache()
                Log.d(TAG, "Reloaded ${_exercises.value.size} exercises after update check")
            }
            // Reload workouts if updated
            if (result.workoutsUpdated > 0) {
                WorkoutRepository.getInstance(context).reloadFromCache()
                Log.d(TAG, "Triggered workout reload after update check")
            }
            if (result.programsUpdated > 0) {
                ProgramRepository.getInstance(context).reloadFromCache()
                Log.d(TAG, "Triggered program reload after update check")
            }
        }
        
        // Download any new audio files
        if (result is SyncManager.SyncResult.Success && syncManager.hasPendingAudioDownloads()) {
            @OptIn(DelicateCoroutinesApi::class)
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
    
    // ==================== Cache Management ====================
    
    /**
     * Clear all caches and sync from backend.
     * Use this when you need to force a complete refresh.
     * 
     * @return true if exercises were successfully loaded after refresh
     */
    suspend fun clearCacheAndReload(): Boolean = withContext(Dispatchers.IO) {
        _isLoading.value = true
        
        try {
            exerciseCache.clearCache()
            audioCache.clearCache()
            _exercises.value = emptyList()
            exerciseMap = emptyMap()
            isInitialized = false
            
            // Attempt to sync from backend after clearing cache
            val result = syncManager.fullRefresh()
            _lastSyncResult.value = result
            
            if (result is SyncManager.SyncResult.Success) {
                loadFromCache()
                isInitialized = true
                Log.d(TAG, "Reloaded ${_exercises.value.size} exercises after cache clear")
                return@withContext _exercises.value.isNotEmpty()
            }
            
            Log.e(TAG, "Failed to reload exercises after cache clear - backend sync failed")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache and reload", e)
            false
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
