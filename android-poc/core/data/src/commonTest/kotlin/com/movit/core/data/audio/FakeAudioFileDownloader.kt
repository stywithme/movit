package com.movit.core.data.audio

import com.movit.core.network.dto.AudioFileInfoDto

class FakeAudioFileDownloader : AudioFileDownloadPort {
    val downloadedBatches = mutableListOf<Pair<String, List<AudioFileInfoDto>>>()
    var orphanCleanupCalls = 0
    var enforceLimitCalls = 0

    override fun hasAudio(filename: String): Boolean = false

    override fun localPath(filename: String): String? = null

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
