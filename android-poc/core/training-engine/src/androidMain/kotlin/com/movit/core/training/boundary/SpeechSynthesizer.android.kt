package com.movit.core.training.boundary

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.movit.core.training.feedback.FeedbackSpeechPriority
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

actual class SpeechSynthesizer(
    context: Context,
) {
    private val utteranceId = AtomicInteger()
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
            }
        }
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = Unit
            },
        )
    }

    actual fun speak(text: String, priority: FeedbackSpeechPriority) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        if (priority == FeedbackSpeechPriority.INTERRUPT) {
            engine.stop()
        }
        val id = "movit-${utteranceId.incrementAndGet()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    actual fun stop() {
        tts?.stop()
    }

    actual fun release() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
