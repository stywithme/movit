package com.movit.core.data.audio

import com.movit.core.network.dto.AudioFileInfoDto

/**
 * Platform-neutral audio file cache operations (WS-7 / DS-6).
 */
interface AudioFileDownloadPort {
    fun hasAudio(filename: String): Boolean

    fun localPath(filename: String): String?

    suspend fun downloadFiles(files: List<AudioFileInfoDto>, baseUrl: String): Int

    fun cleanupOrphanedFiles(validFilenames: Set<String>): Int

    fun enforceCacheLimit(maxBytes: Long = MAX_CACHE_BYTES)

    companion object {
        const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
    }
}

expect class AudioFileDownloader() : AudioFileDownloadPort {
    override fun hasAudio(filename: String): Boolean

    override fun localPath(filename: String): String?

    override suspend fun downloadFiles(files: List<AudioFileInfoDto>, baseUrl: String): Int

    override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int

    override fun enforceCacheLimit(maxBytes: Long)
}
