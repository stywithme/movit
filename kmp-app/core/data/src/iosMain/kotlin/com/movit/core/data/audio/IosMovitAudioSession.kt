package com.movit.core.data.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive

/**
 * Shared AVAudioSession for training feedback (TTS + cue audio) while the camera session runs.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun ensureMovitTrainingAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(
        category = AVAudioSessionCategoryPlayAndRecord,
        withOptions = AVAudioSessionCategoryOptionMixWithOthers,
        error = null,
    )
    session.setActive(active = true, error = null)
}
