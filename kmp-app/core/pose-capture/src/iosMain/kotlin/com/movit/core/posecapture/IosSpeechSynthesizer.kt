package com.movit.core.posecapture

import com.movit.core.training.boundary.SpeechSynthesizer
import com.movit.core.training.feedback.FeedbackSpeechPriority

/**
 * iOS TTS facade for pose-capture bindings — delegates to [SpeechSynthesizer] actual (WS-9).
 */
class IosSpeechSynthesizer(
    private val delegate: SpeechSynthesizer = SpeechSynthesizer(),
) {
    fun speak(
        text: String,
        priority: FeedbackSpeechPriority = FeedbackSpeechPriority.NORMAL,
        languageTag: String = "en-US",
    ) {
        if (languageTag != "en-US") {
            // Language selection is owned by the platform actual; ignore tag until exposed on boundary.
        }
        delegate.speak(text, priority)
    }

    fun stop() = delegate.stop()

    fun release() = delegate.release()
}
