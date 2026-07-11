package com.movit.core.training.diagnostics

import com.movit.core.training.boundary.TrainingThroughputProfiles
import com.movit.core.training.session.SessionRunState
import kotlinx.atomicfu.atomic
import kotlin.concurrent.Volatile

/**
 * Single, low-noise diagnostics channel for the live training capture pipeline.
 *
 * Design:
 * - One consolidated line every [LOG_INTERVAL_MS] while the session is active.
 * - Optional milestone lines on state transitions (not per-frame spam).
 * - Filter logcat with tag `TrainingPipeline`.
 */
object TrainingPipelineDiagnostics {
    private const val LOG_INTERVAL_MS = 2_000L

    private val lastLogMs = atomic(0L)

    @Volatile
    private var cameraTargetFps = 0

    @Volatile
    private var cameraAnalysisSize = "?"

    @Volatile
    private var cameraAppliedFps = "?"

    @Volatile
    private var cameraThroughputProfile = TrainingThroughputProfiles.HIGH.id

    private val cameraAccepted = atomic(0)
    private val cameraSkipped = atomic(0)
    private val poseWithBody = atomic(0)
    private val poseNoBody = atomic(0)
    private val poseBusySkipped = atomic(0)
    private val poseInferenceMsTotal = atomic(0L)
    private val poseInferenceSamples = atomic(0)
    private val poseStallEvents = atomic(0)

    private val vmIngress = atomic(0)
    private val vmProcessed = atomic(0)
    private val vmConflated = atomic(0)

    /** Public gate for callers that must skip per-frame snapshot work in release (WP-07 / K-07). */
    fun isEnabled(): Boolean = isTrainingPipelineDiagnosticsEnabled()

    fun reset() {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        resetWindowCounters()
        lastLogMs.value = 0L
        cameraTargetFps = 0
        cameraAnalysisSize = "?"
        cameraAppliedFps = "?"
        cameraThroughputProfile = TrainingThroughputProfiles.HIGH.id
    }

    fun setCameraConfig(
        targetFps: Int,
        analysisWidth: Int,
        analysisHeight: Int,
        appliedFpsRange: String,
        throughputProfileId: String = TrainingThroughputProfiles.HIGH.id,
    ) {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        cameraTargetFps = targetFps
        cameraAnalysisSize = "${analysisWidth}x$analysisHeight"
        cameraAppliedFps = appliedFpsRange
        cameraThroughputProfile = throughputProfileId
        logMilestone(
            "throughput profile=$throughputProfileId " +
                "target=${targetFps}fps analysis=${analysisWidth}x$analysisHeight ae=$appliedFpsRange",
        )
    }

    fun recordCameraFrame(acceptedForAnalysis: Boolean) {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        if (acceptedForAnalysis) {
            cameraAccepted.incrementAndGet()
        } else {
            cameraSkipped.incrementAndGet()
        }
    }

    fun recordPoseResult(inferenceMs: Long, hasPose: Boolean, busySkippedSinceLastResult: Int) {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        if (hasPose) {
            poseWithBody.incrementAndGet()
        } else {
            poseNoBody.incrementAndGet()
        }
        poseInferenceMsTotal.addAndGet(inferenceMs.coerceAtLeast(0L))
        poseInferenceSamples.incrementAndGet()
        poseBusySkipped.addAndGet(busySkippedSinceLastResult.coerceAtLeast(0))
    }

    fun recordInferenceStall() {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        poseStallEvents.incrementAndGet()
    }

    fun recordVmIngress(wasConflated: Boolean) {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        vmIngress.incrementAndGet()
        if (wasConflated) {
            vmConflated.incrementAndGet()
        }
    }

    fun recordVmProcessed() {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        vmProcessed.incrementAndGet()
    }

    fun logMilestone(message: String) {
        if (!isTrainingPipelineDiagnosticsEnabled()) return
        trainingPipelineLog("milestone | $message")
    }

    /**
     * Emits one summary line if [LOG_INTERVAL_MS] elapsed. Returns whether a line was logged.
     */
    fun maybeEmitPeriodic(
        nowMs: Long,
        runState: SessionRunState,
        phase: String?,
        repCount: Int,
        targetReps: Int,
        formScore: Int,
        droppedEngine: Int,
        droppedSupervisor: Int,
        cameraActive: Boolean,
    ): Boolean {
        if (!isTrainingPipelineDiagnosticsEnabled() || !cameraActive) return false
        val previousLogMs = lastLogMs.value
        if (previousLogMs > 0L && nowMs - previousLogMs < LOG_INTERVAL_MS) return false
        if (!lastLogMs.compareAndSet(previousLogMs, nowMs)) return false

        val snapshot = captureWindowSnapshot()
        val line = buildPeriodicLine(
            snapshot = snapshot,
            runState = runState,
            phase = phase,
            repCount = repCount,
            targetReps = targetReps,
            formScore = formScore,
            droppedEngine = droppedEngine,
            droppedSupervisor = droppedSupervisor,
        )
        trainingPipelineLog(line)
        return true
    }

