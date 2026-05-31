package com.trainingvalidator.poc.ui.train

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.report.BestWorstReplayPipeline
import com.trainingvalidator.poc.training.report.FrameCaptureManager
import com.trainingvalidator.poc.training.session.SessionState

/**
 * [FrameCaptureManager] and [ReportStorage] init, rep-wide replay sampling, peak / error / danger
 * frame capture from the live camera preview.
 */
class TrainingFrameCaptureController(
    private val host: TrainingActivity
) {
    companion object {
        private const val REPLAY_SAMPLE_INTERVAL_MS = 180L
    }

    private var lastCapturedPhase: Phase? = null

    private val repWideReplayHandler = Handler(Looper.getMainLooper())
    private var repWideReplayScheduled = false

    private val repWideReplayRunnable = object : Runnable {
        override fun run() {
            if (!repWideReplayScheduled) return
            if (host.viewModel.supervisor.state.value != SessionState.TRAINING || host.viewModel.isHoldExercise()) {
                repWideReplayScheduled = false
                return
            }
            val manager = host.frameCaptureManager
            val repNumber = (host.viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
            if (manager != null && repNumber >= 1) {
                getBitmapForCapture()?.let { bitmap ->
                    manager.captureReplayFrame(bitmap = bitmap, repNumber = repNumber)
                }
            }
            if (repWideReplayScheduled) {
                repWideReplayHandler.postDelayed(this, REPLAY_SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun initializeReportSystem() {
        lastCapturedPhase = null
        val id = java.util.UUID.randomUUID().toString()
        host.frameCaptureManager = FrameCaptureManager(host, id)
        host.frameCaptureManager?.cleanupOldSessions(5)
        host.reportStorage = ReportStorage(host)
    }

    fun resetForNextExercise() {
        val newId = java.util.UUID.randomUUID().toString()
        Log.d(TrainingActivity.TAG, "Resetting FrameCaptureManager for next exercise: sessionId=$newId")
        lastCapturedPhase = null
        host.frameCaptureManager = FrameCaptureManager(host, newId)
    }

    fun onCurrentPhaseForPeakCapture(phase: Phase) {
        if (phase == lastCapturedPhase) return
        if (phase == Phase.BOTTOM) {
            capturePeakFrame(phase)
        }
        lastCapturedPhase = phase
    }

    fun startRepWideReplaySampler() {
        if (host.viewModel.isHoldExercise()) return
        if (repWideReplayScheduled) return
        repWideReplayScheduled = true
        repWideReplayHandler.removeCallbacks(repWideReplayRunnable)
        repWideReplayHandler.post(repWideReplayRunnable)
        Log.d(BestWorstReplayPipeline.LOG_TAG, "rep_wide_sampler started")
    }

    fun stopRepWideReplaySampler() {
        val wasRunning = repWideReplayScheduled
        repWideReplayScheduled = false
        repWideReplayHandler.removeCallbacks(repWideReplayRunnable)
        if (wasRunning) {
            Log.d(BestWorstReplayPipeline.LOG_TAG, "rep_wide_sampler stopped")
        }
    }

    fun onDestroy() {
        stopRepWideReplaySampler()
    }

    fun capturePeakFrame(phase: Phase) {
        if (host.viewModel.supervisor.state.value != SessionState.TRAINING) {
            return
        }
        getBitmapForCapture()?.let { bmp ->
            val currentRep = (host.viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
            val angles = host.viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            host.frameCaptureManager?.capturePeakFrame(
                bitmap = bmp,
                repNumber = currentRep,
                phase = phase,
                angles = angles
            )
            Log.d(TrainingActivity.TAG, "Captured peak frame for rep $currentRep at phase ${phase.name}")
        }
    }

    fun captureErrorFrame(repNumber: Int, phase: Phase, errorKey: String) {
        if (host.viewModel.supervisor.state.value != SessionState.TRAINING) {
            return
        }
        getBitmapForCapture()?.let { bmp ->
            val angles = host.viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            val captured = host.frameCaptureManager?.captureErrorFrame(
                bitmap = bmp,
                repNumber = repNumber,
                phase = phase,
                errorKey = errorKey,
                angles = angles
            )
            if (captured != null) {
                Log.d(TrainingActivity.TAG, "Captured error frame for $errorKey at rep $repNumber")
            }
        }
    }

    fun captureDangerFrame(repNumber: Int, phase: Phase, jointCode: String, actualAngle: Double) {
        if (host.viewModel.supervisor.state.value != SessionState.TRAINING) {
            return
        }
        getBitmapForCapture()?.let { bmp ->
            val angles = host.viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            val captured = host.frameCaptureManager?.captureDangerFrame(
                bitmap = bmp,
                repNumber = repNumber,
                phase = phase,
                jointCode = jointCode,
                actualAngle = actualAngle,
                angles = angles
            )
            if (captured != null) {
                Log.d(TrainingActivity.TAG, "Captured DANGER frame for $jointCode at ${actualAngle.toInt()}° (rep $repNumber)")
            }
        }
    }

    fun getBitmapForCapture(): android.graphics.Bitmap? = host.binding.previewView.bitmap
}
