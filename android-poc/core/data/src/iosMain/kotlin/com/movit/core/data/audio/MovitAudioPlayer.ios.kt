package com.movit.core.data.audio

/**
 * iOS playback wiring lands in Phase 07; manifest cache is already shared.
 */
actual class MovitAudioPlayer actual constructor() {
    actual fun playFile(localPath: String): Boolean = false

    actual fun stop() = Unit

    actual fun isPlaying(): Boolean = false
}
