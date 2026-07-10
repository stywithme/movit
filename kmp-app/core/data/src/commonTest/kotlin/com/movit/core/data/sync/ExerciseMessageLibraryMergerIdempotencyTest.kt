package com.movit.core.data.sync

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.network.dto.SyncMessageContentDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.core.training.config.FeedbackMessages
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.MessageAssignment
import com.movit.core.training.config.PoseVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseMessageLibraryMergerIdempotencyTest {

    @Test
    fun resolveRecords_twice_doesNotDuplicateFeedback() {
        val library = listOf(tipTemplate("Keep going!"))
        val record = recordWithAssignment(prefilledMotivational = listOf(LocalizedText(en = "stale")))

        val once = ExerciseMessageLibraryMerger.resolveRecords(listOf(record), library).single()
        val twice = ExerciseMessageLibraryMerger.resolveRecords(listOf(once), library).single()

        assertEquals(1, once.config.poseVariants.single().feedbackMessages.motivational.size)
        assertEquals("Keep going!", once.config.poseVariants.single().feedbackMessages.motivational.single().en)
        assertEquals(1, twice.config.poseVariants.single().feedbackMessages.motivational.size)
        assertEquals(
            once.config.poseVariants.single().feedbackMessages.motivational,
            twice.config.poseVariants.single().feedbackMessages.motivational,
        )
    }

    @Test
    fun applySyncMessageLibrary_twice_isIdempotent() {
        val store = InMemoryMovitLocalStore()
        val messageLibrary = MessageLibraryCache(store)
        val repo = TrainingConfigRepository(store, messageLibrary)
        val library = listOf(tipTemplate("Keep going!"))
        messageLibrary.replaceFull(library)
        repo.seedRecord(recordWithAssignment())

        assertEquals(1, repo.applySyncMessageLibrary(library))
        assertEquals(0, repo.applySyncMessageLibrary(library))

        val motivational = repo.getExercise("idem-test")!!.poseVariants.single().feedbackMessages.motivational
        assertEquals(1, motivational.size)
        assertEquals("Keep going!", motivational.single().en)
    }

    @Test
    fun previouslyMergedConfig_reresolvesWhenLibraryTextChanges() {
        val store = InMemoryMovitLocalStore()
        val messageLibrary = MessageLibraryCache(store)
        val repo = TrainingConfigRepository(store, messageLibrary)

        val oldLibrary = listOf(tipTemplate("Old tip"))
        messageLibrary.replaceFull(oldLibrary)
        repo.seedRecord(recordWithAssignment())
        repo.applySyncMessageLibrary(oldLibrary)
        assertEquals("Old tip", repo.getExercise("idem-test")!!.poseVariants.single().feedbackMessages.motivational.single().en)

        val newLibrary = listOf(tipTemplate("New tip"))
        messageLibrary.replaceFull(newLibrary)

        // hasUnresolvedAssignments would be false (lists already filled) — fingerprint must force re-resolve.
        val config = repo.getExercise("idem-test")!!
        assertEquals("New tip", config.poseVariants.single().feedbackMessages.motivational.single().en)
        assertEquals(
            ExerciseMessageLibraryMerger.fingerprint(newLibrary),
            repo.resolveBySlug("idem-test")!!.messageLibraryFingerprint,
        )
    }

    @Test
    fun fingerprint_changesWhenContentChanges() {
        val a = ExerciseMessageLibraryMerger.fingerprint(listOf(tipTemplate("A")))
        val b = ExerciseMessageLibraryMerger.fingerprint(listOf(tipTemplate("B")))
        assertTrue(a != b)
    }

    private fun tipTemplate(en: String) = SyncMessageTemplateDto(
        id = "msg-1",
        code = "keep_going",
        content = SyncMessageContentDto(en = en),
    )

    private fun recordWithAssignment(
        prefilledMotivational: List<LocalizedText> = emptyList(),
    ): ExerciseConfigRecord = ExerciseConfigRecord.fromConfig(
        id = "ex-idem",
        slug = "idem-test",
        updatedAt = "2026-07-10",
        config = ExerciseConfig(
            poseVariants = listOf(
                PoseVariant(
                    messageAssignments = listOf(
                        MessageAssignment(
                            messageId = "msg-1",
                            target = "feedback",
                            context = "motivational",
                            sortOrder = 0,
                        ),
                    ),
                    feedbackMessages = FeedbackMessages(motivational = prefilledMotivational),
                ),
            ),
        ),
    )
}
