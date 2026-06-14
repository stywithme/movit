package com.movit.core.training.session

import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.engine.StartPoseGate
import com.movit.core.training.geometry.LandmarkTiltCorrector
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.position.PositionMessageResolver
import com.movit.core.training.position.PoseSceneDetector
import com.movit.core.training.position.resolveSceneExpectation

/**
 * I-15: merged setup validation (legacy PoseSetupGuide + [StartPoseGate] rolling window).
 */
class SetupReadinessGate(
    private val config: SetupValidationConfig = SetupValidationConfig(),
    private val tiltSource: DeviceTiltPort? = null,
) {
    private val jointWindow = RollingWindow(config.windowSize, config.requiredValid)
    private val sceneDetector = PoseSceneDetector(
        windowSize = config.cameraCheckWindowSize,
        requiredMajority = config.cameraCheckRequired,
    )
    private var startPoseGate: StartPoseGate? = null

    fun reset() {
        jointWindow.reset()
        sceneDetector.reset()
        startPoseGate = null
    }

    fun validate(
        angles: JointAngles?,
        landmarks: List<Landmark>?,
        exerciseConfig: ExerciseConfig,
        poseVariantIndex: Int,
        isFrontCamera: Boolean = false,
    ): SetupReadinessResult {
        if (angles == null) {
            jointWindow.add(false)
            return SetupReadinessResult.empty()
        }
        val variant = exerciseConfig.poseVariants.getOrNull(poseVariantIndex)
            ?: return SetupReadinessResult.empty().also { jointWindow.add(false) }

        if (startPoseGate == null) {
            startPoseGate = StartPoseGate(variant.trackedJoints)
        }

        val expectation = variant.resolveSceneExpectation()
        val sceneLandmarks = landmarks
            ?.takeIf { it.size >= 33 }
            ?.let { getTiltCorrectedLandmarks(it) }
        val scene = sceneLandmarks?.let { sceneDetector.detect(it, isFrontCamera) }
        val axisMatch = scene?.let { expectation.matchesScene(it) }

        val phase = when {
            axisMatch == null || !axisMatch.regionMatch -> SetupPhase.REGION
            !axisMatch.postureMatch -> SetupPhase.POSTURE
            !axisMatch.directionMatch -> SetupPhase.DIRECTION
            else -> SetupPhase.ANGLES
        }

        val inStartPose = startPoseGate?.isInStartPose(angles.toMap()) == true
        val primaryReady = phase == SetupPhase.ANGLES && inStartPose

        jointWindow.add(primaryReady)

        val progressPercent = when (phase) {
            SetupPhase.REGION -> 0
            SetupPhase.POSTURE -> 25
            SetupPhase.DIRECTION -> 50
            SetupPhase.ANGLES -> (50 + jointWindow.validRatio() * 50).toInt().coerceIn(50, 100)
        }

        val phaseMessage = when (phase) {
            SetupPhase.REGION -> PositionMessageResolver.resolveRegionAxisWarning(expectation.regions)
            SetupPhase.POSTURE -> PositionMessageResolver.resolvePostureAxisWarning(expectation.postures)
            SetupPhase.DIRECTION -> PositionMessageResolver.resolveDirectionAxisWarning(expectation.directions)
            SetupPhase.ANGLES -> null
        }

        val angleMap = angles.toMap()
        val jointGuidanceRows = if (phase == SetupPhase.ANGLES) {
            SetupJointGuidanceResolver.resolveAllJoints(
                angles = angleMap,
                joints = variant.trackedJoints,
                closeThresholdDegrees = config.closeThresholdDegrees,
            )
        } else {
            emptyList()
        }
        val worstJoint = jointGuidanceRows.firstOrNull()
            ?: if (phase == SetupPhase.ANGLES) {
                SetupJointGuidanceResolver.resolveWorstJoint(
                    angles = angleMap,
                    joints = variant.trackedJoints,
                    closeThresholdDegrees = config.closeThresholdDegrees,
                )
            } else {
                null
            }
        val cameraTip = SetupJointGuidanceResolver.resolveCameraTip(
            phase = phase,
            cameraTipEnabled = config.cameraTipEnabled,
            regions = expectation.regions,
        )
        val axisStatuses = resolveSetupAxisStatuses(phase, axisMatch)

        return SetupReadinessResult(
            phase = phase,
            progressPercent = progressPercent,
            isConfirmed = jointWindow.isConfirmed(),
            axisMatch = axisMatch,
            phaseMessage = phaseMessage,
            worstJointGuidance = worstJoint,
            cameraTip = cameraTip,
            inStartPose = inStartPose,
            axisStatuses = axisStatuses,
            jointGuidanceRows = jointGuidanceRows,
            referenceImageUrl = variant.positionImageUrl,
        )
    }

    /** Strict setup confirmation — exact [TrackedJoint.startPose] box for rolling window. */
    fun isSetupPoseConfirmed(
        angles: JointAngles?,
        exerciseConfig: ExerciseConfig,
        poseVariantIndex: Int,
    ): Boolean {
        if (angles == null) return false
        val variant = exerciseConfig.poseVariants.getOrNull(poseVariantIndex) ?: return false
        val gate = startPoseGate ?: StartPoseGate(variant.trackedJoints).also { startPoseGate = it }
        return gate.isInStartPose(angles.toMap())
    }

    /**
     * Countdown guard — rough start pose (Legacy `isStartPoseRoughlyValid`), separate from
     * strict [isSetupPoseConfirmed] used during SETUP_POSE confirmation.
     */
    fun isCountdownPoseValid(
        angles: JointAngles?,
        exerciseConfig: ExerciseConfig,
        poseVariantIndex: Int,
    ): Boolean {
        if (angles == null) return false
        val variant = exerciseConfig.poseVariants.getOrNull(poseVariantIndex) ?: return false
        val gate = startPoseGate ?: StartPoseGate(variant.trackedJoints).also { startPoseGate = it }
        return gate.isStartPoseRoughlyValid(
            currentAngles = angles.toMap(),
            toleranceDegrees = config.resolvedCountdownAngleToleranceDegrees(),
            minJointPresenceRatio = config.countdownMinJointPresenceRatio,
            requireAllPrimaryPresent = config.countdownRequireAllPrimaryPresent,
        )
    }

    private fun getTiltCorrectedLandmarks(landmarks: List<Landmark>): List<Landmark> {
        val source = tiltSource ?: return landmarks
        if (!source.isAvailable) return landmarks
        val correction = source.correctionRadians
        if (correction == 0f || !correction.isFinite()) return landmarks
        return LandmarkTiltCorrector.correct(landmarks, correction)
    }
}

