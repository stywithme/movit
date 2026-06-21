package com.movit.core.training.boundary

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.movit.core.training.feedback.FeedbackSpeechPriority
import java.util.concurrent.atomic.AtomicInteger

actual class SpeechSynthesizer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val utteranceId = AtomicInteger()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var language: String = "en"

    init {
        createEngine(TtsVoiceSelector.getPreferredEngine())
    }

    /**
     * Creates the TTS engine, preferring Google TTS for the best Arabic/English neural voices and
     * falling back to the system default engine when Google TTS is unavailable.
     */
    private fun createEngine(engine: String?) {
        val onInit = TextToSpeech.OnInitListener { status ->
            when {
                status == TextToSpeech.SUCCESS -> {
                    ready = true
                    applyVoice()
                }
                engine != null -> {
                    // Preferred (Google) engine unavailable — fall back to the system default.
                    runCatching { tts?.shutdown() }
                    createEngine(null)
                }
                else -> ready = false
            }
        }
        tts = if (engine != null) {
            TextToSpeech(appContext, onInit, engine)
        } else {
            TextToSpeech(appContext, onInit)
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

    private fun applyVoice() {
        val engine = tts ?: return
        TtsVoiceSelector.applyBestVoice(engine, language)
    }

    actual fun setLanguage(language: String) {
        this.language = if (language.trim().lowercase().startsWith("ar")) "ar" else "en"
        if (ready) applyVoice()
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
