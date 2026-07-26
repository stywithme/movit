package com.movit.core.data.image

/**
 * Durable on-disk store for catalog images, mirroring [com.movit.core.data.audio.AudioFileDownloadPort].
 *
 * Deliberately **not** the Coil disk cache: Coil evicts under LRU pressure and (on Android) lives in
 * `cacheDir`, which the OS may clear at any time. Gym-offline needs images that survive both.
 */
interface ImageFileDownloadPort {
    fun hasImage(filename: String): Boolean

    fun localPath(filename: String): String?

    /** @return number of files newly written. */
    suspend fun downloadImages(assets: List<ImageAsset>, baseUrl: String): Int

    fun cleanupOrphanedFiles(validFilenames: Set<String>): Int

    fun enforceCacheLimit(maxBytes: Long = MAX_CACHE_BYTES)

    fun totalSizeBytes(): Long

    companion object {
        /**
         * 256 MiB: whole-catalog thumbnails plus pose frames, alongside the 100 MiB audio budget.
         * Eviction is oldest-first and only kicks in above the ceiling.
         */
        const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    }
}

expect class ImageFileDownloader() : ImageFileDownloadPort {
    override fun hasImage(filename: String): Boolean

    override fun localPath(filename: String): String?

    override suspend fun downloadImages(assets: List<ImageAsset>, baseUrl: String): Int

    override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int

    override fun enforceCacheLimit(maxBytes: Long)

    override fun totalSizeBytes(): Long
}
