package com.movit.core.training.boundary

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive

@OptIn(ExperimentalForeignApi::class)
internal fun ensureTrainingAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(
        category = AVAudioSessionCategoryPlayAndRecord,
        withOptions = AVAudioSessionCategoryOptionMixWithOthers,
        error = null,
    )
    session.setActive(active = true, error = null)
}
