package com.movit.core.data.image

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
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

/**
 * Persists catalog images under Application Support — not Caches, which iOS purges under
 * storage pressure, taking the offline set with it.
 */
@OptIn(ExperimentalForeignApi::class)
actual class ImageFileDownloader actual constructor() : ImageFileDownloadPort {
    private val fileManager = NSFileManager.defaultManager
    private val cacheRoot: String = resolveCacheRoot()

    init {
        if (!fileManager.fileExistsAtPath(cacheRoot)) {
            fileManager.createDirectoryAtPath(
                cacheRoot,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    actual override fun hasImage(filename: String): Boolean {
        val path = pathFor(filename) ?: return false
        val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: return false
        return ((attrs["NSFileSize"] as? Number)?.toLong() ?: 0L) > 0L
    }

    actual override fun localPath(filename: String): String? =
        pathFor(filename)?.takeIf { hasImage(filename) }

    actual override suspend fun downloadImages(
        assets: List<ImageAsset>,
        baseUrl: String,
    ): Int = withContext(Dispatchers.Default) {
        var downloaded = 0
        for (asset in assets) {
            if (asset.filename.isBlank() || hasImage(asset.filename)) continue
            if (downloadWithRetries(asset, baseUrl)) downloaded++
        }
        enforceCacheLimit()
        downloaded
    }

    actual override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int {
        var removed = 0
        val contents = fileManager.contentsOfDirectoryAtPath(cacheRoot, error = null) ?: return 0
        contents.filterIsInstance<String>().forEach { name ->
            if (isCachedImageFilename(name) && name !in validFilenames) {
                if (fileManager.removeItemAtPath("$cacheRoot/$name", error = null)) removed++
            }
        }
        return removed
    }

    actual override fun enforceCacheLimit(maxBytes: Long) {
        val files = listCachedFiles()
        var currentSize = files.sumOf { it.second }
        if (currentSize <= maxBytes) return

        for ((path, size, _) in files.sortedBy { it.third }) {
            if (currentSize <= maxBytes) break
            if (fileManager.removeItemAtPath(path, error = null)) currentSize -= size
        }
    }

    actual override fun totalSizeBytes(): Long = listCachedFiles().sumOf { it.second }

    private fun pathFor(filename: String): String? {
        if (filename.isBlank()) return null
        val path = "$cacheRoot/$filename"
        return if (fileManager.fileExistsAtPath(path)) path else null
    }

    private fun listCachedFiles(): List<Triple<String, Long, Double>> {
        val contents = fileManager.contentsOfDirectoryAtPath(cacheRoot, error = null) ?: return emptyList()
        val out = mutableListOf<Triple<String, Long, Double>>()
        contents.filterIsInstance<String>().forEach { name ->
            if (!isCachedImageFilename(name)) return@forEach
            val path = "$cacheRoot/$name"
            val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: return@forEach
            val size = (attrs["NSFileSize"] as? Number)?.toLong() ?: 0L
            val modified = (attrs["NSFileModificationDate"] as? NSDate)?.timeIntervalSince1970 ?: 0.0
            out.add(Triple(path, size, modified))
        }
        return out
    }

    private suspend fun downloadWithRetries(
        asset: ImageAsset,
        baseUrl: String,
        maxAttempts: Int = 3,
    ): Boolean {
        var delayMs = 500L
        repeat(maxAttempts) { attempt ->
            if (downloadOne(asset, baseUrl)) return true
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 8_000L)
            }
        }
        return false
    }

    private suspend fun downloadOne(asset: ImageAsset, baseUrl: String): Boolean {
        if (hasImage(asset.filename)) return false
        val data = fetchUrlData(resolveImageDownloadUrl(baseUrl, asset.url)) ?: return false
        if (data.length.toLong() <= 0L) return false

        val finalPath = "$cacheRoot/${asset.filename}"
        val tmpPath = "$finalPath.part"

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
            val paths = NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory,
                NSUserDomainMask,
                true,
            )
            val root = paths.firstOrNull() as? String ?: return "image_cache"
            return "$root/image_cache"
        }
    }
}
