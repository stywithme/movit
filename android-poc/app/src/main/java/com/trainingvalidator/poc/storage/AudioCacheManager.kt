package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.AudioFileInfo
import com.trainingvalidator.poc.network.AudioManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * AudioCacheManager
 * 
 * Manages local caching of TTS audio files for offline playback.
 * Downloads audio files in the background and provides access to cached files.
 * 
 * Storage structure:
 * /data/data/[package]/files/audio_cache/
 *   ├── ar/                    (Arabic audio files)
 *   │   ├── tts_ar_123.wav
 *   │   └── ...
 *   ├── en/                    (English audio files)
 *   │   ├── tts_en_123.wav
 *   │   └── ...
 */
class AudioCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioCacheManager"
        private const val CACHE_DIR = "audio_cache"
        private const val MANIFEST_FILE_NAME = "manifest.json"
        private const val MAX_CACHE_SIZE_MB = 100  // Max cache size in MB
    }
    
    private val cacheDir: File = File(context.filesDir, CACHE_DIR)
    private val arDir: File = File(cacheDir, "ar")
    private val enDir: File = File(cacheDir, "en")
    private val manifestFile: File = File(cacheDir, MANIFEST_FILE_NAME)
    
    private val gson = Gson()
    
    // Track download status
    private val downloadedFiles: MutableSet<String> = mutableSetOf()
    private val pendingDownloads: MutableSet<String> = mutableSetOf()
    
    /** Last known manifest from disk or sync (for resume after process death). */
    private var persistedAudioManifest: AudioManifest? = null
    private var persistedEffectiveBaseUrl: String? = null
    
    init {
        ensureDirectoriesExist()
        scanExistingFiles()
        loadPersistedManifestFromDisk()
    }
    
    // ==================== Initialization ====================
    
    private fun ensureDirectoriesExist() {
        listOf(cacheDir, arDir, enDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Log.d(TAG, "Created directory: ${dir.absolutePath}")
            }
        }
    }
    
    private fun scanExistingFiles() {
        downloadedFiles.clear()
        
        listOf(arDir, enDir).forEach { dir ->
            dir.listFiles()
                ?.filter { it.extension == "wav" }
                ?.forEach { downloadedFiles.add(it.name) }
        }
        
        Log.d(TAG, "Found ${downloadedFiles.size} cached audio files (ar: ${arDir.listFiles()?.size ?: 0}, en: ${enDir.listFiles()?.size ?: 0})")
    }
    
    private fun loadPersistedManifestFromDisk() {
        try {
            if (!manifestFile.exists()) return
            val json = manifestFile.readText()
            val wrapper = gson.fromJson(json, PersistedAudioManifestJson::class.java)
            if (wrapper.baseUrl.isNullOrBlank() || wrapper.files == null) return
            persistedEffectiveBaseUrl = wrapper.baseUrl
            persistedAudioManifest = AudioManifest(wrapper.baseUrl, wrapper.files)
            Log.d(TAG, "Loaded persisted audio manifest: ${wrapper.files.size} entries (baseUrl set)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load persisted audio manifest", e)
        }
    }
    
    /**
     * Persist manifest for download resume across app restarts.
     * [effectiveBaseUrl] should match the URL used for downloads (e.g. ApiConfig effective base).
     */
    fun persistAudioManifest(effectiveBaseUrl: String, manifest: AudioManifest) {
        persistedEffectiveBaseUrl = effectiveBaseUrl.trimEnd('/')
        persistedAudioManifest = AudioManifest(effectiveBaseUrl.trimEnd('/'), manifest.files)
        try {
            val json = gson.toJson(
                PersistedAudioManifestJson(
                    baseUrl = persistedEffectiveBaseUrl!!,
                    files = manifest.files
                )
            )
            manifestFile.writeText(json)
            Log.d(TAG, "Saved audio manifest to disk (${manifest.files.size} files)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist audio manifest", e)
        }
    }
    
    /**
     * Effective base URL + manifest last persisted or loaded (for SyncManager restore).
     */
    fun getPersistedManifestState(): Pair<String?, AudioManifest?> {
        return persistedEffectiveBaseUrl to persistedAudioManifest
    }
    
    /**
     * Files listed in the persisted/synced manifest that are not yet on disk.
     */
    fun getPendingDownloadCount(): Int {
        val m = persistedAudioManifest ?: return 0
        return m.files.count { !hasAudio(it.filename) }
    }
    
    private data class PersistedAudioManifestJson(
        val baseUrl: String,
        val files: List<AudioFileInfo>
    )
    
    /**
     * Rescan cached files (call after downloads complete)
     */
    fun rescanCache() {
        scanExistingFiles()
    }
    
    // ==================== Query Operations ====================
    
    /**
     * Check if an audio file is cached
     * 
     * @param filename The audio filename (e.g., "tts_ar_123.wav")
     */
    fun hasAudio(filename: String): Boolean {
        return downloadedFiles.contains(filename)
    }
    
    /**
     * Get the path to a cached audio file
     * 
     * @param filename The audio filename
     * @return File path or null if not cached
     */
    fun getAudioPath(filename: String): File? {
        val language = if (filename.contains("_ar_")) "ar" else "en"
        val dir = if (language == "ar") arDir else enDir
        val file = File(dir, filename)
        
        return if (file.exists()) file else null
    }
    
    /**
     * Get the path to a cached audio file from URL
     * 
     * @param url The audio URL (e.g., "/audio/tts/tts_ar_123.wav")
     * @return File path or null if not cached
     */
    fun getAudioPathFromUrl(url: String?): File? {
        if (url.isNullOrBlank()) return null
        val filename = url.substringAfterLast("/")
        return getAudioPath(filename)
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        var totalSize = 0L
        var arCount = 0
        var enCount = 0
        
        arDir.listFiles()?.forEach {
            if (it.extension == "wav") {
                totalSize += it.length()
                arCount++
            }
        }
        
        enDir.listFiles()?.forEach {
            if (it.extension == "wav") {
                totalSize += it.length()
                enCount++
            }
        }
        
        return CacheStats(
            totalFiles = arCount + enCount,
            arabicFiles = arCount,
            englishFiles = enCount,
            totalSizeBytes = totalSize
        )
    }
    
    data class CacheStats(
        val totalFiles: Int,
        val arabicFiles: Int,
        val englishFiles: Int,
        val totalSizeBytes: Long
    ) {
        fun getFormattedSize(): String {
            return when {
                totalSizeBytes < 1024 -> "$totalSizeBytes B"
                totalSizeBytes < 1024 * 1024 -> "${totalSizeBytes / 1024} KB"
                else -> String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024.0))
            }
        }
    }
    
    // ==================== Download Operations ====================
    
    /**
     * Download audio files from manifest
     * 
     * @param audioFiles List of audio files to download
     * @param baseUrl Base URL for downloads
     * @param onProgress Progress callback (downloaded, total)
     * @return Number of files downloaded
     */
    suspend fun downloadAudioFiles(
        audioFiles: List<AudioFileInfo>,
        baseUrl: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var downloaded = 0
        val toDownload = audioFiles.filter { !hasAudio(it.filename) }
        
        Log.d(TAG, "Downloading ${toDownload.size} audio files (${audioFiles.size - toDownload.size} already cached)")
        
        for ((index, audioFile) in toDownload.withIndex()) {
            try {
                if (downloadSingleFile(audioFile, baseUrl)) {
                    downloaded++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ${audioFile.filename}", e)
            }
            
            onProgress?.invoke(index + 1, toDownload.size)
        }
        
        Log.d(TAG, "Downloaded $downloaded audio files")
        
        // Rescan to update the in-memory cache
        if (downloaded > 0) {
            scanExistingFiles()
        }
        
        downloaded
    }
    
    /**
     * Download a single audio file
     */
    private suspend fun downloadSingleFile(
        audioFile: AudioFileInfo,
        baseUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val filename = audioFile.filename
        
        // Skip if already downloaded or currently downloading
        if (hasAudio(filename) || pendingDownloads.contains(filename)) {
            return@withContext false
        }
        
        pendingDownloads.add(filename)
        
        try {
            // Build full URL
            val url = if (audioFile.url.startsWith("http")) {
                audioFile.url
            } else {
                baseUrl.trimEnd('/') + audioFile.url
            }
            
            // Download using OkHttp
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = ApiClient.getOkHttpClient().newCall(request).execute()
            
            // Use response.use to ensure proper cleanup
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Failed to download $filename: ${resp.code}")
                    return@withContext false
                }
                
                val body = resp.body ?: return@withContext false
                
                // Determine target directory
                val dir = if (audioFile.language == "ar") arDir else enDir
                val file = File(dir, filename)
                
                // Write to file
                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                downloadedFiles.add(filename)
                Log.d(TAG, "Downloaded: $filename (${file.length()} bytes)")
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $filename", e)
            false
        } finally {
            pendingDownloads.remove(filename)
        }
    }
    
    // ==================== Cleanup Operations ====================
    
    /**
     * Clear all cached audio files
     */
    fun clearCache() {
        listOf(arDir, enDir).forEach { dir ->
            dir.listFiles()?.forEach { it.delete() }
        }
        downloadedFiles.clear()
        persistedAudioManifest = null
        persistedEffectiveBaseUrl = null
        try {
            if (manifestFile.exists()) manifestFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete manifest file", e)
        }
        Log.d(TAG, "Cleared audio cache")
    }
    
    /**
     * Remove audio files not in the provided list
     * Used to cleanup orphaned files after sync
     * 
     * @param validFilenames List of valid filenames to keep
     * @return Number of files removed
     */
    fun cleanupOrphanedFiles(validFilenames: Set<String>): Int {
        var removed = 0
        
        listOf(arDir, enDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.extension == "wav" && file.name !in validFilenames) {
                    file.delete()
                    downloadedFiles.remove(file.name)
                    removed++
                }
            }
        }
        
        if (removed > 0) {
            Log.d(TAG, "Removed $removed orphaned audio files")
        }
        
        return removed
    }
    
    /**
     * Enforce cache size limit by removing oldest files
     */
    fun enforceCacheLimit() {
        val maxBytes = MAX_CACHE_SIZE_MB * 1024L * 1024L
        val stats = getCacheStats()
        
        if (stats.totalSizeBytes <= maxBytes) return
        
        // Get all files sorted by modification time (oldest first)
        val allFiles = mutableListOf<File>()
        listOf(arDir, enDir).forEach { dir ->
            dir.listFiles()?.filter { it.extension == "wav" }?.let { allFiles.addAll(it) }
        }
        allFiles.sortBy { it.lastModified() }
        
        var currentSize = stats.totalSizeBytes
        var removed = 0
        
        for (file in allFiles) {
            if (currentSize <= maxBytes) break
            
            currentSize -= file.length()
            downloadedFiles.remove(file.name)
            file.delete()
            removed++
        }
        
        Log.d(TAG, "Removed $removed files to enforce cache limit")
    }
}
