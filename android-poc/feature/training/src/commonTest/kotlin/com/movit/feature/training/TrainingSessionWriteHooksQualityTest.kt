package com.movit.feature.training

import com.movit.core.training.report.SessionQualityMeta
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingSessionWriteHooksQualityTest {
    @Test
    fun sessionQualityMeta_mergeMatchesWriteHooksPolicy() {
        val base = SessionQualityMeta.fromFrameStats(
            framesOffered = 10,
            framesRecorded = 8,
            framesDropped = 1,
            jointCoverageRatio = 0.9f,
            visibilityPauseCount = 0,
            cameraWarningCount = 0,
        )
        val ingressFramesDropped = 4
        val visibilityPauseCount = 2
        val cameraWarningCount = 3

        val merged = SessionQualityMeta.fromFrameStats(
            framesOffered = base.framesOffered + ingressFramesDropped,
            framesRecorded = base.framesRecorded,
            framesDropped = base.framesDropped + ingressFramesDropped,
            jointCoverageRatio = base.jointCoverageRatio,
            visibilityPauseCount = visibilityPauseCount,
            cameraWarningCount = cameraWarningCount,
        )

        assertEquals(14, merged.framesOffered)
        assertEquals(5, merged.framesDropped)
        assertEquals(2, merged.visibilityPauseCount)
        assertEquals(3, merged.cameraWarningCount)
    }
}
