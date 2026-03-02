package com.trainingvalidator.poc.ui.training

import android.util.Log
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.*
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.JointRole
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * PoseSetupGuide - Smart pre-training position guidance system
 *
 * Uses a sequential phase-gated approach:
 *
 * **Phase 1 – REGION**: Ensure the correct body region is visible
 * **Phase 2 – POSTURE**: Ensure the correct body posture
 * **Phase 3 – DIRECTION**: Ensure the correct camera direction
 * **Phase 4 – ANGLES**: Fine-tune joint angles (Rolling Window 9/12)
 *
 * Each phase must be satisfied before advancing to the next.
 * Voice and UI guidance focus on exactly the current blocking phase.
 */
class PoseSetupGuide(
    val language: String = "ar"
) {

    companion object {
        private const val TAG = "PoseSetupGuide"

        // Rolling window defaults - overridden by app_settings.json
        private const val DEFAULT_WINDOW_SIZE = 12
        private const val DEFAULT_REQUIRED_VALID = 9

        // Degrees within which a joint is considered "close" (YELLOW vs RED)
        private const val DEFAULT_CLOSE_THRESHOLD = 15.0

        // Camera rolling window
        private const val DEFAULT_CAM_WINDOW_SIZE = 12
        private const val DEFAULT_CAM_REQUIRED = 9
    }

    // ── Rolling window for joint confirmation ──────────────────────────────

    private val jointWindow = RollingWindow(
        size = SettingsManager.settings.setupValidation.windowSize.takeIf { it > 0 }
            ?: DEFAULT_WINDOW_SIZE,
        required = SettingsManager.settings.setupValidation.requiredValid.takeIf { it > 0 }
            ?: DEFAULT_REQUIRED_VALID
    )

    // ── 3-axis scene detector (direction + posture + region) ───────────────

    private val sceneDetector = PoseSceneDetector(
        windowSize = SettingsManager.settings.setupValidation.cameraCheckWindowSize.takeIf { it > 0 }
            ?: DEFAULT_CAM_WINDOW_SIZE,
        requiredMajority = SettingsManager.settings.setupValidation.cameraCheckRequired.takeIf { it > 0 }
            ?: DEFAULT_CAM_REQUIRED
    )

    // ── Voice guidance state ────────────────────────────────────────────

    private var lastVoiceJointCode: String? = null
    private var lastVoiceTimeMs: Long = 0L

    private var lastVoicePhase: SetupPhase? = null
    private var lastVoicePhaseTimeMs: Long = 0L

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Validate current pose using sequential phase gating.
     *
     * Checks axes in order: Region → Posture → Direction → Angles.
     * Only advances to the next phase when the current one is satisfied.
     *
     * @return [SetupResult] with current phase, guidance, and overall progress
     */
    fun validate(
        angles: JointAngles?,
        landmarks: List<SmoothedLandmark>?,
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int,
        isFrontCamera: Boolean = false
    ): SetupResult {

        if (angles == null || exerciseConfig == null) {
            jointWindow.add(false)
            return SetupResult.empty()
        }

        val variant = exerciseConfig.poseVariants.getOrNull(poseVariantIndex)
            ?: return SetupResult.empty().also { jointWindow.add(false) }

        // ── Build scene expectation ──────────────────────────────────────
        val expectation = if (variant.expectedPostures != null) {
            PoseSceneExpectation.fromJson(variant.expectedPostures, variant.expectedDirections, variant.expectedRegions)
        } else {
            val posCode = variant.posePosition ?: variant.cameraPosition ?: "standing_side"
            PoseSceneExpectation.fromLegacyCode(posCode)
        }

        // ── Detect scene (3-axis) ────────────────────────────────────────
        val validLandmarks = landmarks?.takeIf {
            it.size >= 33 && SettingsManager.settings.setupValidation.cameraTipEnabled
        }

        val scene = validLandmarks?.let { sceneDetector.detect(it, isFrontCamera) }
        val axisMatch = if (scene != null) expectation.matchesScene(scene) else null

        // ── Determine current phase (first failing axis in priority order) ─
        // When scene detection is unavailable, skip straight to ANGLES
        val phase: SetupPhase
        val phaseMessage: LocalizedText?

        if (axisMatch == null) {
            phase = SetupPhase.ANGLES
            phaseMessage = null
        } else if (!axisMatch.regionMatch) {
            phase = SetupPhase.REGION
            phaseMessage = buildRegionTip(expectation.regions)
        } else if (!axisMatch.postureMatch) {
            phase = SetupPhase.POSTURE
            phaseMessage = buildPostureTip(expectation.postures)
        } else if (!axisMatch.directionMatch) {
            phase = SetupPhase.DIRECTION
            phaseMessage = buildDirectionTip(expectation.directions)
        } else {
            phase = SetupPhase.ANGLES
            phaseMessage = null
        }

        // ── Joint guidance (only computed in ANGLES phase) ───────────────
        val visibilityThreshold = SettingsManager.getPoseValidationVisibility()
        val closeThreshold = SettingsManager.settings.setupValidation
            .closeThresholdDegrees.takeIf { it > 0.0 } ?: DEFAULT_CLOSE_THRESHOLD

        val jointGuidances = if (phase == SetupPhase.ANGLES) {
            variant.trackedJoints.mapNotNull { joint ->
                buildJointGuidance(joint, angles, visibilityThreshold, closeThreshold)
            }
        } else {
            emptyList()
        }

        // Rolling window only counts when ALL scene axes pass AND all joints valid
        val allJointsPresent = phase == SetupPhase.ANGLES &&
                jointGuidances.size == variant.trackedJoints.size
        val allJointsValid = allJointsPresent &&
                jointGuidances.all { it.level == GuidanceLevel.GREEN }
        jointWindow.add(allJointsValid)

        // ── Camera guidance (for bottom bar VIEW card, backwards-compatible) ─
        val cameraGuidance = if (scene != null) {
            buildSceneGuidance(scene, expectation)
        } else null

        // ── Progress ─────────────────────────────────────────────────────
        val progressPercent = when (phase) {
            SetupPhase.REGION -> 0
            SetupPhase.POSTURE -> 25
            SetupPhase.DIRECTION -> 50
            SetupPhase.ANGLES -> {
                val windowRatio = jointWindow.validRatio()
                (50 + windowRatio * 50).toInt().coerceIn(50, 100)
            }
        }
        val progress = SetupProgress(
            percent = progressPercent,
            isConfirmed = jointWindow.isConfirmed()
        )

        val worstJoint = jointGuidances
            .filter { it.level != GuidanceLevel.GREEN }
            .maxByOrNull { it.distance }

        if (progress.isConfirmed) {
            Log.d(TAG, "Pose confirmed via rolling window")
        }

        return SetupResult(
            phase = phase,
            phaseMessage = phaseMessage,
            joints = jointGuidances,
            camera = cameraGuidance,
            progress = progress,
            worstJoint = worstJoint,
            isConfirmed = progress.isConfirmed
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun buildJointGuidance(
        joint: TrackedJoint,
        angles: JointAngles,
        visibilityThreshold: Float,
        closeThreshold: Double
    ): JointGuidance? {

        val angle = angles.getAngle(joint.joint) ?: return null

        // Reject anatomically impossible angles
        if (!SettingsManager.isAngleValid(angle)) return null

        val min = joint.startPose.min
        val max = joint.startPose.max

        val distance = when {
            angle < min -> min - angle
            angle > max -> angle - max
            else -> 0.0
        }

        val level = when {
            distance <= 0.0 -> GuidanceLevel.GREEN
            distance <= closeThreshold -> GuidanceLevel.YELLOW
            else -> GuidanceLevel.RED
        }

        val direction = when {
            angle < min -> Direction.RAISE
            angle > max -> Direction.LOWER
            else -> null
        }

        val message = buildGuidanceMessage(joint.joint, direction, level)

        return JointGuidance(
            jointCode = joint.joint,
            jointName = formatJointName(joint.joint),
            level = level,
            currentAngle = angle,
            targetMin = min,
            targetMax = max,
            distance = distance,
            direction = direction,
            message = message,
            isPrimary = joint.role == JointRole.PRIMARY
        )
    }

    private fun buildSceneGuidance(
        scene: PoseSceneResult,
        expectation: PoseSceneExpectation
    ): CameraGuidance {
        val match = expectation.matchesScene(scene)
        val tips = mutableListOf<LocalizedText>()

        if (!match.directionMatch) {
            tips.add(buildDirectionTip(expectation.directions))
        }
        if (!match.postureMatch) {
            tips.add(buildPostureTip(expectation.postures))
        }
        if (!match.regionMatch) {
            tips.add(buildRegionTip(expectation.regions))
        }

        val combinedTip = if (tips.isNotEmpty()) {
            LocalizedText(
                ar = tips.joinToString(" • ") { it.ar },
                en = tips.joinToString(" • ") { it.en }
            )
        } else null

        return CameraGuidance(
            isCorrect = match.allMatch,
            detectedPosition = CameraPositionDetector.toJsonCameraPosition(scene.direction),
            expectedPosition = expectation.directionLabel(),
            tip = combinedTip
        )
    }

    private fun buildDirectionTip(dirs: List<ExpectedDirection>): LocalizedText {
        val arParts = dirs.mapNotNull { dirAr(it) }
        val enParts = dirs.mapNotNull { dirEn(it) }
        return LocalizedText(
            ar = "صوّر من ${arParts.joinToString(" أو ")} ↻",
            en = "Film from ${enParts.joinToString(" or ")} ↻"
        )
    }

    private fun buildPostureTip(postures: List<BodyPosture>): LocalizedText {
        val arParts = postures.mapNotNull { posAr(it) }
        val enParts = postures.mapNotNull { posEn(it) }
        return LocalizedText(
            ar = arParts.joinToString(" أو "),
            en = enParts.joinToString(" or ")
        )
    }

    private fun buildRegionTip(regions: List<VisibleRegion>): LocalizedText {
        val arParts = regions.mapNotNull { regAr(it) }
        val enParts = regions.mapNotNull { regEn(it) }
        return LocalizedText(
            ar = "أظهر ${arParts.joinToString(" أو ")}",
            en = "Show ${enParts.joinToString(" or ")}"
        )
    }

    private fun dirAr(d: ExpectedDirection) = when (d) {
        ExpectedDirection.FRONT -> "الأمام"
        ExpectedDirection.BACK -> "الخلف"
        ExpectedDirection.SIDE_ANY -> "الجانب"
        ExpectedDirection.SIDE_LEFT -> "الجانب الأيسر"
        ExpectedDirection.SIDE_RIGHT -> "الجانب الأيمن"
        ExpectedDirection.DIAGONAL -> "بزاوية مائلة"
        ExpectedDirection.ANY -> null
    }
    private fun dirEn(d: ExpectedDirection) = when (d) {
        ExpectedDirection.FRONT -> "the front"
        ExpectedDirection.BACK -> "the back"
        ExpectedDirection.SIDE_ANY -> "the side"
        ExpectedDirection.SIDE_LEFT -> "the left side"
        ExpectedDirection.SIDE_RIGHT -> "the right side"
        ExpectedDirection.DIAGONAL -> "an angle"
        ExpectedDirection.ANY -> null
    }
    private fun posAr(p: BodyPosture) = when (p) {
        BodyPosture.STANDING -> "قف مستقيماً"
        BodyPosture.LYING_PRONE -> "استلقِ على وجهك"
        BodyPosture.LYING_SUPINE -> "استلقِ على ظهرك"
        BodyPosture.LYING_SIDE -> "استلقِ على جنبك"
        BodyPosture.SITTING -> "اجلس"
        BodyPosture.UNKNOWN -> null
    }
    private fun posEn(p: BodyPosture) = when (p) {
        BodyPosture.STANDING -> "Stand upright"
        BodyPosture.LYING_PRONE -> "Lie face down"
        BodyPosture.LYING_SUPINE -> "Lie face up"
        BodyPosture.LYING_SIDE -> "Lie on your side"
        BodyPosture.SITTING -> "Sit down"
        BodyPosture.UNKNOWN -> null
    }
    private fun regAr(r: VisibleRegion) = when (r) {
        VisibleRegion.FULL_BODY -> "الجسم بالكامل"
        VisibleRegion.UPPER_BODY -> "الجزء العلوي"
        VisibleRegion.LOWER_BODY -> "الجزء السفلي"
        VisibleRegion.UNKNOWN -> null
    }
    private fun regEn(r: VisibleRegion) = when (r) {
        VisibleRegion.FULL_BODY -> "your full body"
        VisibleRegion.UPPER_BODY -> "your upper body"
        VisibleRegion.LOWER_BODY -> "your lower body"
        VisibleRegion.UNKNOWN -> null
    }

    /**
     * Build directional guidance message for a joint.
     * Falls back to generic raise/lower when no specific mapping exists.
     */
    private fun buildGuidanceMessage(
        jointCode: String,
        direction: Direction?,
        level: GuidanceLevel
    ): LocalizedText {
        if (level == GuidanceLevel.GREEN || direction == null) {
            return LocalizedText(ar = "✓ ممتاز", en = "✓ Good")
        }

        val isRaise = direction == Direction.RAISE
        // Normalise to left_* for lookup; swap if right_*
        val base = jointCode.removePrefix("right_").removePrefix("left_")
        val side = when {
            jointCode.startsWith("left_") -> if (language == "ar") "الأيسر" else "left"
            jointCode.startsWith("right_") -> if (language == "ar") "الأيمن" else "right"
            else -> ""
        }

        return when (base) {
            "elbow" -> if (isRaise) LocalizedText(
                ar = "ارفع الكوع $side أكثر",
                en = "Raise your $side elbow more"
            ) else LocalizedText(
                ar = "اخفض الكوع $side أكثر",
                en = "Lower your $side elbow more"
            )
            "shoulder" -> if (isRaise) LocalizedText(
                ar = "ارفع الكتف $side",
                en = "Raise your $side shoulder"
            ) else LocalizedText(
                ar = "اخفض الكتف $side",
                en = "Lower your $side shoulder"
            )
            "knee" -> if (isRaise) LocalizedText(
                ar = "افرد الركبة $side أكثر",
                en = "Straighten your $side knee more"
            ) else LocalizedText(
                ar = "اثني الركبة $side أكثر",
                en = "Bend your $side knee more"
            )
            "hip" -> if (isRaise) LocalizedText(
                ar = "افرد الورك $side",
                en = "Extend your $side hip"
            ) else LocalizedText(
                ar = "اثني الورك $side أكثر",
                en = "Bend your $side hip more"
            )
            "ankle" -> if (isRaise) LocalizedText(
                ar = "ارفع الكاحل $side",
                en = "Raise your $side ankle"
            ) else LocalizedText(
                ar = "اخفض الكاحل $side",
                en = "Lower your $side ankle"
            )
            "wrist" -> if (isRaise) LocalizedText(
                ar = "ارفع المعصم $side",
                en = "Raise your $side wrist"
            ) else LocalizedText(
                ar = "اخفض المعصم $side",
                en = "Lower your $side wrist"
            )
            "spine" -> if (isRaise) LocalizedText(
                ar = "افرد ظهرك أكثر",
                en = "Straighten your back more"
            ) else LocalizedText(
                ar = "انحني للأمام أكثر",
                en = "Bend forward more"
            )
            "neck" -> if (isRaise) LocalizedText(
                ar = "ارفع رأسك",
                en = "Lift your head"
            ) else LocalizedText(
                ar = "اخفض رأسك",
                en = "Lower your head"
            )
            else -> if (isRaise) LocalizedText(
                ar = "ارفع أكثر",
                en = "Raise more"
            ) else LocalizedText(
                ar = "اخفض أكثر",
                en = "Lower more"
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public utilities
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns whether enough cooldown has passed to speak joint voice guidance.
     */
    fun shouldSpeakGuidance(worstJoint: JointGuidance?): Boolean {
        if (worstJoint == null) return false
        val cooldownMs = SettingsManager.settings.setupValidation.voiceCooldownMs
            .takeIf { it > 0L } ?: 2500L
        val now = System.currentTimeMillis()
        val sameJoint = worstJoint.jointCode == lastVoiceJointCode
        val cooldownOk = (now - lastVoiceTimeMs) >= cooldownMs
        return !sameJoint || cooldownOk
    }

    /** Mark that voice guidance was spoken for [joint]. */
    fun onVoiceGuidanceSpoken(joint: JointGuidance) {
        lastVoiceJointCode = joint.jointCode
        lastVoiceTimeMs = System.currentTimeMillis()
    }

    /**
     * Returns whether the scene-phase voice should speak.
     * Speaks immediately on phase change, then respects cooldown.
     */
    fun shouldSpeakPhaseGuidance(phase: SetupPhase): Boolean {
        if (phase == SetupPhase.ANGLES) return false
        val cooldownMs = SettingsManager.settings.setupValidation.voiceCooldownMs
            .takeIf { it > 0L } ?: 2500L
        val now = System.currentTimeMillis()
        val samePhase = phase == lastVoicePhase
        val cooldownOk = (now - lastVoicePhaseTimeMs) >= cooldownMs
        return !samePhase || cooldownOk
    }

    fun onPhaseGuidanceSpoken(phase: SetupPhase) {
        lastVoicePhase = phase
        lastVoicePhaseTimeMs = System.currentTimeMillis()
    }

    /** Text displayed in the setup panel header - shows required angles. */
    fun getPoseRequirementsText(
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int
    ): String {
        val variant = exerciseConfig?.poseVariants?.getOrNull(poseVariantIndex) ?: return ""
        return buildString {
            val header = if (language == "ar") "خذ وضع البداية:" else "Get into starting position:"
            appendLine(header)
            appendLine()
            variant.trackedJoints.forEach { joint ->
                val name = formatJointName(joint.joint)
                val indicator = if (joint.role == JointRole.PRIMARY) "●" else "○"
                val range = "${joint.startPose.min.toInt()}° – ${joint.startPose.max.toInt()}°"
                appendLine("$indicator $name: $range")
            }
        }.trimEnd()
    }

    /** Reset all rolling windows and voice cooldown. */
    fun reset() {
        jointWindow.reset()
        sceneDetector.reset()
        lastVoiceJointCode = null
        lastVoiceTimeMs = 0L
        lastVoicePhase = null
        lastVoicePhaseTimeMs = 0L
        Log.d(TAG, "PoseSetupGuide reset")
    }

    /** Current progress as 0–100 int (for UI). */
    fun getProgressPercent(): Int = (jointWindow.validRatio() * 100).toInt().coerceIn(0, 100)

    // ──────────────────────────────────────────────────────────────────────
    // Formatting
    // ──────────────────────────────────────────────────────────────────────

    private fun formatJointName(jointCode: String): String =
        jointCode.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

// ──────────────────────────────────────────────────────────────────────────
// Result types
// ──────────────────────────────────────────────────────────────────────────

/** Sequential setup phase - checked in this priority order. */
enum class SetupPhase {
    REGION,
    POSTURE,
    DIRECTION,
    ANGLES
}

/**
 * Full result of one setup validation pass.
 */
data class SetupResult(
    val phase: SetupPhase,
    val phaseMessage: LocalizedText?,
    val joints: List<JointGuidance>,
    val camera: CameraGuidance?,
    val progress: SetupProgress,
    val worstJoint: JointGuidance?,
    val isConfirmed: Boolean
) {
    companion object {
        fun empty() = SetupResult(
            phase = SetupPhase.REGION,
            phaseMessage = null,
            joints = emptyList(),
            camera = null,
            progress = SetupProgress(0, false),
            worstJoint = null,
            isConfirmed = false
        )
    }
}

/**
 * Guidance for a single tracked joint.
 */
data class JointGuidance(
    val jointCode: String,
    val jointName: String,
    val level: GuidanceLevel,
    val currentAngle: Double,
    val targetMin: Double,
    val targetMax: Double,
    /** Degrees outside the valid range (0 = in range). */
    val distance: Double,
    /** Direction to move (null when in range). */
    val direction: Direction?,
    val message: LocalizedText,
    val isPrimary: Boolean
)

/**
 * Camera position guidance (soft tip, never blocks start).
 */
data class CameraGuidance(
    val isCorrect: Boolean,
    val detectedPosition: String,
    val expectedPosition: String,
    /** Null when camera is correct (no tip needed). */
    val tip: LocalizedText?
)

/**
 * Overall setup progress.
 */
data class SetupProgress(
    val percent: Int,
    val isConfirmed: Boolean
)

/** Joint guidance level based on distance from target range. */
enum class GuidanceLevel {
    GREEN,   // In range
    YELLOW,  // ≤ closeThreshold° outside range
    RED      // > closeThreshold° outside range
}

/** Direction to move a joint to enter the valid range. */
enum class Direction {
    RAISE,
    LOWER
}

// ──────────────────────────────────────────────────────────────────────────
// Rolling Window implementations
// ──────────────────────────────────────────────────────────────────────────

/**
 * Fixed-size rolling window tracking boolean (valid/invalid) frames.
 * Confirms when at least [required] of the last [size] frames are valid.
 */
class RollingWindow(val size: Int, val required: Int) {

    private val frames = ArrayDeque<Boolean>(size)

    fun add(valid: Boolean) {
        if (frames.size >= size) frames.removeFirst()
        frames.addLast(valid)
    }

    fun isConfirmed(): Boolean =
        frames.size >= size && frames.count { it } >= required

    fun validRatio(): Float =
        if (frames.isEmpty()) 0f
        else frames.count { it }.toFloat() / frames.size.toFloat()

    fun reset() = frames.clear()
}

