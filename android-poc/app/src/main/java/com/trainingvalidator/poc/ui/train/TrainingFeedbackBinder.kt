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
import com.trainingvalidator.poc.training.session.PauseReason
import com.trainingvalidator.poc.ui.training.SetupPhase
import com.trainingvalidator.poc.ui.training.SetupResult
import com.trainingvalidator.poc.training.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Cameras: audio + vignette, no training glassmorphic text. Video: glassmorphic + vignette.
 * Subscribes to [TrainingViewModel.feedbackEvents] and, once [TrainingViewModel.initializeFeedback]
 * has run, to [FeedbackManager.visualMessages] (rebind with [rebindVisualMessageFlow]).
 */
class TrainingFeedbackBinder(
    private val host: TrainingActivity
) {
    private val isVideoMode: Boolean
        get() = host.isVideoMode

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
                    "Pipeline trace (empty — start a session first)",
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
                    if (isVideoMode) {
                        host.binding.glassmorphicMessage.showMessage(
                            "Return to frame to continue",
                            GlassmorphicMessageView.TYPE_ERROR,
                            durationMs = -1
                        )
                    }
                } else {
                    if (isVideoMode) host.binding.glassmorphicMessage.clearAll()
                    host.binding.vignetteOverlay.clear()
                }
            }
        }
        parentScope.launch {
            engine.visibilityResumeCountdown.collect { seconds ->
                if (seconds != null && seconds > 0) {
                    if (isVideoMode) {
                        host.binding.glassmorphicMessage.showMessage(
                            "Resuming in $seconds...",
                            GlassmorphicMessageView.TYPE_INFO,
                            durationMs = 900
                        )
                    }
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

        if (!isVideoMode) {
            when (message.type) {
                FeedbackManager.MessageType.ERROR -> host.binding.vignetteOverlay.showError()
                FeedbackManager.MessageType.WARNING -> host.binding.vignetteOverlay.showWarning()
                else -> {}
            }
            return
        }

        host.binding.glassmorphicMessage.showMessage(message.text, type, message.durationMs)

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

        if (!isVideoMode && reason != PauseReason.MANUAL) {
            host.viewModel.feedbackManager?.speakSystemCue(
                messageKey = "auto_pause_${reason.name.lowercase()}",
                localizedText = lt,
                severity = FeedbackSeverity.CRITICAL
            )
        } else {
            host.binding.glassmorphicMessage.showMessage(
                message,
                if (reason == PauseReason.MANUAL) GlassmorphicMessageView.TYPE_INFO
                else GlassmorphicMessageView.TYPE_ERROR,
                durationMs = -1
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

        if (!isVideoMode) {
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
        } else {
            val remainingSeconds = ((4000 - elapsedMs) / 1000).toInt().coerceAtLeast(0)
            val base = SystemMessageRegistry.get(
                "training_no_pose_countdown_warning",
                "لم يتم اكتشاف وضعية! العودة خلال {seconds}ث...",
                "No pose detected! Return in {seconds}s..."
            )
            val message = SystemMessageRegistry.substitute(
                base,
                mapOf("seconds" to remainingSeconds.toString())
            ).get(host.viewModel.poseSetupGuide.language)
            host.binding.glassmorphicMessage.showMessage(
                message,
                GlassmorphicMessageView.TYPE_WARNING,
                durationMs = 500
            )
        }
    }

    fun showCountdownPoseIssue(result: SetupResult) {
        if (!isVideoMode) {
            host.binding.glassmorphicMessage.clearAll()
            host.binding.vignetteOverlay.showWarning()
            return
        }
        if (host.viewModel.supervisor.state.value == SessionState.SETUP_POSE ||
            host.viewModel.supervisor.state.value == SessionState.RESUME_SETUP
        ) {
            host.binding.glassmorphicMessage.clearAll()
            host.binding.vignetteOverlay.showWarning()
            return
        }

        val lang = host.viewModel.poseSetupGuide.language
        val message = when {
            result.phase != SetupPhase.ANGLES -> result.phaseMessage?.get(lang)
            result.worstJoint != null -> result.worstJoint.message.get(lang)
            else -> SystemMessageRegistry.get(
                "training_setup_return_start_pose",
                "عد إلى وضعية البداية",
                "Return to the start pose"
            ).get(lang)
        } ?: return

        host.binding.glassmorphicMessage.showMessage(
            message,
            GlassmorphicMessageView.TYPE_WARNING,
            1000
        )
        host.binding.vignetteOverlay.showWarning()
    }
}
