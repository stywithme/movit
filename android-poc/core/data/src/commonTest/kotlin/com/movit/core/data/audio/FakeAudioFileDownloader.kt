package com.movit.core.data.audio

import com.movit.core.network.dto.AudioFileInfoDto

class FakeAudioFileDownloader : AudioFileDownloadPort {
    val downloadedBatches = mutableListOf<Pair<String, List<AudioFileInfoDto>>>()
    var orphanCleanupCalls = 0
    var enforceLimitCalls = 0
    private val paths = mutableMapOf<String, String>()

    fun seedFile(filename: String, absolutePath: String) {
        paths[filename] = absolutePath
    }

    override fun hasAudio(filename: String): Boolean = paths.containsKey(filename)

    override fun localPath(filename: String): String? = paths[filename]

    override suspend fun downloadFiles(files: List<AudioFileInfoDto>, baseUrl: String): Int {
        downloadedBatches += baseUrl to files
        return files.size
    }

    override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int {
        orphanCleanupCalls++
        return 0
    }

    override fun enforceCacheLimit(maxBytes: Long) {
        enforceLimitCalls++
    }
}
