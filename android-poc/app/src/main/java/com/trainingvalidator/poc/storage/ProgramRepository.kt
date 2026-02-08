package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.training.models.ProgramConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * ProgramRepository
 *
 * Single source of truth for program data in the app.
 */
class ProgramRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ProgramRepository"

        @Volatile
        private var instance: ProgramRepository? = null

        fun getInstance(context: Context): ProgramRepository {
            return instance ?: synchronized(this) {
                instance ?: ProgramRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val programCache = ProgramCacheManager(context)
    private val userProgramStore = UserProgramStore(context)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _programs = MutableStateFlow<List<ProgramConfig>>(emptyList())
    val programs: StateFlow<List<ProgramConfig>> = _programs

    private var programMap: Map<String, ProgramConfig> = emptyMap()
    private var isInitialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && _programs.value.isNotEmpty()) return@withContext true

        _isLoading.value = true

        try {
            programCache.loadCache()

            if (programCache.hasPrograms()) {
                loadFromCache()
                Log.d(TAG, "Loaded ${_programs.value.size} programs from cache")
                isInitialized = true
            } else {
                Log.w(TAG, "No programs in cache - sync required")
            }

            isInitialized = true
            _programs.value.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            if (_programs.value.isNotEmpty()) {
                isInitialized = true
                return@withContext true
            }
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun reloadFromCache() = withContext(Dispatchers.IO) {
        programCache.reload()
        loadFromCache()
        Log.d(TAG, "Reloaded ${_programs.value.size} programs from cache")
    }

    fun getAllPrograms(): List<ProgramConfig> {
        return _programs.value
    }

    fun getProgram(slug: String): ProgramConfig? {
        return programMap[slug]
    }

    fun getProgramById(id: String): ProgramConfig? {
        return programCache.getProgramById(id)
    }

    fun getActiveProgram(): ProgramConfig? {
        val active = userProgramStore.getActiveUserProgram()
        val programId = active?.programId ?: return null
        return getProgramById(programId)
    }

    fun getProgramSlugs(): List<String> {
        return programMap.keys.toList()
    }

    fun hasProgram(slug: String): Boolean {
        return programMap.containsKey(slug)
    }

    fun getCacheManager(): ProgramCacheManager = programCache

    fun getCacheStats(): ProgramCacheManager.CacheStats {
        return programCache.getCacheStats()
    }

    private fun loadFromCache() {
        val cached = programCache.getAllPrograms()
        _programs.value = cached
        programMap = cached.associateBy { it.slug }
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            programCache.clearCache()
            _programs.value = emptyList()
            programMap = emptyMap()
            isInitialized = false
            Log.d(TAG, "Program cache cleared - sync required to reload")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear program cache", e)
            false
        } finally {
            _isLoading.value = false
        }
    }
}
