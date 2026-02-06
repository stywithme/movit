package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
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
 * Uses offline-first cache strategy with backend sync.
 * 
 * Data Source Priority:
 * 1. Cached workouts (from server sync) - offline first
 * 2. Fetch from backend and update cache
 * 3. If both fail, no workouts available
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
     * Initialize repository and load workouts from cache.
     * Call this on app startup after ExerciseRepository.initialize()
     * 
     * Note: This does NOT trigger sync - use SyncManager for that.
     * Workouts are synced as part of ExerciseRepository.initialize() sync.
     * 
     * @return true if workouts are available, false if cache is empty
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && _workouts.value.isNotEmpty()) return@withContext true
        
        _isLoading.value = true
        
        try {
            // Load from cache (synced from backend)
            workoutCache.loadCache()
            
            if (workoutCache.hasWorkouts()) {
                loadFromCache()
                Log.d(TAG, "Loaded ${_workouts.value.size} workouts from cache")
                isInitialized = true
            } else {
                Log.w(TAG, "No workouts in cache - sync required")
            }
            
            isInitialized = true
            
            // Return false if no workouts available
            if (_workouts.value.isEmpty()) {
                Log.e(TAG, "No workouts available - cache empty and sync needed")
                return@withContext false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            
            // If we have cached data, still return true
            if (_workouts.value.isNotEmpty()) {
                isInitialized = true
                return@withContext true
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
    
    // ==================== Cache Management ====================
    
    /**
     * Clear cache. After clearing, sync from backend is required.
     * 
     * @return true if cache was cleared successfully
     */
    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        _isLoading.value = true
        
        try {
            workoutCache.clearCache()
            _workouts.value = emptyList()
            workoutMap = emptyMap()
            isInitialized = false
            Log.d(TAG, "Workout cache cleared - sync required to reload")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear workout cache", e)
            false
        } finally {
            _isLoading.value = false
        }
    }
}
