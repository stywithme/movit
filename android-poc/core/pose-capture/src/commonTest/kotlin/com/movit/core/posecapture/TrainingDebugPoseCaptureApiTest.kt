package com.movit.core.posecapture

import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugVideoFrameSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingDebugVideoFrameSelectorTest {
    @Test
    fun firstFrameAlwaysProcesses() {
        assertTrue(
            TrainingDebugVideoFrameSelector.shouldProcessFrame(
                videoTimestampMs = 0L,
                lastProcessedVideoTimestampMs = -1L,
                isProcessingFrame = false,
            ),
        )
    }

    @Test
    fun skipsWhileProcessing() {
        assertFalse(
            TrainingDebugVideoFrameSelector.shouldProcessFrame(
                videoTimestampMs = 100L,
                lastProcessedVideoTimestampMs = 0L,
                isProcessingFrame = true,
            ),
        )
    }

    @Test
    fun processesAtDeterministicInterval() {
        assertFalse(
            TrainingDebugVideoFrameSelector.shouldProcessFrame(
                videoTimestampMs = 20L,
                lastProcessedVideoTimestampMs = 0L,
                isProcessingFrame = false,
            ),
        )
        assertTrue(
            TrainingDebugVideoFrameSelector.shouldProcessFrame(
                videoTimestampMs = 33L,
                lastProcessedVideoTimestampMs = 0L,
                isProcessingFrame = false,
            ),
        )
    }

    @Test
    fun intervalMatchesLegacyThirtyFps() {
        assertEquals(33L, TrainingDebugVideoFrameSelector.DETERMINISTIC_FRAME_INTERVAL_MS)
    }
}

class PoseModelTypeTest {
    @Test
    fun preferenceRoundTrip() {
        assertEquals(PoseModelType.HEAVY, PoseModelType.fromPreference("heavy"))
        assertEquals(PoseModelType.FULL, PoseModelType.fromPreference("FULL"))
        assertEquals("heavy", PoseModelType.toPreference(PoseModelType.HEAVY))
        assertEquals("full", PoseModelType.toPreference(PoseModelType.FULL))
    }
}
