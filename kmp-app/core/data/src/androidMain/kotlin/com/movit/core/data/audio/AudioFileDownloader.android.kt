package com.movit.core.data.audio

import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.network.dto.AudioFileInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

actual class AudioFileDownloader actual constructor() : AudioFileDownloadPort {
    private val cacheDir = File(MovitAndroidRuntime.applicationContext.filesDir, CACHE_DIR)
    private val arDir = File(cacheDir, "ar")
    private val enDir = File(cacheDir, "en")

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val downloadedFiles: MutableSet<String> =
        Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    private val pendingDownloads: MutableSet<String> =
        Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    init {
        ensureDirectoriesExist()
        scanExistingFiles()
    }

    actual override fun hasAudio(filename: String): Boolean {
        val file = resolveFile(filename) ?: return false
        return file.exists() && file.length() > 0L
    }

    actual override fun localPath(filename: String): String? =
        resolveFile(filename)?.takeIf { it.exists() }?.absolutePath

    actual override suspend fun downloadFiles(
        files: List<AudioFileInfoDto>,
        baseUrl: String,
    ): Int = withContext(Dispatchers.IO) {
        var downloaded = 0
        val toDownload = files.filter { it.filename.isNotBlank() && !hasAudio(it.filename) }
        for (audioFile in toDownload) {
            if (downloadSingleFileWithRetries(audioFile, baseUrl)) {
                downloaded++
            }
        }
        if (downloaded > 0) {
            scanExistingFiles()
        }
        enforceCacheLimit()
        downloaded
    }

    actual override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int {
        var removed = 0
        languageDirs().forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (isCachedAudioFilename(file.name) && file.name !in validFilenames) {
                    file.delete()
                    downloadedFiles.remove(file.name)
                    removed++
                }
            }
        }
        return removed
    }

    actual override fun enforceCacheLimit(maxBytes: Long) {
        val stats = cacheStats()
        if (stats.totalSizeBytes <= maxBytes) return

        val allFiles = mutableListOf<File>()
        languageDirs().forEach { dir ->
            dir.listFiles()
                ?.filter { isCachedAudioFilename(it.name) }
                ?.let { allFiles.addAll(it) }
        }
        allFiles.sortBy { it.lastModified() }

        var currentSize = stats.totalSizeBytes
        for (file in allFiles) {
            if (currentSize <= maxBytes) break
            currentSize -= file.length()
            downloadedFiles.remove(file.name)
            file.delete()
        }
    }

    private fun ensureDirectoriesExist() {
        listOf(cacheDir, arDir, enDir).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    private fun scanExistingFiles() {
        downloadedFiles.clear()
        languageDirs().forEach { dir ->
            dir.listFiles()
                ?.filter { isCachedAudioFilename(it.name) }
                ?.forEach { downloadedFiles.add(it.name) }
        }
    }

    private fun languageDirs(): List<File> = listOf(arDir, enDir)

    private fun resolveFile(filename: String): File? {
        if (filename.isBlank()) return null
        val language = resolveLanguageSubdir(filename, language = "")
        val dir = if (language == "ar") arDir else enDir
        return File(dir, filename)
    }

    private suspend fun downloadSingleFileWithRetries(
        audioFile: AudioFileInfoDto,
        baseUrl: String,
        maxAttempts: Int = 4,
    ): Boolean {
        var delayMs = 500L
        repeat(maxAttempts) { attempt ->
            if (downloadOneFileSync(audioFile, baseUrl)) return true
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 8_000L)
            }
        }
        return false
    }

    private fun downloadOneFileSync(audioFile: AudioFileInfoDto, baseUrl: String): Boolean {
        val filename = audioFile.filename
        if (hasAudio(filename)) return false
        if (!pendingDownloads.add(filename)) return false

        try {
            val url = resolveAudioDownloadUrl(baseUrl, audioFile)
            val request = Request.Builder().url(url).build()
            val response = downloadClient.newCall(request).execute()

            return response.use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false

                val language = resolveLanguageSubdir(filename, audioFile.language)
                val dir = if (language == "ar") arDir else enDir
                val finalFile = File(dir, filename)
                val tmp = File(dir, "$filename.part")

                FileOutputStream(tmp).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (tmp.length() <= 0L) {
                    tmp.delete()
                    return@use false
                }

                if (finalFile.exists()) finalFile.delete()
                if (!tmp.renameTo(finalFile)) {
                    tmp.copyTo(finalFile, overwrite = true)
                    tmp.delete()
                }

                downloadedFiles.add(filename)
                true
            }
        } catch (_: Exception) {
            languageDirs().forEach { File(it, "$filename.part").delete() }
            return false
        } finally {
            pendingDownloads.remove(filename)
        }
    }

    private data class CacheStats(val totalSizeBytes: Long)

    private fun cacheStats(): CacheStats {
        var totalSize = 0L
        languageDirs().forEach { dir ->
            dir.listFiles()
                ?.filter { isCachedAudioFilename(it.name) }
                ?.forEach { totalSize += it.length() }
        }
        return CacheStats(totalSize)
    }

    private companion object {
        const val CACHE_DIR = "audio_cache"
    }
}
