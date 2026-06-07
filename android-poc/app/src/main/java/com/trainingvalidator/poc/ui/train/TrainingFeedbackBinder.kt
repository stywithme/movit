package com.trainingvalidator.poc.ui.train

import android.graphics.Color
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.ui.components.AnimationUtils
import com.trainingvalidator.poc.ui.components.GlassmorphicMessageView
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackManager
import com.trainingvalidator.poc.training.feedback.FeedbackSeverity
import com.trainingvalidator.poc.training.feedback.JointQualityContent
import com.trainingvalidator.poc.training.feedback.SystemMessageRegistry
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.workout.PauseReason
import com.trainingvalidator.poc.ui.training.SetupPhase
import com.trainingvalidator.poc.ui.training.SetupResult
import com.trainingvalidator.poc.training.workout.WorkoutRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Camera training: audio + vignette feedback (no glassmorphic overlay during live training).
 * Subscribes to [TrainingViewModel.feedbackEvents] and, once [TrainingViewModel.initializeFeedback]
 * has run, to [FeedbackManager.visualMessages] (rebind with [rebindVisualMessageFlow]).
 */
class TrainingFeedbackBinder(
    private val host: TrainingActivity
) {
    private var visualMessagesJob: kotlinx.coroutines.Job? = null

    private val colorCorrect = Color.parseColor("#00E676")
    private val colorWarning = Color.parseColor("#FFC107")
    private val colorDefault = Color.WHITE

    /**
     * Feedback events + pipeline trace long-press; call once from [com.trainingvalidator.poc.ui.train.TrainingActivity.onCreate].
     */
    fun startFeedbackObservers() {
        host.lifecycleScope.launch {
            host.viewModel.feedbackEvents.collectLatest { handleFeedbackEvent(it) }
        }
    }

    fun rebindVisualMessageFlow() {
        visualMessagesJob?.cancel()
        val flow = host.viewModel.feedbackManager?.visualMessages
        if (flow == null) {
            return
        }
        visualMessagesJob = host.lifecycleScope.launch {
            flow.collectLatest { showGlassmorphicMessage(it) }
        }
    }

    /**
     * Dev-only: long-press rep counter to view last engine pipeline events.
     */
    fun registerPipelineTraceShortcut() {
        host.binding.tvRepCount.setOnLongClickListener {
            val lines = host.viewModel.getPipelineTraceSnapshot()
            if (lines.isEmpty()) {
                Toast.makeText(
                    host,
                    "Pipeline trace (empty — start a workout first)",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                AlertDialog.Builder(host)
                    .setTitle("Pipeline trace")
                    .setMessage(lines.takeLast(50).joinToString("\n"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            true
        }
    }

    /**
     * Runs the visibility glassmorphic policy as child jobs of [parentScope] (same cancellation as
     * [com.trainingvalidator.poc.ui.train.TrainingActivity.observeTrainingEngineState] stateInfos job).
     */
    fun collectEngineVisibilityInScope(parentScope: CoroutineScope, engine: TrainingEngine) {
        parentScope.launch {
            engine.isVisibilityPaused.collect { paused ->
                if (paused) {
                    host.binding.vignetteOverlay.showError()
                } else {
                    host.binding.vignetteOverlay.clear()
                }
            }
        }
        parentScope.launch {
            engine.visibilityResumeCountdown.collect { seconds ->
                if (seconds != null && seconds > 0) {
                    // Countdown handled via audio/haptic in camera mode
                }
            }
        }
    }

    fun showGlassmorphicMessage(message: FeedbackManager.VisualMessage) {
        val type = when (message.type) {
            FeedbackManager.MessageType.TIP -> GlassmorphicMessageView.TYPE_TIP
            FeedbackManager.MessageType.WARNING -> GlassmorphicMessageView.TYPE_WARNING
            FeedbackManager.MessageType.ERROR -> GlassmorphicMessageView.TYPE_ERROR
            FeedbackManager.MessageType.MOTIVATION -> GlassmorphicMessageView.TYPE_MOTIVATION
            FeedbackManager.MessageType.INFO -> GlassmorphicMessageView.TYPE_INFO
        }

        when (message.type) {
            FeedbackManager.MessageType.ERROR -> host.binding.vignetteOverlay.showError()
            FeedbackManager.MessageType.WARNING -> host.binding.vignetteOverlay.showWarning()
            else -> {}
        }
    }

    fun handleFeedbackEvent(event: FeedbackEvent) {
        when (event) {
            is FeedbackEvent.RepCompleted -> {
                AnimationUtils.repCompletedPulse(
                    host.binding.tvRepCount,
                    event.isCorrect,
                    colorCorrect,
                    colorWarning,
                    colorDefault
                )
                if (event.isCorrect) {
                    host.frameCaptureManager?.markAsBestRep(event.repNumber)
                }
            }

            is FeedbackEvent.RepIncomplete -> {
                AnimationUtils.shake(host.binding.tvRepCount)
                host.binding.vignetteOverlay.showWarning()
            }

            is FeedbackEvent.JointQuality -> {
                val err = (event.content as? JointQualityContent.Error)?.error
                    ?: return
                val errorKey = "${err.jointCode}:${err.state.name}"
                val currentRep = (host.viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
                val phase = host.viewModel.currentPhase.value
                if (err.state == JointState.DANGER) {
                    host.frameCaptureController.captureDangerFrame(
                        repNumber = currentRep,
                        phase = phase,
                        jointCode = err.jointCode,
                        actualAngle = err.actualAngle
                    )
                } else {
                    host.frameCaptureController.captureErrorFrame(currentRep, phase, errorKey)
                }
            }

            is FeedbackEvent.HoldGraceStarted -> {
                AnimationUtils.shake(host.binding.tvRepCount)
            }

            is FeedbackEvent.HoldFailed -> {
                AnimationUtils.shake(host.binding.tvRepCount, 15f)
                host.binding.tvRepCount.text = "00:00"
                host.binding.tvRepCount.setTextColor(colorDefault)
            }

            is FeedbackEvent.VisibilityWarning -> {
                host.binding.vignetteOverlay.showWarning()
            }

            else -> {}
        }
    }

    fun handleAutoPaused(reason: PauseReason) {
        val lang = host.viewModel.poseSetupGuide.language
        val lt = when (reason) {
            PauseReason.VISIBILITY -> SystemMessageRegistry.get(
                "training_pause_visibility_joints",
                "المفاصل المطلوبة غير مرئية",
                "Required joints not visible"
            )
            PauseReason.NO_POSE -> SystemMessageRegistry.get(
                "training_pause_no_pose_long",
                "لم يتم اكتشاف وضعية لفترة طويلة",
                "No pose detected for too long"
            )
            PauseReason.MANUAL -> SystemMessageRegistry.get(
                "training_pause_manual",
                "تم إيقاف التمرين مؤقتاً",
                "Training paused"
            )
        }
        val message = lt.get(lang)

        if (reason != PauseReason.MANUAL) {
            host.binding.vignetteOverlay.showError()
        }

        if (reason != PauseReason.MANUAL) {
            host.viewModel.feedbackManager?.speakSystemCue(
                messageKey = "auto_pause_${reason.name.lowercase()}",
                localizedText = lt,
                severity = FeedbackSeverity.CRITICAL
            )
        }
        Log.d(TrainingActivity.TAG, "Auto-paused: $reason")
    }

    fun handleNoPoseWarning(elapsedMs: Long) {
        if (host.isTextlessSetupState()) {
            host.binding.glassmorphicMessage.clearAll()
            host.binding.vignetteOverlay.showWarning()
            return
        }

        host.binding.vignetteOverlay.showWarning()

        if (!host.noPoseTtsSpoken) {
            host.noPoseTtsSpoken = true
            val lt = SystemMessageRegistry.get(
                "training_no_pose_return_camera",
                "عد إلى الكاميرا",
                "Return to the camera"
            )
            host.viewModel.feedbackManager?.speakSystemCue(
                messageKey = "no_pose_return_camera",
                localizedText = lt,
                severity = FeedbackSeverity.WARNING
            )
        }
    }

    fun showCountdownPoseIssue(result: SetupResult) {
        host.binding.glassmorphicMessage.clearAll()
        host.binding.vignetteOverlay.showWarning()
    }
}
