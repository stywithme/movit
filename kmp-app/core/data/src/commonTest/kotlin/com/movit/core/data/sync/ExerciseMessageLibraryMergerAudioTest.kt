package com.movit.core.data.sync

import com.movit.core.network.dto.SyncMessageContentDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.FeedbackMessages
import com.movit.core.training.config.MessageAssignment
import com.movit.core.training.config.PoseVariant
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseMessageLibraryMergerAudioTest {

    @Test
    fun resolveExerciseConfigs_copiesAudioUrlsIntoLocalizedText() {
        val audioEn = "https://cdn.example/audio_en.wav"
        val audioAr = "https://cdn.example/audio_ar.wav"
        val library = listOf(
            SyncMessageTemplateDto(
                id = "msg-audio",
                code = "brace_core",
                content = SyncMessageContentDto(
                    en = "Brace your core",
                    ar = "شدّ بطنك",
                    audioEn = audioEn,
                    audioAr = audioAr,
                ),
            ),
        )
        val exercise = ExerciseConfig(
            poseVariants = listOf(
                PoseVariant(
                    messageAssignments = listOf(
                        MessageAssignment(
                            messageId = "msg-audio",
                            target = "feedback",
                            context = "motivational",
                            sortOrder = 0,
                        ),
                    ),
                    feedbackMessages = FeedbackMessages(),
                ),
            ),
        )

        val resolved = ExerciseMessageLibraryMerger.resolveExerciseConfigs(listOf(exercise), library)
        val message = resolved.single().poseVariants.single().feedbackMessages.motivational.single()

        assertEquals("Brace your core", message.en)
        assertEquals("شدّ بطنك", message.ar)
        assertEquals(audioEn, message.audioEn)
        assertEquals(audioAr, message.audioAr)
    }

    @Test
    fun resolveExerciseConfigs_textOnlyContent_leavesAudioNull() {
        val library = listOf(
            SyncMessageTemplateDto(
                id = "msg-text",
                code = "keep_going",
                content = SyncMessageContentDto(en = "Keep going!"),
            ),
        )
        val exercise = ExerciseConfig(
            poseVariants = listOf(
                PoseVariant(
                    messageAssignments = listOf(
                        MessageAssignment(
                            messageId = "msg-text",
                            target = "feedback",
                            context = "tip",
                            sortOrder = 0,
                        ),
                    ),
                ),
            ),
        )

        val resolved = ExerciseMessageLibraryMerger.resolveExerciseConfigs(listOf(exercise), library)
        val message = resolved.single().poseVariants.single().feedbackMessages.tips.single()

        assertEquals("Keep going!", message.en)
        assertEquals(null, message.audioEn)
        assertEquals(null, message.audioAr)
    }
}
