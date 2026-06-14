package com.movit.feature.training

import com.movit.core.training.boundary.PersistedFrameSnapshot
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.model.JointAngles
import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCaptureManager
import com.movit.core.training.report.MovitRepReplaySampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingFrameCaptureCoordinatorTest {

    @Test
    fun peakPhase_registersCaptureWhenPortAvailable() = runBlocking {
        val manager = MovitPeakFrameCaptureManager()
        val coordinator = coordinator(manager)
        coordinator.onPhaseChanged(Phase.BOTTOM, repInProgress = 1)
        assertEquals(1, manager.captures().size)
        assertEquals(MovitPeakCaptureType.PEAK_FRAME, manager.captures().single().captureType)
    }

    @Test
    fun jointDanger_requestsDangerCapture() = runBlocking {
        val manager = MovitPeakFrameCaptureManager()
        val coordinator = coordinator(manager)
        coordinator.onJointState(
            "left_knee",
            JointState.DANGER,
            repInProgress = 2,
            phase = Phase.DOWN,
            angles = JointAngles(leftKnee = 42.0),
        )
        val capture = manager.captures().single()
        assertEquals(MovitPeakCaptureType.DANGER_FRAME, capture.captureType)
        assertEquals(42.0, capture.metadata.angles["left_knee"])
        assertTrue(capture.metadata.hasError)
    }

    @Test
    fun replaySampler_collectsBurstFrames() = runBlocking {
        val replaySampler = MovitRepReplaySampler()
        val coordinator = TrainingFrameCaptureCoordinator(
            sessionId = "session-replay",
            scope = this,
            snapshotPort = ReplaySnapshotPort(),
            replaySampler = replaySampler,
        )
        coordinator.startReplaySampler { 1 }
        delay(MovitRepReplaySampler.SAMPLE_INTERVAL_MS * 2 + 60L)
        coordinator.stopReplaySampler()
        assertEquals(1, replaySampler.clips().single().repNumber)
    }

    private class ReplaySnapshotPort : TrainingFrameSnapshotPort {
        private var seq = 0
        override val isAvailable: Boolean = true

        override suspend fun persistSnapshot(sessionId: String, captureId: String): PersistedFrameSnapshot? =
            PersistedFrameSnapshot(localPath = "/tmp/$sessionId/$captureId.jpg")

        override suspend fun persistReplaySnapshot(sessionId: String, captureId: String): PersistedFrameSnapshot? {
            seq += 1
            return PersistedFrameSnapshot(localPath = "/tmp/$sessionId/replay-$seq.jpg")
        }
    }

    @Test
    fun repCompleted_marksBestRepWhenPeakExists() = runBlocking {
        val manager = MovitPeakFrameCaptureManager()
        manager.tryRegister(
            MovitPeakFrameCaptureManager.RegisterRequest(
                repNumber = 1,
                phaseCode = Phase.BOTTOM.ordinal.toByte(),
                captureType = MovitPeakCaptureType.PEAK_FRAME,
                localPath = "/tmp/peak.jpg",
            ),
        )
        val coordinator = coordinator(manager)
        coordinator.onRepCompleted(repNumber = 1, isCounted = true)
        assertTrue(manager.captures().single().captureType == MovitPeakCaptureType.BEST_REP)
    }

    @Test
    fun awaitPendingCaptures_waitsForAsyncSnapshotJobs() = runBlocking {
        val manager = MovitPeakFrameCaptureManager()
        val coordinator = TrainingFrameCaptureCoordinator(
            sessionId = "session-1",
            scope = this,
            snapshotPort = DelayedSnapshotPort(),
            manager = manager,
        )
        coordinator.onPhaseChanged(Phase.BOTTOM, repInProgress = 1)
        assertEquals(0, manager.captures().size)
        coordinator.awaitPendingCaptures()
        assertEquals(1, manager.captures().size)
    }

    private class DelayedSnapshotPort : TrainingFrameSnapshotPort {
        override val isAvailable: Boolean = true

        override suspend fun persistSnapshot(sessionId: String, captureId: String): PersistedFrameSnapshot? {
            delay(25)
            return PersistedFrameSnapshot(
                localPath = "/tmp/$sessionId/$captureId.jpg",
                thumbnailPath = "/tmp/$sessionId/${captureId}_thumb.jpg",
            )
        }
    }

    private fun coordinator(manager: MovitPeakFrameCaptureManager): TrainingFrameCaptureCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return TrainingFrameCaptureCoordinator(
            sessionId = "session-1",
            scope = scope,
            snapshotPort = RecordingSnapshotPort(),
            manager = manager,
        )
    }

    private class RecordingSnapshotPort : TrainingFrameSnapshotPort {
        override val isAvailable: Boolean = true

        override suspend fun persistSnapshot(sessionId: String, captureId: String): PersistedFrameSnapshot? =
            PersistedFrameSnapshot(
                localPath = "/tmp/$sessionId/$captureId.jpg",
                thumbnailPath = "/tmp/$sessionId/${captureId}_thumb.jpg",
            )
    }
}
