package com.movit.core.training.diagnostics

import com.movit.core.training.session.SessionRunState

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

    private val lock = Any()

    private var lastLogMs = 0L
    private var cameraTargetFps = 0
    private var cameraAnalysisSize = "?"
    private var cameraAppliedFps = "?"

    private var cameraAccepted = 0
    private var cameraSkipped = 0
    private var poseWithBody = 0
    private var poseNoBody = 0
    private var poseBusySkipped = 0
    private var poseInferenceMsTotal = 0L
    private var poseInferenceSamples = 0
    private var poseStallEvents = 0

    private var vmIngress = 0
    private var vmProcessed = 0
    private var vmConflated = 0

    fun reset() {
        synchronized(lock) {
            resetWindowCounters()
            lastLogMs = 0L
            cameraTargetFps = 0
            cameraAnalysisSize = "?"
            cameraAppliedFps = "?"
        }
    }

    fun setCameraConfig(targetFps: Int, analysisWidth: Int, analysisHeight: Int, appliedFpsRange: String) {
        synchronized(lock) {
            cameraTargetFps = targetFps
            cameraAnalysisSize = "${analysisWidth}x$analysisHeight"
            cameraAppliedFps = appliedFpsRange
        }
    }

    fun recordCameraFrame(acceptedForAnalysis: Boolean) {
        synchronized(lock) {
            if (acceptedForAnalysis) cameraAccepted++ else cameraSkipped++
        }
    }

    fun recordPoseResult(inferenceMs: Long, hasPose: Boolean, busySkippedSinceLastResult: Int) {
        synchronized(lock) {
            if (hasPose) poseWithBody++ else poseNoBody++
            poseInferenceMsTotal += inferenceMs.coerceAtLeast(0L)
            poseInferenceSamples++
            poseBusySkipped += busySkippedSinceLastResult.coerceAtLeast(0)
        }
    }

    fun recordInferenceStall() {
        synchronized(lock) {
            poseStallEvents++
        }
    }

    fun recordVmIngress(wasConflated: Boolean) {
        synchronized(lock) {
            vmIngress++
            if (wasConflated) vmConflated++
        }
    }

    fun recordVmProcessed() {
        synchronized(lock) {
            vmProcessed++
        }
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
        synchronized(lock) {
            if (lastLogMs > 0L && nowMs - lastLogMs < LOG_INTERVAL_MS) return false
            val line = buildPeriodicLine(
                runState = runState,
                phase = phase,
                repCount = repCount,
                targetReps = targetReps,
                formScore = formScore,
                droppedEngine = droppedEngine,
                droppedSupervisor = droppedSupervisor,
            )
            trainingPipelineLog(line)
            resetWindowCounters()
            lastLogMs = nowMs
            return true
        }
    }

    private fun buildPeriodicLine(
        runState: SessionRunState,
        phase: String?,
        repCount: Int,
        targetReps: Int,
        formScore: Int,
        droppedEngine: Int,
        droppedSupervisor: Int,
    ): String {
        val camFps = cameraAccepted
        val camSkip = cameraSkipped
        val poseFps = poseWithBody + poseNoBody
        val avgInference = if (poseInferenceSamples == 0) {
            0
        } else {
            (poseInferenceMsTotal / poseInferenceSamples).toInt()
        }
        val phaseLabel = phase ?: "-"
        return buildString {
            append("window=${LOG_INTERVAL_MS / 1_000}s")
            append(" | cam=${camFps}fps")
            append("(skipThrottle=$camSkip")
            append(" target=$cameraTargetFps")
            append(" ae=$cameraAppliedFps")
            append(" analysis=$cameraAnalysisSize)")
            append(" | pose=${poseFps}fps")
            append("(body=$poseWithBody nopose=$poseNoBody")
            append(" inferMs=$avgInference")
            append(" busySkip=$poseBusySkipped")
            if (poseStallEvents > 0) append(" stalls=$poseStallEvents")
            append(')')
            append(" | vm=in $vmIngress")
            append(" proc=$vmProcessed")
            val backlog = vmIngress - vmProcessed
            if (backlog > 0) append(" backlog=$backlog")
            if (vmConflated > 0) append(" conflated=$vmConflated")
            append(" | engine=state=$runState")
            append(" phase=$phaseLabel")
            append(" reps=$repCount/$targetReps")
            append(" score=$formScore")
            append(" drop=$droppedEngine")
            append(" | supervisor=drop=$droppedSupervisor")
        }
    }

    private fun resetWindowCounters() {
        cameraAccepted = 0
        cameraSkipped = 0
        poseWithBody = 0
        poseNoBody = 0
        poseBusySkipped = 0
        poseInferenceMsTotal = 0L
        poseInferenceSamples = 0
        poseStallEvents = 0
        vmIngress = 0
        vmProcessed = 0
        vmConflated = 0
    }
}

/** Test-only formatter access. */
internal fun formatTrainingPipelinePeriodicForTest(
    cameraAccepted: Int,
    cameraSkipped: Int,
    cameraTargetFps: Int,
    cameraAnalysisSize: String,
    cameraAppliedFps: String,
    poseWithBody: Int,
    poseNoBody: Int,
    avgInferenceMs: Int,
    poseBusySkipped: Int,
    poseStallEvents: Int,
    vmIngress: Int,
    vmProcessed: Int,
    vmConflated: Int,
    runState: SessionRunState,
    phase: String?,
    repCount: Int,
    targetReps: Int,
    formScore: Int,
    droppedEngine: Int,
    droppedSupervisor: Int,
): String {
    val poseFps = poseWithBody + poseNoBody
    val phaseLabel = phase ?: "-"
    return buildString {
        append("window=2s")
        append(" | cam=${cameraAccepted}fps")
        append("(skipThrottle=$cameraSkipped")
        append(" target=$cameraTargetFps")
        append(" ae=$cameraAppliedFps")
        append(" analysis=$cameraAnalysisSize)")
        append(" | pose=${poseFps}fps")
        append("(body=$poseWithBody nopose=$poseNoBody")
        append(" inferMs=$avgInferenceMs")
        append(" busySkip=$poseBusySkipped")
        if (poseStallEvents > 0) append(" stalls=$poseStallEvents")
        append(')')
        append(" | vm=in $vmIngress")
        append(" proc=$vmProcessed")
        if (vmConflated > 0) append(" conflated=$vmConflated")
        append(" | engine=state=$runState")
        append(" phase=$phaseLabel")
        append(" reps=$repCount/$targetReps")
        append(" score=$formScore")
        append(" drop=$droppedEngine")
        append(" | supervisor=drop=$droppedSupervisor")
    }
}