enum class SetupPhase {
    REGION,
    POSTURE,
    DIRECTION,
    ANGLES,
}

data class SetupReadinessResult(
    val phase: SetupPhase,
    val progressPercent: Int,
    val isConfirmed: Boolean,
    val axisMatch: com.movit.core.training.position.AxisMatchResult?,
    val phaseMessage: LocalizedText? = null,
    val worstJointGuidance: JointSetupGuidance? = null,
    val cameraTip: LocalizedText? = null,
    val inStartPose: Boolean = false,
    val axisStatuses: SetupAxisStatuses = SetupAxisStatuses(
        region = SetupAxisStatus.PENDING,
        posture = SetupAxisStatus.PENDING,
        direction = SetupAxisStatus.PENDING,
    ),
    val jointGuidanceRows: List<JointSetupGuidance> = emptyList(),
    val referenceImageUrl: String? = null,
) {
    companion object {
        fun empty() = SetupReadinessResult(
            phase = SetupPhase.REGION,
            progressPercent = 0,
            isConfirmed = false,
            axisMatch = null,
        )
    }
}

class RollingWindow(private val size: Int, private val required: Int) {
    private val frames = ArrayDeque<Boolean>(size)

    fun add(valid: Boolean) {
        if (frames.size >= size) frames.removeFirst()
        frames.addLast(valid)
    }

    fun isConfirmed(): Boolean = frames.size >= size && frames.count { it } >= required

    fun validRatio(): Float =
        if (frames.isEmpty()) 0f else frames.count { it }.toFloat() / frames.size.toFloat()

    fun reset() = frames.clear()
}
