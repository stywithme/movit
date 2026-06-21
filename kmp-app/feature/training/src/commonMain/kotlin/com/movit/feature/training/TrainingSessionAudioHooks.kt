package com.movit.feature.training

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.EntityAudioManifestFetcher

/**
 * DS-6 — prefetch cached voice clips when a live session opens.
 *
 * **TTS fallback:** if a clip is missing after prefetch, [com.movit.core.training.engine.feedback.FeedbackRouter]
 * speaks [com.movit.core.training.feedback.FeedbackSignal.text] via [com.movit.core.training.boundary.SpeechSynthesizer].
 */
object TrainingSessionAudioHooks {
    suspend fun prefetchOnSessionOpen(
        prefetchRunner: AudioPrefetchRunner,
        exerciseSlug: String,
        workoutTemplateId: String? = null,
    ) {
        val hasEntityTargets = exerciseSlug.isNotBlank() || !workoutTemplateId.isNullOrBlank()
        if (!hasEntityTargets) {
            prefetchRunner.afterManifestApplied(isFullSync = false)
            return
        }
        prefetchRunner.prefetchForTargets(
            EntityAudioManifestFetcher.Targets(
                exerciseSlugs = listOfNotNull(exerciseSlug.takeIf { it.isNotBlank() }),
                workoutTemplateIds = listOfNotNull(workoutTemplateId?.takeIf { it.isNotBlank() }),
            ),
        )
    }
}
