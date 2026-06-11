package com.movit.core.data.audio

import com.movit.core.data.cache.AudioManifestCache

/**
 * After sync persists audio manifest metadata, downloads missing files and enforces cache policy.
 */
class AudioPrefetchRunner(
    private val manifestCache: AudioManifestCache,
    private val downloader: AudioFileDownloadPort,
) {
    suspend fun afterManifestApplied(isFullSync: Boolean) {
        val state = manifestCache.read() ?: return
        if (state.manifest.files.isEmpty()) return

        if (isFullSync) {
            val valid = state.manifest.files.map { it.filename }.toSet()
            downloader.cleanupOrphanedFiles(valid)
        }

        val pending = state.manifest.files.filter { file ->
            file.filename.isNotBlank() && !downloader.hasAudio(file.filename)
        }
        if (pending.isEmpty()) return

        downloader.downloadFiles(pending, state.baseUrl)
        downloader.enforceCacheLimit()
    }
}
