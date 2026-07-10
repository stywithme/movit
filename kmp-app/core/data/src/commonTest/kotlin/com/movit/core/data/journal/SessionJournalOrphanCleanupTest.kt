package com.movit.core.data.journal

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.SessionJournalSnapshot
import com.movit.core.training.journal.StateCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionJournalOrphanCleanupTest {
    private val nowMs = 10_000_000L
    private val sixHoursMs = SessionJournalStore.ORPHAN_MAX_AGE_MS

    @Test
    fun cleanup_deletesStaleActiveJournals() {
        val local = InMemoryMovitLocalStore()
        val store = SessionJournalStore(local)
        store.saveCheckpoint(snapshot("old", "squat", reps = 3))
        local.upsertSessionJournal(
            sessionId = "old",
            exerciseId = "squat",
            payloadJson = local.selectSessionJournal("old")!!.payloadJson,
            status = "active",
            updatedAtEpochMs = nowMs - sixHoursMs - 1,
        )

        val resume = store.cleanupOrphansAndFindResume("squat", nowMs = nowMs)

        assertNull(resume)
        assertEquals(0, store.listActiveCheckpoints().size)
        assertNull(store.readCheckpoint("old"))
    }

    @Test
    fun cleanup_returnsFreshJournalForSameExercise() {
        val local = InMemoryMovitLocalStore()
        val store = SessionJournalStore(local)
        store.saveCheckpoint(snapshot("fresh", "squat", reps = 7))
        local.upsertSessionJournal(
            sessionId = "fresh",
            exerciseId = "squat",
            payloadJson = local.selectSessionJournal("fresh")!!.payloadJson,
            status = "active",
            updatedAtEpochMs = nowMs - 60_000L,
        )

        val resume = store.cleanupOrphansAndFindResume("squat", nowMs = nowMs)

        assertNotNull(resume)
        assertEquals("fresh", resume.sessionId)
        assertEquals(7, resume.completedRepMetrics.size)
        assertEquals(1, store.listActiveCheckpoints().size)
    }

    @Test
    fun cleanup_ignoresOtherExercises_andPicksNewest() {
        val local = InMemoryMovitLocalStore()
        val store = SessionJournalStore(local)
        store.saveCheckpoint(snapshot("curl", "curl", reps = 2))
        store.saveCheckpoint(snapshot("older-squat", "squat", reps = 4))
        store.saveCheckpoint(snapshot("newer-squat", "squat", reps = 9))
        local.upsertSessionJournal(
            sessionId = "curl",
            exerciseId = "curl",
            payloadJson = local.selectSessionJournal("curl")!!.payloadJson,
            status = "active",
            updatedAtEpochMs = nowMs - 10_000L,
        )
        local.upsertSessionJournal(
            sessionId = "older-squat",
            exerciseId = "squat",
            payloadJson = local.selectSessionJournal("older-squat")!!.payloadJson,
            status = "active",
            updatedAtEpochMs = nowMs - 120_000L,
        )
        local.upsertSessionJournal(
            sessionId = "newer-squat",
            exerciseId = "squat",
            payloadJson = local.selectSessionJournal("newer-squat")!!.payloadJson,
            status = "active",
            updatedAtEpochMs = nowMs - 30_000L,
        )

        val resume = store.cleanupOrphansAndFindResume("squat", nowMs = nowMs)

        assertNotNull(resume)
        assertEquals("newer-squat", resume.sessionId)
        assertEquals(9, resume.completedRepMetrics.size)
        assertEquals(3, store.listActiveCheckpoints().size)
    }

    @Test
    fun markCompleted_deletesWithoutWritingCompletedStatus() {
        val local = InMemoryMovitLocalStore()
        val store = SessionJournalStore(local)
        store.saveCheckpoint(snapshot("done", "squat", reps = 1))
        store.markCompleted("done")
        assertNull(store.readCheckpoint("done"))
        assertEquals(0, local.listActiveSessionJournals().size)
        assertNull(local.selectSessionJournal("done"))
    }

    private fun snapshot(sessionId: String, exerciseId: String, reps: Int) = SessionJournalSnapshot(
        sessionId = sessionId,
        exerciseId = exerciseId,
        trackedJoints = listOf("left_knee"),
        recordingStartMs = 1L,
        completedRepMetrics = List(reps) { index ->
            RepMetricsData(
                num = index + 1,
                durationMs = 2_000,
                worstState = StateCode.NORMAL,
                score = 800,
                metrics = RepMetrics(
                    rom = 600,
                    symmetry = null,
                    stability = 900,
                    tempo = listOf(800, 200, 700),
                    velocity = 45,
                    formScore = 800,
                    alignmentAccuracy = 900,
                ),
            )
        },
    )
}
