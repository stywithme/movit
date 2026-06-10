package com.movit.core.data.audio

import android.media.MediaPlayer
import java.io.File

actual class MovitAudioPlayer actual constructor() {
    private var player: MediaPlayer? = null

    actual fun playFile(localPath: String): Boolean {
        stop()
        val file = File(localPath)
        if (!file.exists()) return false
        return runCatching {
            player = MediaPlayer().apply {
                setDataSource(localPath)
                prepare()
                start()
            }
            true
        }.getOrDefault(false)
    }

    actual fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    actual fun isPlaying(): Boolean = player?.isPlaying == true
}