    internal data class WindowSnapshot(
        val cameraAccepted: Int,
        val cameraSkipped: Int,
        val cameraTargetFps: Int,
        val cameraAnalysisSize: String,
        val cameraAppliedFps: String,
        val cameraThroughputProfile: String,
        val poseWithBody: Int,
        val poseNoBody: Int,
        val poseInferenceMsTotal: Long,
        val poseInferenceSamples: Int,
        val poseBusySkipped: Int,
        val poseStallEvents: Int,
        val vmIngress: Int,
        val vmProcessed: Int,
        val vmConflated: Int,
    )

    private fun captureWindowSnapshot(): WindowSnapshot {
        return WindowSnapshot(
            cameraAccepted = cameraAccepted.getAndSet(0),
            cameraSkipped = cameraSkipped.getAndSet(0),
            cameraTargetFps = cameraTargetFps,
            cameraAnalysisSize = cameraAnalysisSize,
            cameraAppliedFps = cameraAppliedFps,
            cameraThroughputProfile = cameraThroughputProfile,
            poseWithBody = poseWithBody.getAndSet(0),
            poseNoBody = poseNoBody.getAndSet(0),
            poseInferenceMsTotal = poseInferenceMsTotal.getAndSet(0L),
            poseInferenceSamples = poseInferenceSamples.getAndSet(0),
            poseBusySkipped = poseBusySkipped.getAndSet(0),
            poseStallEvents = poseStallEvents.getAndSet(0),
            vmIngress = vmIngress.getAndSet(0),
            vmProcessed = vmProcessed.getAndSet(0),
            vmConflated = vmConflated.getAndSet(0),
        )
    }

    private fun resetWindowCounters() {
        cameraAccepted.value = 0
        cameraSkipped.value = 0
        poseWithBody.value = 0
        poseNoBody.value = 0
        poseBusySkipped.value = 0
        poseInferenceMsTotal.value = 0L
        poseInferenceSamples.value = 0
        poseStallEvents.value = 0
        vmIngress.value = 0
        vmProcessed.value = 0
        vmConflated.value = 0
    }

    internal fun buildPeriodicLine(
        snapshot: WindowSnapshot,
        runState: SessionRunState,
        phase: String?,
        repCount: Int,
        targetReps: Int,
        formScore: Int,
        droppedEngine: Int,
        droppedSupervisor: Int,
    ): String {
        val camFps = snapshot.cameraAccepted
        val camSkip = snapshot.cameraSkipped
        val poseFps = snapshot.poseWithBody + snapshot.poseNoBody
        val avgInference = if (snapshot.poseInferenceSamples == 0) {
            0
        } else {
            (snapshot.poseInferenceMsTotal / snapshot.poseInferenceSamples).toInt()
        }
        val phaseLabel = phase ?: "-"
        return buildString {
            append("window=${LOG_INTERVAL_MS / 1_000}s")
            append(" | cam=${camFps}fps")
            append("(skipThrottle=$camSkip")
            append(" profile=${snapshot.cameraThroughputProfile}")
            append(" target=${snapshot.cameraTargetFps}")
            append(" ae=${snapshot.cameraAppliedFps}")
            append(" analysis=${snapshot.cameraAnalysisSize})")
            append(" | pose=${poseFps}fps")
            append("(body=${snapshot.poseWithBody} nopose=${snapshot.poseNoBody}")
            append(" inferMs=$avgInference")
            append(" busySkip=${snapshot.poseBusySkipped}")
            if (snapshot.poseStallEvents > 0) append(" stalls=${snapshot.poseStallEvents}")
            append(')')
            append(" | vm=in ${snapshot.vmIngress}")
            append(" proc=${snapshot.vmProcessed}")
            val workerLag = snapshot.vmIngress - snapshot.vmProcessed
            if (workerLag > 0) append(" workerLag=$workerLag")
            append(" conflated=${snapshot.vmConflated}")
            append(" | engine=state=$runState")
            append(" phase=$phaseLabel")
            append(" reps=$repCount/$targetReps")
            append(" score=$formScore")
            append(" drop=$droppedEngine")
            append(" | supervisor=drop=$droppedSupervisor")
        }
    }
}
