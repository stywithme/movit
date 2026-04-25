package com.trainingvalidator.poc.ui.train

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.ui.components.AnimationUtils
import com.trainingvalidator.poc.ui.training.AxisStatus
import com.trainingvalidator.poc.ui.training.CountdownController
import com.trainingvalidator.poc.ui.training.GuidanceLevel
import com.trainingvalidator.poc.ui.training.JointGuidance
import com.trainingvalidator.poc.ui.training.SetupPhase
import com.trainingvalidator.poc.ui.training.SetupResult
import com.trainingvalidator.poc.training.engine.BodyPosture
import com.trainingvalidator.poc.training.engine.ExpectedDirection
import com.trainingvalidator.poc.training.engine.PoseSceneExpectation
import com.trainingvalidator.poc.training.engine.VisibleRegion
import com.trainingvalidator.poc.training.feedback.SystemMessageRegistry
import com.trainingvalidator.poc.training.models.PoseVariant
import com.trainingvalidator.poc.training.session.SessionState
import com.trainingvalidator.poc.ui.training.CameraGuidance
import com.trainingvalidator.poc.ui.training.Direction
import com.trainingvalidator.poc.ui.training.TrainingViewModel
import android.util.Log

/**
 * Pre-training setup pose, floating indicator / axis / reference image, and [CountdownController] UI.
 */
