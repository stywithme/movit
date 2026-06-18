package com.movit.core.training.boundary

import com.movit.core.training.feedback.FeedbackSpeechPriority
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

@OptIn(ExperimentalForeignApi::class)
actual class SpeechSynthesizer {
    private val synthesizer = AVSpeechSynthesizer()
    private var voice: AVSpeechSynthesisVoice? = null

    actual fun setLanguage(language: String) {
        val isArabic = language.trim().lowercase().startsWith("ar")
        val primary = if (isArabic) "ar-SA" else "en-US"
        val fallback = if (isArabic) "ar" else "en"
        voice = AVSpeechSynthesisVoice.voiceWithLanguage(primary)
            ?: AVSpeechSynthesisVoice.voiceWithLanguage(fallback)
    }

    actual fun speak(text: String, priority: FeedbackSpeechPriority) {
        if (text.isBlank()) return
        ensureTrainingAudioSession()
        if (priority == FeedbackSpeechPriority.INTERRUPT) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        voice?.let { utterance.voice = it }
        synthesizer.speakUtterance(utterance)
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    actual fun release() {
        stop()
    }
}
