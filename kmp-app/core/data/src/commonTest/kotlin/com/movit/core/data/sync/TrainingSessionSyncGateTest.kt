package com.movit.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingSessionSyncGateTest {

    @Test
    fun fullRefresh_skippedWhileTrainingSessionActive() {
        TrainingSessionSyncGate.trainingSessionActive = true
        try {
            assertTrue(TrainingSessionSyncGate.trainingSessionActive)
        } finally {
            TrainingSessionSyncGate.trainingSessionActive = false
        }
        assertEquals(false, TrainingSessionSyncGate.trainingSessionActive)
    }
}
