package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.network.ProgramConfigWithMeta
import com.trainingvalidator.poc.training.models.ProgramConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * ProgramCacheManager
 *
 * Manages local caching of programs synced from the server.
 */
class ProgramCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "ProgramCacheManager"
        private const val CACHE_DIR = "program_cache"
        private const val PROGRAMS_DIR = "programs"
        private const val METADATA_FILE = "metadata.json"
    }

    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .setPrettyPrinting()
        .create()

    private val cachedEntityType =
        TypeToken.getParameterized(CachedEntity::class.java, ProgramConfig::class.java).type

    private val cacheDir: File = File(context.filesDir, CACHE_DIR)
    private val programsDir: File = File(cacheDir, PROGRAMS_DIR)
    private val metadataFile: File = File(cacheDir, METADATA_FILE)

    private val programCache: ConcurrentHashMap<String, CachedEntity<ProgramConfig>> = ConcurrentHashMap()
    private var metadata: CacheMetadata? = null
    private var isLoaded = false

    init {
        ensureDirectoriesExist()
    }

    data class CacheMetadata(
        val lastSyncTimestamp: String,
        val programCount: Int,
        val serverVersion: String,
        val lastSyncSuccessful: Boolean = true
    )

    private fun ensureDirectoriesExist() {
        JsonEntityCacheSupport.ensureDirs(cacheDir, programsDir, TAG)
    }

    fun loadCache() {
        if (isLoaded) return

        try {
            if (metadataFile.exists()) {
                metadata = gson.fromJson(metadataFile.readText(), CacheMetadata::class.java)
                Log.d(TAG, "Loaded metadata: $metadata")
            }

            programsDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.forEach { file ->
                    try {
                        val cached: CachedEntity<ProgramConfig> =
                            gson.fromJson(file.readText(), cachedEntityType)
                        programCache[cached.slug] = cached
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load program: ${file.name}", e)
                    }
                }

            isLoaded = true
            Log.d(TAG, "Loaded ${programCache.size} programs from cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
        }
    }

    fun getLastSyncTimestamp(): String? {
        if (!isLoaded) loadCache()
        return metadata?.lastSyncTimestamp
    }

    fun hasPrograms(): Boolean {
        if (!isLoaded) loadCache()
        return programCache.isNotEmpty()
    }

    fun getProgramCount(): Int {
        if (!isLoaded) loadCache()
        return programCache.size
    }

    fun getAllPrograms(): List<ProgramConfig> {
        if (!isLoaded) loadCache()
        return programCache.values.map { it.config }
    }

    fun getProgram(slug: String): ProgramConfig? {
        if (!isLoaded) loadCache()
        return programCache[slug]?.config
    }

    fun getProgramById(id: String): ProgramConfig? {
        if (!isLoaded) loadCache()
        return programCache.values.find { it.id == id }?.config
    }

    fun getProgramSlugs(): List<String> {
        if (!isLoaded) loadCache()
        return programCache.keys.toList()
    }

    fun getCacheStats(): CacheStats {
        if (!isLoaded) loadCache()

        val totalSize = programsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sumOf { it.length() } ?: 0L

        return CacheStats(
            programCount = programCache.size,
            totalSizeBytes = totalSize + (metadataFile.takeIf { it.exists() }?.length() ?: 0),
            lastSyncTimestamp = metadata?.lastSyncTimestamp,
            serverVersion = metadata?.serverVersion
        )
    }

    data class CacheStats(
        val programCount: Int,
        val totalSizeBytes: Long,
        val lastSyncTimestamp: String?,
        val serverVersion: String?
    )

    fun savePrograms(programs: List<ProgramConfigWithMeta>, isFullSync: Boolean) {
        if (isFullSync) {
            clearProgramsOnly()
        }

        for (programMeta in programs) {
            try {
                val cached = CachedEntity(
                    id = programMeta.id,
                    slug = programMeta.slug,
                    updatedAt = programMeta.updatedAt,
                    config = programMeta.toProgramConfig()
                )

                val file = File(programsDir, "${cached.slug}.json")
                file.writeText(gson.toJson(cached))

                programCache[cached.slug] = cached
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid program payload: slug=${programMeta.slug}", e)
            }
        }

        Log.d(TAG, "Saved ${programs.size} programs")
    }

    fun removePrograms(ids: List<String>) {
        val toRemove = programCache.values.filter { it.id in ids }

        for (cached in toRemove) {
            val file = File(programsDir, "${cached.slug}.json")
            if (file.exists()) {
                file.delete()
            }
            programCache.remove(cached.slug)
        }

        Log.d(TAG, "Removed ${toRemove.size} programs")
    }

    fun saveMetadata(
        timestamp: String,
        programCount: Int,
        serverVersion: String,
        successful: Boolean = true
    ) {
        metadata = CacheMetadata(
            lastSyncTimestamp = timestamp,
            programCount = programCount,
            serverVersion = serverVersion,
            lastSyncSuccessful = successful
        )

        metadataFile.writeText(gson.toJson(metadata))
        Log.d(TAG, "Saved metadata: $metadata")
    }

    private fun clearProgramsOnly() {
        programsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }

        programCache.clear()
        Log.d(TAG, "Cleared programs cache")
    }

    fun clearCache() {
        programsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { it.delete() }

        if (metadataFile.exists()) {
            metadataFile.delete()
        }

        programCache.clear()
        metadata = null
        isLoaded = false

        Log.d(TAG, "Cleared entire cache")
    }

    fun reload() {
        isLoaded = false
        programCache.clear()
        metadata = null
        loadCache()
    }
}
