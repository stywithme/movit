package com.movit.core.training.boundary

import com.movit.core.training.feedback.FeedbackSpeechPriority

/**
 * Platform TTS for live coaching (Android: TextToSpeech; iOS: AVSpeechSynthesizer in WS-9).
 */
expect class SpeechSynthesizer {
    /**
     * Selects the best platform voice for the app language ("ar"/"en") before speaking.
     * Call when the session language is known; the device locale is **not** assumed to match.
     */
    fun setLanguage(language: String)

    fun speak(text: String, priority: FeedbackSpeechPriority = FeedbackSpeechPriority.NORMAL)

    fun stop()

    fun release()
}
