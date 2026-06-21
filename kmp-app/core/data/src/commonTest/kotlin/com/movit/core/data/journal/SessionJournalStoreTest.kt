package com.movit.core.data.journal

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.SessionJournalSnapshot
import com.movit.core.training.journal.StateCode
import com.movit.core.training.journal.WorkoutExecutionMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionJournalStoreTest {
    @Test
    fun saveCheckpoint_roundTripsThroughSqlDelightInterface() {
        val store = SessionJournalStore(InMemoryMovitLocalStore())
        val snapshot = SessionJournalSnapshot(
            sessionId = "sess-1",
            exerciseId = "squat",
            trackedJoints = listOf("left_knee"),
            recordingStartMs = 1000L,
            completedRepMetrics = listOf(
                RepMetricsData(
                    num = 1,
                    durationMs = 2500,
                    worstState = StateCode.NORMAL,
                    score = 850,
                    metrics = RepMetrics(
                        rom = 600,
                        symmetry = null,
                        stability = 900,
                        tempo = listOf(800, 200, 700),
                        velocity = 45,
                        formScore = 850,
                        alignmentAccuracy = 950,
                    ),
                ),
            ),
        )
        store.saveCheckpoint(snapshot)
        val restored = store.readCheckpoint("sess-1")
        assertNotNull(restored)
        assertEquals(1, restored.completedRepMetrics.size)
        assertEquals(850, restored.completedRepMetrics.first().score)
    }

    @Test
    fun markCompleted_removesActiveCheckpoint() {
        val local = InMemoryMovitLocalStore()
        val store = SessionJournalStore(local)
        val snapshot = SessionJournalSnapshot(
            sessionId = "sess-2",
            exerciseId = "squat",
            trackedJoints = emptyList(),
            recordingStartMs = 1L,
        )
        store.saveCheckpoint(snapshot)
        store.markCompleted("sess-2")
        assertEquals(null, store.readCheckpoint("sess-2"))
        assertEquals(0, local.listActiveSessionJournals().size)
    }
}
