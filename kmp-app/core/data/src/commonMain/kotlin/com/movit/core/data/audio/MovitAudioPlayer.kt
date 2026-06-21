package com.movit.core.data.audio

/**
 * Platform audio playback boundary (WS-3). Manifest caching stays in commonMain;
 * only playback is platform-specific.
 */
expect class MovitAudioPlayer() {
    fun playFile(localPath: String): Boolean

    fun stop()

    fun isPlaying(): Boolean
}
