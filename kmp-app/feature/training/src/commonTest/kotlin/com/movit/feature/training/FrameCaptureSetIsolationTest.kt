package com.movit.feature.training

import com.movit.core.training.boundary.PersistedFrameSnapshot
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import com.movit.core.training.engine.Phase
import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FrameCaptureSetIsolationTest {

    @Test
    fun setTwoRepOne_doesNotReuseSetOnePeakFrame() {
        val manager = MovitPeakFrameCaptureManager()

        val setOnePeak = manager.tryRegister(
            peakRequest(setNumber = 1, repNumber = 1, path = "/tmp/set1-rep1.jpg"),
        )
        val setTwoPeak = manager.tryRegister(
            peakRequest(setNumber = 2, repNumber = 1, path = "/tmp/set2-rep1.jpg"),
        )

        assertNotNull(setOnePeak)
        assertNotNull(setTwoPeak)
        assertEquals("/tmp/set1-rep1.jpg", setOnePeak.localPath)
        assertEquals("/tmp/set2-rep1.jpg", setTwoPeak.localPath)
        assertEquals(2, manager.captures().size)
    }

    @Test
    fun coordinator_beginSet_clearsPreviousSetCaptures() {
        val manager = MovitPeakFrameCaptureManager()
        val coordinator = TrainingFrameCaptureCoordinator(
            sessionId = "session-set-isolation",
            scope = CoroutineScope(Dispatchers.Unconfined),
            snapshotPort = NoopSnapshotPort(),
            manager = manager,
        )

        coordinator.beginSet(1)
        manager.tryRegister(peakRequest(setNumber = 1, repNumber = 1, path = "/tmp/set1-rep1.jpg"))
        assertEquals(1, coordinator.captures().size)

        coordinator.beginSet(2)
        manager.tryRegister(peakRequest(setNumber = 2, repNumber = 1, path = "/tmp/set2-rep1.jpg"))

        assertEquals(1, coordinator.captures().size)
        assertEquals("/tmp/set2-rep1.jpg", coordinator.captures().single().localPath)
        assertNull(manager.tryRegister(peakRequest(setNumber = 2, repNumber = 1, path = "/tmp/set2-rep1-dup.jpg")))
    }

    private fun peakRequest(
        setNumber: Int,
        repNumber: Int,
        path: String,
    ): MovitPeakFrameCaptureManager.RegisterRequest = MovitPeakFrameCaptureManager.RegisterRequest(
        repNumber = repNumber,
        setNumber = setNumber,
        phaseCode = Phase.BOTTOM.ordinal.toByte(),
        captureType = MovitPeakCaptureType.PEAK_FRAME,
        localPath = path,
    )

    private class NoopSnapshotPort : TrainingFrameSnapshotPort {
        override val isAvailable: Boolean = false

        override suspend fun persistSnapshot(
            sessionId: String,
            captureId: String,
        ): PersistedFrameSnapshot? = null
    }
}
