package com.movit.core.training.boundary

import com.movit.core.training.feedback.FeedbackSpeechPriority

/** WS-9: AVSpeechSynthesizer actual; stub keeps iosSimulatorArm64 green in 07.4. */
actual class SpeechSynthesizer {
    actual fun speak(text: String, priority: FeedbackSpeechPriority) = Unit

    actual fun stop() = Unit

    actual fun release() = Unit
}
