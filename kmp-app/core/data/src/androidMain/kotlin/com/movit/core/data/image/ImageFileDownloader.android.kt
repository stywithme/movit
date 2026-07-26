package com.movit.core.data.image

import com.movit.core.data.local.MovitAndroidRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Persists catalog images under `filesDir/image_cache` — not `cacheDir`, so Android's
 * low-storage cleaner cannot wipe the offline set before a gym session.
 */
actual class ImageFileDownloader actual constructor() : ImageFileDownloadPort {
    private val cacheDir = File(MovitAndroidRuntime.applicationContext.filesDir, CACHE_DIR)

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    actual override fun hasImage(filename: String): Boolean {
        val file = resolveFile(filename) ?: return false
        return file.exists() && file.length() > 0L
    }

    actual override fun localPath(filename: String): String? =
        resolveFile(filename)?.takeIf { it.exists() && it.length() > 0L }?.absolutePath

    actual override suspend fun downloadImages(
        assets: List<ImageAsset>,
        baseUrl: String,
    ): Int = withContext(Dispatchers.IO) {
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
        cacheDir.listFiles()?.forEach { file ->
            if (isCachedImageFilename(file.name) && file.name !in validFilenames) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    actual override fun enforceCacheLimit(maxBytes: Long) {
        var currentSize = totalSizeBytes()
        if (currentSize <= maxBytes) return

        val files = cacheDir.listFiles()
            ?.filter { isCachedImageFilename(it.name) }
            ?.sortedBy { it.lastModified() }
            ?: return
        for (file in files) {
            if (currentSize <= maxBytes) break
            val size = file.length()
            if (file.delete()) currentSize -= size
        }
    }

    actual override fun totalSizeBytes(): Long =
        cacheDir.listFiles()
            ?.filter { isCachedImageFilename(it.name) }
            ?.sumOf { it.length() }
            ?: 0L

    private fun resolveFile(filename: String): File? {
        if (filename.isBlank()) return null
        return File(cacheDir, filename)
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

    private fun downloadOne(asset: ImageAsset, baseUrl: String): Boolean {
        val target = resolveFile(asset.filename) ?: return false
        val partFile = File(cacheDir, "${asset.filename}.part")
        val url = resolveImageDownloadUrl(baseUrl, asset.url)

        try {
            val request = Request.Builder().url(url).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                partFile.delete()
                FileOutputStream(partFile).use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
        } catch (e: IOException) {
            partFile.delete()
            return false
        }

        if (partFile.length() <= 0L) {
            partFile.delete()
            return false
        }
        if (target.exists()) target.delete()
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        return target.exists() && target.length() > 0L
    }

    private companion object {
        const val CACHE_DIR = "image_cache"
    }
}
