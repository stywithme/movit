package com.movit.feature.training

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache

/**
 * DS-6 — prefetch cached voice clips when a live session opens.
 *
 * **TTS fallback:** if a clip is missing after prefetch, [com.movit.core.training.engine.feedback.FeedbackRouter]
 * speaks [com.movit.core.training.feedback.FeedbackSignal.text] via [com.movit.core.training.boundary.SpeechSynthesizer].
 */
object TrainingSessionAudioHooks {
    suspend fun prefetchOnSessionOpen(
        prefetchRunner: AudioPrefetchRunner,
        manifestCache: AudioManifestCache,
    ) {
        if (manifestCache.read() == null) return
        prefetchRunner.afterManifestApplied(isFullSync = false)
    }
}
