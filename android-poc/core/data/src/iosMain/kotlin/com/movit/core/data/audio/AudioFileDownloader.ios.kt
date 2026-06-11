package com.movit.core.data.audio

import com.movit.core.network.dto.AudioFileInfoDto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual class AudioFileDownloader actual constructor() : AudioFileDownloadPort {
    private val fileManager = NSFileManager.defaultManager
    private val cacheRoot: String = resolveCacheRoot()
    private val arDir = "$cacheRoot/ar"
    private val enDir = "$cacheRoot/en"

    init {
        ensureDirectoriesExist()
    }

    actual override fun hasAudio(filename: String): Boolean {
        val path = localPath(filename) ?: return false
        val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: return false
        val size = (attrs["NSFileSize"] as? Number)?.toLong() ?: 0L
        return size > 0L
    }

    actual override fun localPath(filename: String): String? {
        if (filename.isBlank()) return null
        val language = resolveLanguageSubdir(filename, language = "")
        val dir = if (language == "ar") arDir else enDir
        val path = "$dir/$filename"
        return if (fileManager.fileExistsAtPath(path)) path else null
    }

    actual override suspend fun downloadFiles(
        files: List<AudioFileInfoDto>,
        baseUrl: String,
    ): Int = withContext(Dispatchers.Default) {
        var downloaded = 0
        val toDownload = files.filter { it.filename.isNotBlank() && !hasAudio(it.filename) }
        for (audioFile in toDownload) {
            if (downloadSingleFileWithRetries(audioFile, baseUrl)) {
                downloaded++
            }
        }
        enforceCacheLimit()
        downloaded
    }

    actual override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int {
        var removed = 0
        languageDirs().forEach { dir ->
            val contents = fileManager.contentsOfDirectoryAtPath(dir, error = null) ?: return@forEach
            contents.filterIsInstance<String>().forEach { name ->
                if (isCachedAudioFilename(name) && name !in validFilenames) {
                    val path = "$dir/$name"
                    if (fileManager.removeItemAtPath(path, error = null)) {
                        removed++
                    }
                }
            }
        }
        return removed
    }

    actual override fun enforceCacheLimit(maxBytes: Long) {
        val files = listCachedFiles()
        var currentSize = files.sumOf { it.second }
        if (currentSize <= maxBytes) return

        val sorted = files.sortedBy { it.third }
        for ((path, size, _) in sorted) {
            if (currentSize <= maxBytes) break
            if (fileManager.removeItemAtPath(path, error = null)) {
                currentSize -= size
            }
        }
    }

    private fun ensureDirectoriesExist() {
        listOf(cacheRoot, arDir, enDir).forEach { path ->
            if (!fileManager.fileExistsAtPath(path)) {
                fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
            }
        }
    }

    private fun languageDirs(): List<String> = listOf(arDir, enDir)

    private fun listCachedFiles(): List<Triple<String, Long, Double>> {
        val out = mutableListOf<Triple<String, Long, Double>>()
        languageDirs().forEach { dir ->
            val contents = fileManager.contentsOfDirectoryAtPath(dir, error = null) ?: return@forEach
            contents.filterIsInstance<String>().forEach { name ->
                if (!isCachedAudioFilename(name)) return@forEach
                val path = "$dir/$name"
                val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: return@forEach
                val size = (attrs["NSFileSize"] as? Number)?.toLong() ?: 0L
                val modified = (attrs["NSFileModificationDate"] as? NSDate)?.timeIntervalSince1970 ?: 0.0
                out.add(Triple(path, size, modified))
            }
        }
        return out
    }

    private suspend fun downloadSingleFileWithRetries(
        audioFile: AudioFileInfoDto,
        baseUrl: String,
        maxAttempts: Int = 4,
    ): Boolean {
        var delayMs = 500L
        repeat(maxAttempts) { attempt ->
            if (downloadOneFile(audioFile, baseUrl)) return true
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 8_000L)
            }
        }
        return false
    }

    private suspend fun downloadOneFile(audioFile: AudioFileInfoDto, baseUrl: String): Boolean {
        val filename = audioFile.filename
        if (hasAudio(filename)) return false

        val url = resolveAudioDownloadUrl(baseUrl, audioFile)
        val data = fetchUrlData(url) ?: return false
        if (data.length.toLong() <= 0L) return false

        val language = resolveLanguageSubdir(filename, audioFile.language)
        val dir = if (language == "ar") arDir else enDir
        val finalPath = "$dir/$filename"
        val tmpPath = "$dir/$filename.part"

        fileManager.removeItemAtPath(tmpPath, error = null)
        if (!data.writeToFile(tmpPath, atomically = true)) {
            fileManager.removeItemAtPath(tmpPath, error = null)
            return false
        }

        fileManager.removeItemAtPath(finalPath, error = null)
        return fileManager.moveItemAtPath(tmpPath, toPath = finalPath, error = null)
    }

    private suspend fun fetchUrlData(url: String): NSData? = suspendCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resume(null)
            return@suspendCoroutine
        }
        val task = NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, response, error ->
            if (error != null || data == null) {
                cont.resume(null)
                return@dataTaskWithURL
            }
            val http = response as? NSHTTPURLResponse
            if (http != null && http.statusCode !in 200L..299L) {
                cont.resume(null)
                return@dataTaskWithURL
            }
            cont.resume(data)
        }
        task.resume()
    }

    private companion object {
        fun resolveCacheRoot(): String {
            val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            val root = paths.firstOrNull() as? String ?: return "audio_cache"
            return "$root/audio_cache"
        }
    }
}
