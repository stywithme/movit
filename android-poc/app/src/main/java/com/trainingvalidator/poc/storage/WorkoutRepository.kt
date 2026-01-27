package com.trainingvalidator.poc.storage

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.trainingvalidator.poc.training.loader.WorkoutLoader
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.models.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * WorkoutRepository
 * 
 * Single source of truth for workout data in the app.
 * Prioritizes cached data from server sync, falls back to bundled assets.
 * 
 * Data Source Priority:
 * 1. Cached workouts (from server sync)
 * 2. Bundled assets (for offline-first experience)
 * 
 * Usage:
 * ```kotlin
 * val repository = WorkoutRepository.getInstance(context)
 * 
 * // Get all workouts
 * val workouts = repository.getAllWorkouts()
 * 
 * // Get specific workout
 * val circuit = repository.getWorkout("core_burner")
 * 
 * // Observe loading state
 * repository.isLoading.collect { loading -> ... }
 * ```
 */
class WorkoutRepository private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkoutRepository"
        
        @Volatile
        private var instance: WorkoutRepository? = null
        
        /**
         * Get singleton instance
         */
        fun getInstance(context: Context): WorkoutRepository {
            return instance ?: synchronized(this) {
                instance ?: WorkoutRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    // Cache manager
    private val workoutCache = WorkoutCacheManager(context)
    
    // State flows for UI observation
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _workouts = MutableStateFlow<List<WorkoutConfig>>(emptyList())
    val workouts: StateFlow<List<WorkoutConfig>> = _workouts
    
    // In-memory cache for fast lookups
    private var workoutMap: Map<String, WorkoutConfig> = emptyMap()
    private var isInitialized = false
    
    // ==================== Initialization ====================
    
    /**
     * Initialize repository and load workouts.
     * Call this on app startup after ExerciseRepository.initialize()
     * 
     * Note: This does NOT trigger sync - use SyncManager for that
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        _isLoading.value = true
        
        try {
            // First, try to load from cache for fast startup
            workoutCache.loadCache()
            
            if (workoutCache.hasWorkouts()) {
                loadFromCache()
                Log.d(TAG, "Loaded ${_workouts.value.size} workouts from cache")
            } else {
                // Fall back to bundled assets
                loadFromAssets()
                Log.d(TAG, "Loaded ${_workouts.value.size} workouts from assets")
            }
            
            isInitialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            
            // Last resort: load from assets
            if (_workouts.value.isEmpty()) {
                loadFromAssets()
            }
            
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Reload workouts from cache after sync
     * Called by SyncManager after successful sync
     */
    suspend fun reloadFromCache() = withContext(Dispatchers.IO) {
        workoutCache.reload()
        loadFromCache()
        Log.d(TAG, "Reloaded ${_workouts.value.size} workouts from cache")
    }
    
    // ==================== Read Operations ====================
    
    /**
     * Get all available workouts
     */
    fun getAllWorkouts(): List<WorkoutConfig> {
        return _workouts.value
    }
    
    /**
     * Get workout by slug/fileName
     */
    fun getWorkout(slug: String): WorkoutConfig? {
        return workoutMap[slug]
    }
    
    /**
     * Get workout by ID (from server)
     */
    fun getWorkoutById(id: String): WorkoutConfig? {
        return workoutCache.getWorkoutById(id)
    }
    
    /**
     * Get list of available workout names (slugs)
     */
    fun getWorkoutNames(): List<String> {
        return workoutMap.keys.toList()
    }
    
    /**
     * Check if a workout exists
     */
    fun hasWorkout(slug: String): Boolean {
        return workoutMap.containsKey(slug)
    }
    
    /**
     * Get workouts by type
     */
    fun getWorkoutsByType(type: WorkoutType): List<WorkoutConfig> {
        return _workouts.value.filter { it.type == type }
    }
    
    /**
     * Search workouts by name
     */
    fun searchWorkouts(query: String): List<WorkoutConfig> {
        val lowerQuery = query.lowercase()
        return _workouts.value.filter { workout ->
            workout.name.en.lowercase().contains(lowerQuery) ||
            workout.name.ar.contains(query)
        }
    }
    
    // ==================== Cache Manager Access ====================
    
    /**
     * Get WorkoutCacheManager for SyncManager
     */
    fun getCacheManager(): WorkoutCacheManager = workoutCache
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): WorkoutCacheManager.CacheStats {
        return workoutCache.getCacheStats()
    }
    
    // ==================== Private Methods ====================
    
    /**
     * Load workouts from cache into memory
     */
    private fun loadFromCache() {
        val cached = workoutCache.getAllWorkouts()
        _workouts.value = cached
        workoutMap = cached.associateBy { it.fileName }
    }
    
    /**
     * Load workouts from bundled assets
     */
    private fun loadFromAssets() {
        val assets: AssetManager = context.assets
        val workouts = WorkoutLoader.loadAll(assets)
        _workouts.value = workouts
        workoutMap = workouts.associateBy { it.fileName }
    }
    
    // ==================== Cache Management ====================
    
    /**
     * Clear cache and reload from assets
     */
    suspend fun clearCacheAndReload() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        
        try {
            workoutCache.clearCache()
            loadFromAssets()
            isInitialized = false
        } finally {
            _isLoading.value = false
        }
    }
}
