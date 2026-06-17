package com.movit.core.data.audio

import com.movit.core.training.boundary.AudioFeedbackPlayer
import com.movit.core.training.boundary.SpeechSynthesizer
import com.movit.core.training.feedback.FeedbackSeverity
import com.movit.core.training.feedback.FeedbackSignal
import com.movit.core.training.feedback.FeedbackSpeechPriority

/**
 * Plays prefetched manifest clips when available; falls back to [SpeechSynthesizer] (legacy parity).
 */
class CachedAudioFeedbackPlayer(
    private val audioCache: AudioFileDownloadPort,
    private val mediaPlayer: MovitAudioPlayer,
    private val speech: SpeechSynthesizer,
) : AudioFeedbackPlayer {
    override fun prepare() = Unit

    override fun play(signal: FeedbackSignal) {
        val localPath = AudioClipResolver.resolveLocalPath(audioCache, signal.audioUrl)
        if (localPath != null && mediaPlayer.playFile(localPath)) {
            return
        }
        if (signal.text.isNotBlank()) {
            speech.speak(signal.text, speechPriorityFor(signal))
        }
    }

    override fun stopAll() {
        mediaPlayer.stop()
        speech.stop()
    }

    override fun release() {
        stopAll()
        speech.release()
    }

    private fun speechPriorityFor(signal: FeedbackSignal): FeedbackSpeechPriority = when {
        signal.severity.priority >= FeedbackSeverity.CRITICAL.priority -> FeedbackSpeechPriority.INTERRUPT
        signal.severity.priority >= FeedbackSeverity.ERROR.priority -> FeedbackSpeechPriority.INTERRUPT
        signal.severity.priority >= FeedbackSeverity.WARNING.priority -> FeedbackSpeechPriority.NORMAL
        else -> FeedbackSpeechPriority.LOW
    }
}
