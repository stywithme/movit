package com.movit.feature.training

import com.movit.core.training.boundary.NoOpTrainingFrameSnapshotPort
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCapture
import com.movit.core.training.report.MovitPeakFrameCaptureManager
import com.movit.core.training.session.HoldStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Wires engine moments to [MovitPeakFrameCaptureManager] + optional [TrainingFrameSnapshotPort].
 * Replay burst sampling is intentionally out of scope for v1.
 */
class TrainingFrameCaptureCoordinator(
    private val sessionId: String,
    private val scope: CoroutineScope,
    private val snapshotPort: TrainingFrameSnapshotPort = NoOpTrainingFrameSnapshotPort,
    private val manager: MovitPeakFrameCaptureManager = MovitPeakFrameCaptureManager(),
    private var captureSeq: Int = 0,
) {
    private var lastHoldSampleElapsedMs = -1L
    private val pendingCaptureJobs = mutableListOf<Job>()

    fun onPhaseChanged(phase: Phase, repInProgress: Int) {
        if (phase == Phase.BOTTOM) {
            requestCapture(
                repNumber = repInProgress,
                phase = phase,
                captureType = MovitPeakCaptureType.PEAK_FRAME,
            )
        }
    }

    fun onJointState(jointCode: String, state: JointState, repInProgress: Int, phase: Phase) {
        when (state) {
            JointState.DANGER -> requestCapture(
                repNumber = repInProgress,
                phase = phase,
                captureType = MovitPeakCaptureType.DANGER_FRAME,
                errorKey = "$jointCode:DANGER",
            )
            JointState.WARNING -> requestCapture(
                repNumber = repInProgress,
                phase = phase,
                captureType = MovitPeakCaptureType.ERROR_FRAME,
                errorKey = "$jointCode:WARNING",
            )
            else -> Unit
        }
    }

    fun onJointError(error: JointError, repInProgress: Int, phase: Phase) {
        val errorKey = "${error.jointCode}:${error.state.name}:${error.errorType.name}"
        val captureType = when (error.state) {
            JointState.DANGER -> MovitPeakCaptureType.DANGER_FRAME
            else -> MovitPeakCaptureType.ERROR_FRAME
        }
        requestCapture(
            repNumber = repInProgress,
            phase = phase,
            captureType = captureType,
            errorKey = errorKey,
        )
    }

    fun onHoldStatus(status: HoldStatus, phase: Phase) {
        val elapsed = status.elapsedMs
        if (elapsed < HOLD_SAMPLE_INTERVAL_MS) return
        if (lastHoldSampleElapsedMs >= 0 && elapsed - lastHoldSampleElapsedMs < HOLD_SAMPLE_INTERVAL_MS) {
            return
        }
        lastHoldSampleElapsedMs = elapsed
        requestCapture(
            repNumber = 0,
            phase = phase,
            captureType = MovitPeakCaptureType.HOLD_SAMPLE,
            errorKey = "hold_${elapsed}ms",
        )
    }

    fun onRepCompleted(repNumber: Int, isCounted: Boolean) {
        if (isCounted) {
            manager.markBestRep(repNumber)
        }
    }

    fun captures(): List<MovitPeakFrameCapture> = manager.captures()

    /** Waits for in-flight snapshot jobs before building post-training reports. */
    suspend fun awaitPendingCaptures() {
        pendingCaptureJobs.toList().joinAll()
    }

    fun resetForNextExercise() {
        lastHoldSampleElapsedMs = -1L
        captureSeq = 0
        pendingCaptureJobs.clear()
    }

    private fun requestCapture(
        repNumber: Int,
        phase: Phase,
        captureType: MovitPeakCaptureType,
        errorKey: String? = null,
    ) {
        if (!manager.canCapture(captureType, repNumber, errorKey)) return
        if (!snapshotPort.isAvailable) return
        val captureId = nextCaptureId()
        val job = scope.launch {
            val persisted = snapshotPort.persistSnapshot(sessionId, captureId) ?: return@launch
            manager.tryRegister(
                MovitPeakFrameCaptureManager.RegisterRequest(
                    repNumber = repNumber,
                    phaseCode = phase.ordinal.toByte(),
                    captureType = captureType,
                    localPath = persisted.localPath,
                    thumbnailPath = persisted.thumbnailPath,
                    errorKey = errorKey,
                    id = captureId,
                ),
            )
        }
        pendingCaptureJobs += job
        job.invokeOnCompletion { pendingCaptureJobs.remove(job) }
    }

    private fun nextCaptureId(): String {
        captureSeq += 1
        return "frame-$sessionId-$captureSeq"
    }

    private companion object {
        const val HOLD_SAMPLE_INTERVAL_MS = 5_000L
    }
}
