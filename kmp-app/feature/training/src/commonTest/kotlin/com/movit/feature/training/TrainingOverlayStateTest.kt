package com.movit.feature.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingOverlayStateTest {
    @Test
    fun defaults_areEmptyPreviewSafe() {
        val overlay = TrainingOverlayState()
        assertEquals(null, overlay.landmarks)
        assertEquals(0, overlay.analysisWidth)
        assertTrue(overlay.romIndicators.isEmpty())
        assertTrue(overlay.jointVisuals.isEmpty())
    }

    @Test
    fun r1Flag_defaultsOn() {
        assertTrue(TrainingUiStateSplitFlags.r1OverlayFlowEnabled)
    }
}
