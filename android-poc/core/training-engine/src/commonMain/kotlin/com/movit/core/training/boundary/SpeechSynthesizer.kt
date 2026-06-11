package com.movit.core.training.boundary

import com.movit.core.training.feedback.FeedbackSpeechPriority

/**
 * Platform TTS for live coaching (Android: TextToSpeech; iOS: AVSpeechSynthesizer in WS-9).
 */
expect class SpeechSynthesizer {
    fun speak(text: String, priority: FeedbackSpeechPriority = FeedbackSpeechPriority.NORMAL)

    fun stop()

    fun release()
}
