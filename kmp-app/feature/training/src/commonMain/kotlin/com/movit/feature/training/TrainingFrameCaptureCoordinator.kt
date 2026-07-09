package com.movit.feature.training

import com.movit.core.training.boundary.NoOpTrainingFrameSnapshotPort
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.model.JointAngles
import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCapture
import com.movit.core.training.report.MovitPeakFrameCaptureManager
import com.movit.core.training.report.MovitRepReplayClip
import com.movit.core.training.report.MovitRepReplaySampler
import com.movit.core.training.session.HoldStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Wires engine moments to [MovitPeakFrameCaptureManager], [MovitRepReplaySampler],
 * and optional [TrainingFrameSnapshotPort].
 */
class TrainingFrameCaptureCoordinator(
    private val sessionId: String,
    private val scope: CoroutineScope,
    private val snapshotPort: TrainingFrameSnapshotPort = NoOpTrainingFrameSnapshotPort,
    private val manager: MovitPeakFrameCaptureManager = MovitPeakFrameCaptureManager(),
    private val replaySampler: MovitRepReplaySampler = MovitRepReplaySampler(),
    private var captureSeq: Int = 0,
    private var replaySeq: Int = 0,
) {
    var currentSetNumber: Int = 1
        private set

    private var lastHoldSampleElapsedMs = -1L
    private val pendingCaptureJobs = mutableListOf<Job>()
    private var replaySamplerJob: Job? = null
    private var replayRepProvider: (() -> Int)? = null

    fun beginSet(setNumber: Int) {
        if (setNumber > 1) {
            resetForNextSet()
        }
        currentSetNumber = setNumber.coerceAtLeast(1)
    }

    fun onPhaseChanged(phase: Phase, repInProgress: Int, angles: JointAngles? = null) {
        if (phase == Phase.BOTTOM) {
            requestCapture(
                repNumber = repInProgress,
                phase = phase,
                captureType = MovitPeakCaptureType.PEAK_FRAME,
                angles = angles,
            )
        }
    }

    fun onJointState(
        jointCode: String,
        state: JointState,
        repInProgress: Int,
        phase: Phase,
        angles: JointAngles? = null,
    ) {
        when (state) {
            JointState.DANGER -> requestCapture(
                repNumber = repInProgress,
                phase = phase,
                captureType = MovitPeakCaptureType.DANGER_FRAME,
                errorKey = "$jointCode:DANGER",
                angles = angles,
            )
            JointState.WARNING -> requestCapture(
                repNumber = repInProgress,
                phase = phase,
                captureType = MovitPeakCaptureType.ERROR_FRAME,
                errorKey = "$jointCode:WARNING",
                angles = angles,
            )
            else -> Unit
        }
    }

    fun onJointError(
        error: JointError,
        repInProgress: Int,
        phase: Phase,
        angles: JointAngles? = null,
    ) {
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
            angles = angles,
        )
    }

    fun onHoldStatus(status: HoldStatus, phase: Phase, angles: JointAngles? = null) {
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
            angles = angles,
        )
    }

    fun onRepCompleted(repNumber: Int, isCounted: Boolean) {
        if (isCounted) {
            manager.markBestRep(repNumber, currentSetNumber)
        }
    }

    fun startReplaySampler(repInProgress: () -> Int) {
        if (!snapshotPort.isAvailable) return
        stopReplaySampler()
        replayRepProvider = repInProgress
        replaySamplerJob = scope.launch {
            sampleReplayFrame()
            while (isActive) {
                delay(MovitRepReplaySampler.SAMPLE_INTERVAL_MS)
                sampleReplayFrame()
            }
        }
    }

    fun stopReplaySampler() {
        replaySamplerJob?.cancel()
        replaySamplerJob = null
        replayRepProvider = null
    }

    fun captures(): List<MovitPeakFrameCapture> =
        manager.captures().filter { it.setNumber == currentSetNumber }

    fun replayClips(): List<MovitRepReplayClip> = replaySampler.clips(currentSetNumber)

    /** Waits for in-flight snapshot jobs before building post-training reports. */
    suspend fun awaitPendingCaptures() {
        pendingCaptureJobs.toList().joinAll()
    }

    fun resetForNextSet() {
        stopReplaySampler()
        lastHoldSampleElapsedMs = -1L
        captureSeq = 0
        replaySeq = 0
        pendingCaptureJobs.forEach { it.cancel() }
        pendingCaptureJobs.clear()
        manager.clear()
        replaySampler.clear()
    }

    fun resetForNextExercise() {
        resetForNextSet()
        currentSetNumber = 1
    }

    private fun requestCapture(
        repNumber: Int,
        phase: Phase,
        captureType: MovitPeakCaptureType,
        errorKey: String? = null,
        angles: JointAngles? = null,
    ) {
        val setNumber = currentSetNumber
        if (!manager.canCapture(captureType, repNumber, setNumber, errorKey)) return
        if (!snapshotPort.isAvailable) return
        val captureId = nextCaptureId()
        val angleMap = angles?.toMap().orEmpty()
        val job = scope.launch {
            val persisted = snapshotPort.persistSnapshot(sessionId, captureId) ?: return@launch
            manager.tryRegister(
                MovitPeakFrameCaptureManager.RegisterRequest(
                    repNumber = repNumber,
                    setNumber = setNumber,
                    phaseCode = phase.ordinal.toByte(),
                    captureType = captureType,
                    localPath = persisted.localPath,
                    thumbnailPath = persisted.thumbnailPath,
                    errorKey = errorKey,
                    angles = angleMap,
                    id = captureId,
                ),
            )
        }
        pendingCaptureJobs += job
        job.invokeOnCompletion { pendingCaptureJobs.remove(job) }
    }

    private suspend fun sampleReplayFrame() {
        val repNumber = replayRepProvider?.invoke() ?: return
        val setNumber = currentSetNumber
        if (repNumber < 1 || !replaySampler.canSample(repNumber, setNumber)) return
        val captureId = nextReplayCaptureId(repNumber, setNumber)
        val persisted = snapshotPort.persistReplaySnapshot(sessionId, captureId) ?: return
        replaySampler.tryRegisterFrame(repNumber, persisted.localPath, setNumber)
    }

    private fun nextCaptureId(): String {
        captureSeq += 1
        return "frame-$sessionId-set$currentSetNumber-$captureSeq"
    }

    private fun nextReplayCaptureId(repNumber: Int, setNumber: Int = currentSetNumber): String {
        replaySeq += 1
        return "set${setNumber}_rep${repNumber}_replay_${replaySeq - 1}"
    }

    private companion object {
        const val HOLD_SAMPLE_INTERVAL_MS = 5_000L
    }
}
