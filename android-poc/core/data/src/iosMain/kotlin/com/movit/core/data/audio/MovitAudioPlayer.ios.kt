package com.movit.core.data.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual class MovitAudioPlayer actual constructor() {
    private var player: AVAudioPlayer? = null

    actual fun playFile(localPath: String): Boolean {
        stop()
        ensureMovitTrainingAudioSession()
        if (!NSFileManager.defaultManager.fileExistsAtPath(localPath)) return false
        val url = NSURL.fileURLWithPath(localPath)
        return runCatching {
            val next = AVAudioPlayer(contentsOfURL = url, error = null) ?: return@runCatching false
            if (!next.prepareToPlay()) return@runCatching false
            player = next
            next.play()
            true
        }.getOrDefault(false)
    }

    actual fun stop() {
        player?.run {
            if (isPlaying()) stop()
        }
        player = null
    }

    actual fun isPlaying(): Boolean = player?.isPlaying() == true
}
