package com.trainingvalidator.poc.ui.train

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.report.BestWorstReplayPipeline
import com.trainingvalidator.poc.training.report.FrameCaptureManager
import com.trainingvalidator.poc.training.workout.WorkoutRunState

/**
 * [FrameCaptureManager] and [ReportStorage] init, rep-wide replay sampling, peak / error / danger
 * frame capture, and [getBitmapForCapture] for both camera and video.
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
            if (host.viewModel.supervisor.state.value != WorkoutRunState.TRAINING || host.viewModel.isHoldExercise()) {
                repWideReplayScheduled = false
                return
            }
            val manager = host.frameCaptureManager
            val repNumber = (host.viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
            if (manager != null && repNumber >= 1) {
                val bitmap = getBitmapForCapture()
                if (bitmap != null) {
                    manager.captureReplayFrame(bitmap = bitmap, repNumber = repNumber)
                    if (host.isVideoMode) bitmap.recycle()
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
        host.frameCaptureManager?.cleanupOldWorkouts(5)
        host.reportStorage = ReportStorage(host)
    }

    /**
     * New [FrameCaptureManager] for the next session exercise; clears peak-phase de-dupe state.
     */
    fun resetForNextExercise() {
        val newId = java.util.UUID.randomUUID().toString()
        Log.d(TrainingActivity.TAG, "Resetting FrameCaptureManager for next exercise: workoutId=$newId")
        lastCapturedPhase = null
        host.frameCaptureManager = FrameCaptureManager(host, newId)
    }

    /**
     * BOTTOM peak capture on phase change (matches prior [TrainingActivity] observer behaviour).
     */
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

    /**
     * Capture peak frame when reaching BOTTOM phase.
     * Uses [com.trainingvalidator.poc.training.TrainingEngine.getCurrentRep] + 1 as repNumber so
     * it matches [com.trainingvalidator.poc.training.feedback.FeedbackEvent.RepCompleted.repNumber]
     * for the rep in progress.
     */
    fun capturePeakFrame(phase: Phase) {
        if (host.viewModel.supervisor.state.value != WorkoutRunState.TRAINING) {
            return
        }
        val bitmap = getBitmapForCapture()
        bitmap?.let { bmp ->
            val currentRep = (host.viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
            val angles = host.viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            host.frameCaptureManager?.capturePeakFrame(
                bitmap = bmp,
                repNumber = currentRep,
                phase = phase,
                angles = angles
            )
            Log.d(TrainingActivity.TAG, "Captured peak frame for rep $currentRep at phase ${phase.name}")
            if (host.isVideoMode) {
                bmp.recycle()
            }
        }
    }

    fun captureErrorFrame(repNumber: Int, phase: Phase, errorKey: String) {
        if (host.viewModel.supervisor.state.value != WorkoutRunState.TRAINING) {
            return
        }
        val bitmap = getBitmapForCapture()
        bitmap?.let { bmp ->
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
            if (host.isVideoMode) {
                bmp.recycle()
            }
        }
    }

    fun captureDangerFrame(repNumber: Int, phase: Phase, jointCode: String, actualAngle: Double) {
        if (host.viewModel.supervisor.state.value != WorkoutRunState.TRAINING) {
            return
        }
        val bitmap = getBitmapForCapture()
        bitmap?.let { bmp ->
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
                Log.d(TrainingActivity.TAG, "🚨 Captured DANGER frame for $jointCode at ${actualAngle.toInt()}° (rep $repNumber)")
            }
            if (host.isVideoMode) {
                bmp.recycle()
            }
        }
    }

    fun getBitmapForCapture(): android.graphics.Bitmap? {
        return if (host.isVideoMode) {
            host.videoModeController?.getCurrentFrameBitmap()
        } else {
            host.binding.previewView.bitmap
        }
    }
}