class SetupCountdownBinder(
    private val host: TrainingActivity
) {
    private var lastSetupPhase: SetupPhase? = null

    private val binding
        get() = host.binding
    private val viewModel: TrainingViewModel
        get() = host.viewModel
    private val useFrontCamera: Boolean
        get() = host.useFrontCamera
    private val context: Context
        get() = host

    private val AXIS_COLOR_PENDING = Color.parseColor("#66FFFFFF")
    private val AXIS_COLOR_FAILED = Color.parseColor("#FFA726")
    private val AXIS_COLOR_PASSED = Color.parseColor("#4CAF50")

    fun installCountdownListener() {
        viewModel.countdownController.setListener(object : CountdownController.CountdownListener {
            override fun onTick(secondsRemaining: Int) {
                AnimationUtils.animateCountdown(binding.tvCountdown, secondsRemaining.toString())
            }

            override fun onFinish() {
                viewModel.onCountdownFinished()
                val goOverlay = SystemMessageRegistry.get("training_go_overlay", "انطلق!", "GO!")
                    .get(viewModel.poseSetupGuide.language)
                AnimationUtils.animateGoText(binding.tvCountdown, goOverlay) { }
            }

            override fun onCancelled() {}

            override fun onFrozen() {
                binding.tvCountdown.alpha = 0.45f
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(host, R.color.warning)
                )
            }

            override fun onUnfrozen() {
                binding.tvCountdown.alpha = 1f
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(host, R.color.text_primary)
                )
                binding.vignetteOverlay.clear()
            }
        })
    }

    fun isTextlessSetupState(): Boolean {
        return when (viewModel.supervisor.state.value) {
            SessionState.SETUP_POSE,
            SessionState.RESUME_SETUP,
            SessionState.COUNTDOWN,
            SessionState.RESUME_COUNTDOWN -> true
            else -> false
        }
    }

    fun showSetupPoseUI() {
        binding.setupPosePanel.visibility = View.GONE
        binding.bottomStatsBar.visibility = View.GONE
        binding.countdownPanel.visibility = View.GONE
        binding.glassmorphicMessage.clearAll()
        binding.vignetteOverlay.clear()
        binding.setupIndicatorBar.visibility = View.VISIBLE
        bindSetupIndicatorExpectations()
        resetAxisIcons()
        loadSetupReferenceImage()
        binding.skeletonOverlay.setSceneCheckMode(true)
        binding.skeletonOverlay.updateFrontCameraState(useFrontCamera)
        lastSetupPhase = null
    }

    fun updateSetupGuidanceUI(result: SetupResult) {
        val state = viewModel.supervisor.state.value
        if (state != SessionState.SETUP_POSE && state != SessionState.RESUME_SETUP) return
        val phase = result.phase
        updateAxisIcons(result)
        val previousPhase = lastSetupPhase
        lastSetupPhase = phase

        if (phase == SetupPhase.ANGLES && previousPhase != null && previousPhase != SetupPhase.ANGLES) {
            binding.skeletonOverlay.setSceneCheckMode(false)
            val transitionMsg = com.trainingvalidator.poc.training.feedback.MobileMessageResolver
                .resolveSetupSceneToVisibility()
            viewModel.feedbackManager?.speakSetupPhaseGuidance(transitionMsg)
        }

        if (phase != SetupPhase.ANGLES) {
            binding.skeletonOverlay.setSceneCheckMode(true)
            if (result.phaseMessage != null &&
                viewModel.poseSetupGuide.shouldSpeakPhaseGuidance(phase)
            ) {
                viewModel.feedbackManager?.speakSetupPhaseGuidance(result.phaseMessage)
                viewModel.poseSetupGuide.onPhaseGuidanceSpoken(phase)
            }
        } else {
            val landmarks = viewModel.lastSmoothedLandmarks
            val imageSize = viewModel.lastImageSize
            if (landmarks != null) {
                binding.skeletonOverlay.updateSetupGuidance(
                    guidances = result.joints,
                    smoothedLandmarks = landmarks,
                    imageW = imageSize.first,
                    imageH = imageSize.second,
                    useFrontCamera = useFrontCamera
                )
            }
            val missingJoint = result.joints.firstOrNull { it.level == GuidanceLevel.RED }
            if (missingJoint != null && viewModel.poseSetupGuide.shouldSpeakGuidance(missingJoint)) {
                viewModel.feedbackManager?.speakSetupGuidance(missingJoint)
                viewModel.poseSetupGuide.onVoiceGuidanceSpoken(missingJoint)
            }
        }
    }

    fun updateJointGuidanceRows(joints: List<JointGuidance>) {
        val container = binding.jointGuidanceContainer
        joints.forEachIndexed { i, guidance ->
            val row = container.findViewWithTag<android.widget.TextView>("joint_row_$i")
                ?: android.widget.TextView(context).also { tv ->
                    tv.tag = "joint_row_$i"
                    tv.textSize = 16f
                    tv.setPadding(0, 8, 0, 8)
                    tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    container.addView(tv)
                }
            row.visibility = View.VISIBLE
            val colorRes = when (guidance.level) {
                GuidanceLevel.GREEN -> R.color.success
                GuidanceLevel.YELLOW -> R.color.warning
                GuidanceLevel.RED -> R.color.error
            }
            val icon = when (guidance.level) {
                GuidanceLevel.GREEN -> "✓"
                GuidanceLevel.YELLOW -> "⚠"
                GuidanceLevel.RED -> "✗"
            }
            val arrow = when (guidance.direction) {
                Direction.LOWER -> " ↓"
                Direction.RAISE -> " ↑"
                else -> ""
            }
            val angleStr = "%.0f°".format(guidance.currentAngle)
            row.text = "$icon  ${guidance.jointName}  $angleStr$arrow"
            row.setTextColor(ContextCompat.getColor(context, colorRes))
        }
        for (i in joints.size until container.childCount) {
            container.getChildAt(i).visibility = View.GONE
        }
    }

    fun updateViewCard(cameraGuidance: CameraGuidance?) {
        if (cameraGuidance == null) return
        val lang = viewModel.poseSetupGuide.language
        val (text, colorRes) = if (cameraGuidance.isCorrect) {
            (if (lang == "ar") "✓" else "✓ OK") to R.color.success
        } else {
            val label = cameraGuidance.tip?.get(lang) ?: "—"
            label to R.color.warning
        }
        binding.tvFormStatus.text = text
        binding.tvFormStatus.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    fun switchBottomBarToSetupMode() {
        val isAr = viewModel.poseSetupGuide.language == "ar"
        binding.tvTimeCardLabel.text = if (isAr) "جاهز" else "READY"
        binding.tvTimeElapsed.text = "0%"
        binding.tvTimeElapsed.visibility = View.VISIBLE
        binding.progressSetupReadyBar.visibility = View.VISIBLE
        binding.progressSetupReadyBar.progress = 0
        binding.tvFormCardLabel.text = if (isAr) "الوضعية" else "VIEW"
    }

    fun updateReadyPercent(percent: Int) {
        binding.tvTimeElapsed.text = "$percent%"
        binding.progressSetupReadyBar.progress = percent
    }

    fun switchBottomBarToFormMode() {
        binding.tvTimeCardLabel.text = context.getString(R.string.time)
        binding.tvFormCardLabel.text = context.getString(R.string.form)
        binding.progressSetupReadyBar.visibility = View.GONE
        binding.tvTimeElapsed.text = "00:00"
        binding.tvTimeElapsed.visibility = View.VISIBLE
        binding.tvFormStatus.text = context.getString(R.string.good)
        binding.tvFormStatus.setTextColor(ContextCompat.getColor(context, R.color.primary))
    }

    private fun resetAxisIcons() {
        updateAxisRow(binding.ivAxisRegion, binding.tvAxisRegionExpected, AxisStatus.PENDING, R.drawable.ic_viewfinder)
        updateAxisRow(binding.ivAxisPosture, binding.tvAxisPostureExpected, AxisStatus.PENDING, R.drawable.ic_person_placeholder)
        updateAxisRow(binding.ivAxisDirection, binding.tvAxisDirectionExpected, AxisStatus.PENDING, R.drawable.ic_eye)
    }

    private fun updateAxisIcons(result: SetupResult) {
        updateAxisRow(binding.ivAxisRegion, binding.tvAxisRegionExpected, result.regionStatus, R.drawable.ic_viewfinder)
        updateAxisRow(binding.ivAxisPosture, binding.tvAxisPostureExpected, result.postureStatus, R.drawable.ic_person_placeholder)
        updateAxisRow(binding.ivAxisDirection, binding.tvAxisDirectionExpected, result.directionStatus, R.drawable.ic_eye)
    }

    private fun axisColor(status: AxisStatus): Int = when (status) {
        AxisStatus.PENDING -> AXIS_COLOR_PENDING
        AxisStatus.FAILED -> AXIS_COLOR_FAILED
        AxisStatus.PASSED -> AXIS_COLOR_PASSED
    }

    private fun updateAxisRow(iconView: ImageView, labelView: TextView, status: AxisStatus, defaultIconRes: Int) {
        val color = axisColor(status)
        when (status) {
            AxisStatus.PENDING -> {
                iconView.setImageResource(defaultIconRes)
                labelView.alpha = 0.5f
            }
            AxisStatus.FAILED -> {
                iconView.setImageResource(R.drawable.ic_warning)
                labelView.alpha = 1f
            }
            AxisStatus.PASSED -> {
                iconView.setImageResource(R.drawable.ic_check)
                labelView.alpha = 1f
            }
        }
        iconView.setColorFilter(color)
        labelView.setTextColor(color)
    }

    private fun currentPoseVariant(): PoseVariant? {
        val config = viewModel.exerciseConfig.value ?: return null
        return config.getPoseVariant(viewModel.poseVariantIndex.value)
    }

    private fun currentSetupExpectation(): PoseSceneExpectation? {
        val variant = currentPoseVariant() ?: return null
        return if (variant.expectedPostures != null) {
            PoseSceneExpectation.fromJson(
                postures = variant.expectedPostures,
                directions = variant.expectedDirections,
                regions = variant.expectedRegions
            )
        } else {
            val posCode = variant.posePosition ?: variant.cameraPosition ?: "standing_side"
            PoseSceneExpectation.fromLegacyCode(posCode)
        }
    }

    private fun bindSetupIndicatorExpectations() {
        val expectation = currentSetupExpectation()
        if (expectation == null) {
            binding.tvSetupRefTitle.text = "Target pose"
            binding.tvSetupRefSubtitle.text = "Reference"
            binding.tvAxisRegionExpected.text = "Show your body in frame"
            binding.tvAxisPostureExpected.text = "Get into position"
            binding.tvAxisDirectionExpected.text = "Adjust camera angle"
            return
        }
        val postureText = expectation.postures
            .ifEmpty { listOf(BodyPosture.UNKNOWN) }
            .joinToString(" / ") { postureLabel(it) }
        val regionText = expectation.regions
            .ifEmpty { listOf(VisibleRegion.UNKNOWN) }
            .joinToString(" / ") { regionLabel(it) }
        val directionText = expectation.directions
            .ifEmpty { listOf(ExpectedDirection.ANY) }
            .joinToString(" / ") { directionLabel(it) }
        binding.tvSetupRefTitle.text = postureText
        binding.tvSetupRefSubtitle.text = directionText
        binding.tvAxisRegionExpected.text = regionText
        binding.tvAxisPostureExpected.text = postureText
        binding.tvAxisDirectionExpected.text = directionText
    }

    private fun postureLabel(posture: BodyPosture): String = when (posture) {
        BodyPosture.STANDING -> "Stand upright"
        BodyPosture.LYING_PRONE -> "Lie face down"
        BodyPosture.LYING_SUPINE -> "Lie on your back"
        BodyPosture.LYING_SIDE -> "Lie on your side"
        BodyPosture.SITTING -> "Sit down"
        BodyPosture.UNKNOWN -> "Get into position"
    }

    private fun regionLabel(region: VisibleRegion): String = when (region) {
        VisibleRegion.FULL_BODY -> "Show full body"
        VisibleRegion.UPPER_BODY -> "Show upper body"
        VisibleRegion.LOWER_BODY -> "Show lower body"
        VisibleRegion.UNKNOWN -> "Show your body in frame"
    }

    private fun directionLabel(direction: ExpectedDirection): String = when (direction) {
        ExpectedDirection.FRONT -> "Face the camera"
        ExpectedDirection.BACK -> "Turn your back to camera"
        ExpectedDirection.SIDE_ANY -> "Stand sideways to camera"
        ExpectedDirection.SIDE_LEFT -> "Show your left side"
        ExpectedDirection.SIDE_RIGHT -> "Show your right side"
        ExpectedDirection.DIAGONAL -> "Stand at an angle"
        ExpectedDirection.ANY -> "Any camera angle"
    }

    private fun loadSetupReferenceImage() {
        val variant = currentPoseVariant()
        if (variant == null) {
            Log.d(TrainingActivity.TAG, "SetupRefImage: no variant loaded — showing fallback")
            binding.ivSetupRefImage.setImageDrawable(null)
            binding.setupRefFallbackContainer.visibility = View.VISIBLE
            return
        }
        val imageUrl = variant.positionImageUrl
        if (!imageUrl.isNullOrBlank()) {
            ImageLoader(host).enqueue(
                ImageRequest.Builder(host)
                    .data(imageUrl)
                    .target(
                        onSuccess = { result ->
                            binding.ivSetupRefImage.setImageDrawable(result)
                            binding.setupRefFallbackContainer.visibility = View.GONE
                        },
                        onError = {
                            binding.ivSetupRefImage.setImageDrawable(null)
                            binding.setupRefFallbackContainer.visibility = View.VISIBLE
                        }
                    )
                    .crossfade(true)
                    .build()
            )
        } else {
            binding.ivSetupRefImage.setImageDrawable(null)
            binding.setupRefFallbackContainer.visibility = View.VISIBLE
        }
    }
}
