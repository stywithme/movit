package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.network.AudioFileInfo
import com.trainingvalidator.poc.network.AudioManifest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

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
    
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    /**
     * Dedicated OkHttpClient for downloading audio from external URLs (GCS).
     * Does NOT include API auth headers or Content-Type overrides that the main
     * ApiClient attaches — those headers can cause 401/403 from GCS.
     */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    // Track download status (thread-safe)
    private val downloadedFiles: MutableSet<String> =
        Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    private val pendingDownloads: MutableSet<String> =
        Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    
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
            if (wrapper.baseUrl.isBlank() || wrapper.files.isEmpty()) return
            persistedEffectiveBaseUrl = wrapper.baseUrl
            persistedAudioManifest = AudioManifest(wrapper.baseUrl, wrapper.files)
            Log.d(TAG, "Loaded persisted audio manifest: ${wrapper.files.size} entries (scope=${wrapper.scope ?: "legacy"})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load persisted audio manifest", e)
        }
    }
    
    /**
     * Replace persisted manifest and in-memory list (full sync). Caller may run [cleanupOrphanedFiles] after.
     */
    fun replaceFullPersistedManifest(effectiveBaseUrl: String, manifest: AudioManifest) {
        persistedEffectiveBaseUrl = effectiveBaseUrl.trimEnd('/')
        persistedAudioManifest = AudioManifest(effectiveBaseUrl.trimEnd('/'), manifest.files)
        writeManifestDisk(scope = "full")
        Log.d(TAG, "Full audio manifest persisted (${manifest.files.size} files)")
    }

    /**
     * Merge new manifest entries into persisted state (incremental sync / entity prefetch).
     */
    fun mergePartialPersistedManifest(effectiveBaseUrl: String, manifest: AudioManifest) {
        val base = effectiveBaseUrl.trimEnd('/')
        val existing = persistedAudioManifest?.files ?: emptyList()
        val merged = LinkedHashMap<String, AudioFileInfo>()
        for (f in existing) merged[f.filename] = f
        for (f in manifest.files) merged[f.filename] = f
        val list = merged.values.toList()
        persistedEffectiveBaseUrl = base
        persistedAudioManifest = AudioManifest(base, list)
        writeManifestDisk(scope = "partial")
        Log.d(TAG, "Merged audio manifest (now ${list.size} files)")
    }

    /**
     * @deprecated Use [replaceFullPersistedManifest] or [mergePartialPersistedManifest].
     */
    fun persistAudioManifest(effectiveBaseUrl: String, manifest: AudioManifest) {
        replaceFullPersistedManifest(effectiveBaseUrl, manifest)
    }

    private fun writeManifestDisk(scope: String) {
        val man = persistedAudioManifest ?: return
        val base = persistedEffectiveBaseUrl ?: return
        try {
            val json = gson.toJson(
                PersistedAudioManifestJson(
                    baseUrl = base,
                    files = man.files,
                    scope = scope
                )
            )
            manifestFile.writeText(json)
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
        val files: List<AudioFileInfo>,
        /** "full" after full sync manifest; "partial" for merges / entity prefetch */
        val scope: String? = null
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
        val f = getAudioPath(filename)
        return f != null && f.length() > 0L
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
                if (downloadSingleFileWithRetries(audioFile, baseUrl)) {
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
        enforceCacheLimit()

        downloaded
    }
    
    /**
     * Exponential backoff retries for flaky networks (runs on [Dispatchers.IO] caller).
     */
    private suspend fun downloadSingleFileWithRetries(
        audioFile: AudioFileInfo,
        baseUrl: String,
        maxAttempts: Int = 4
    ): Boolean {
        var delayMs = 500L
        repeat(maxAttempts) { attempt ->
            if (downloadOneFileSync(audioFile, baseUrl)) return true
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 8000L)
            }
        }
        return false
    }

    /**
     * Single HTTP GET and write; not suspend — caller provides IO dispatcher.
     */
    private fun downloadOneFileSync(audioFile: AudioFileInfo, baseUrl: String): Boolean {
        val filename = audioFile.filename

        if (hasAudio(filename)) return false
        if (!pendingDownloads.add(filename)) return false

        try {
            val url = if (audioFile.url.startsWith("http", ignoreCase = true)) {
                audioFile.url
            } else if (audioFile.url.startsWith("/")) {
                baseUrl.trimEnd('/') + audioFile.url
            } else {
                baseUrl.trimEnd('/') + "/" + audioFile.url
            }

            val request = Request.Builder()
                .url(url)
                .build()

            val response = downloadClient.newCall(request).execute()

            return response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "DOWNLOAD_FAIL: $filename code=${resp.code} url=$url")
                    return@use false
                }

                val body = resp.body ?: run {
                    Log.e(TAG, "DOWNLOAD_FAIL: $filename empty body url=$url")
                    return@use false
                }

                val dir = if (audioFile.language == "ar") arDir else enDir
                val finalFile = File(dir, filename)
                val tmp = File(dir, "${filename}.part")

                FileOutputStream(tmp).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }

                val expected = audioFile.size
                if (tmp.length() <= 0L) {
                    tmp.delete()
                    Log.e(TAG, "DOWNLOAD_FAIL: $filename zero bytes url=$url")
                    return@use false
                }
                if (expected != null && expected > 0 && tmp.length() != expected) {
                    Log.w(TAG, "DOWNLOAD_WARN: $filename size ${tmp.length()} expected $expected url=$url")
                }

                if (finalFile.exists()) finalFile.delete()
                if (!tmp.renameTo(finalFile)) {
                    tmp.copyTo(finalFile, overwrite = true)
                    tmp.delete()
                }

                downloadedFiles.add(filename)
                Log.d(TAG, "DOWNLOAD_OK: $filename (${finalFile.length()} bytes)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "DOWNLOAD_ERROR: $filename ${e.javaClass.simpleName}: ${e.message}")
            listOf(arDir, enDir).forEach { File(it, "${filename}.part").delete() }
            return false
        } finally {
            pendingDownloads.remove(filename)
        }
    }
    
    /**
     * Log a diagnostic summary of the audio cache state.
     * Call before training to help debug TTS-fallback issues.
     */
    fun logDiagnosticSummary() {
        val stats = getCacheStats()
        val pending = getPendingDownloadCount()
        Log.i(TAG, "──── AUDIO CACHE DIAGNOSTIC ────")
        Log.i(TAG, "Cached files: ${stats.totalFiles} (ar=${stats.arabicFiles}, en=${stats.englishFiles}, size=${stats.getFormattedSize()})")
        Log.i(TAG, "Pending downloads: $pending")
        Log.i(TAG, "Manifest persisted: ${persistedAudioManifest != null} (${persistedAudioManifest?.files?.size ?: 0} entries)")
        Log.i(TAG, "In-memory downloadedFiles set: ${downloadedFiles.size}")
        if (stats.totalFiles == 0 && pending > 0) {
            Log.w(TAG, "⚠ No audio cached yet but $pending files in manifest — downloads may have failed!")
        }
        Log.i(TAG, "────────────────────────────────")
    }
    
    // ==================== Cleanup Operations ====================
    
    /**
     * Clear all cached audio files
     */
    fun clearCache() {
        listOf(arDir, enDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.extension == "wav" || file.name.endsWith(".part")) {
                    file.delete()
                }
            }
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
