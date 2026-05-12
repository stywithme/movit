package com.trainingvalidator.poc.storage

import com.trainingvalidator.poc.training.models.CategoryInfo
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.FeedbackMessages
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.PoseVariant
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioUrlCollectorTest {

    @Test
    fun collectFromExercise_includesInlineAudioUrls() {
        val lt = LocalizedText(
            ar = "a",
            en = "b",
            audioAr = "/x/tts_ar_1.wav",
            audioEn = "https://ex/tts_en_2.wav"
        )
        val variant = PoseVariant(
            name = LocalizedText(ar = "", en = ""),
            positionChecks = emptyList(),
            trackedJoints = emptyList(),
            feedbackMessages = FeedbackMessages(
                motivational = listOf(lt),
                tips = emptyList()
            )
        )
        val cfg = ExerciseConfig(
            name = LocalizedText(ar = "n", en = "n"),
            category = CategoryInfo("c", LocalizedText(ar = "", en = "")),
            countingMethod = CountingMethod.UP_DOWN,
            equipment = emptyList(),
            poseVariants = listOf(variant)
        )
        val urls = AudioUrlCollector.collectFromExercise(cfg)
        assertTrue(urls.any { it.contains("tts_ar_1.wav") })
        assertTrue(urls.any { it.contains("tts_en_2.wav") })
    }
}
