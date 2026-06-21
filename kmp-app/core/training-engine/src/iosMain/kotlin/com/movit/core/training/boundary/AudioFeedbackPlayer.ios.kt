package com.movit.core.training.boundary

import com.movit.core.training.feedback.FeedbackSignal

actual interface AudioFeedbackPlayer {
    actual fun prepare()

    actual fun play(signal: FeedbackSignal)

    actual fun stopAll()

    actual fun release()
}
