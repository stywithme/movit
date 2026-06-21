package com.movit.core.data.audio

/**
 * Maps manifest/download URLs to on-disk cached clip paths (DS-6).
 */
object AudioClipResolver {
    fun filenameFromUrl(audioUrl: String?): String? =
        audioUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && it.contains('.') }

    fun resolveLocalPath(audioCache: AudioFileDownloadPort, audioUrl: String?): String? {
        val filename = filenameFromUrl(audioUrl) ?: return null
        return audioCache.localPath(filename)
    }
}
