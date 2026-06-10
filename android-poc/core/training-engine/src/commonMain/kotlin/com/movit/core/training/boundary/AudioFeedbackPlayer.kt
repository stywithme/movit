package com.movit.core.training.boundary

import com.movit.core.training.feedback.FeedbackSignal

/**
 * Platform audio output. Phase 07 actuals: Android MediaPlayer/TTS, iOS AVSpeechSynthesizer.
 */
expect interface AudioFeedbackPlayer {
    fun prepare()

    fun play(signal: FeedbackSignal)

    fun stopAll()

    fun release()
}
