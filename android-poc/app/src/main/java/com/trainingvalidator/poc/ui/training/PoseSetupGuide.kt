package com.trainingvalidator.poc.ui.training

import android.util.Log
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.CameraPositionDetector
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.JointRole
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * PoseSetupGuide - Smart pre-training position guidance system
 *
 * Replaces PoseValidator with a richer, more user-friendly approach:
 *
 * 1. **Rolling Window Confirmation** (9/12 frames) - tolerates camera noise
 * 2. **Per-joint colour guidance** (GREEN / YELLOW / RED)
 * 3. **Directional feedback** (RAISE / LOWER) per joint
 * 4. **Camera position tip** (soft - never blocks start)
 * 5. **Voice guidance** target: always the single worst joint
 *
 * Philosophy: "Guide, don't gate" - help the user reach the position,
 * never refuse to start unless the pose is clearly wrong.
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

    // ── Rolling window for camera position ────────────────────────────────

    private val cameraWindow = RollingCameraWindow(
        size = SettingsManager.settings.setupValidation.cameraCheckWindowSize.takeIf { it > 0 }
            ?: DEFAULT_CAM_WINDOW_SIZE,
        required = SettingsManager.settings.setupValidation.cameraCheckRequired.takeIf { it > 0 }
            ?: DEFAULT_CAM_REQUIRED
    )

    // ── Last voice guidance state (to drive cooldown in caller) ───────────

    private var lastVoiceJointCode: String? = null
    private var lastVoiceTimeMs: Long = 0L

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Validate current pose and produce full setup guidance.
     *
     * Should be called every frame from the ValidatePose supervisor action.
     *
     * @return [SetupResult] with per-joint guidance, camera tip, and overall progress
     */
    fun validate(
        angles: JointAngles?,
        landmarks: List<SmoothedLandmark>?,
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int
    ): SetupResult {

        if (angles == null || exerciseConfig == null) {
            jointWindow.add(false)
            return SetupResult.empty()
        }

        val variant = exerciseConfig.poseVariants.getOrNull(poseVariantIndex)
            ?: return SetupResult.empty().also { jointWindow.add(false) }

        val visibilityThreshold = SettingsManager.getPoseValidationVisibility()
        val closeThreshold = SettingsManager.settings.setupValidation
            .closeThresholdDegrees.takeIf { it > 0.0 } ?: DEFAULT_CLOSE_THRESHOLD

        // ── Per-joint guidance ────────────────────────────────────────────
        val jointGuidances = variant.trackedJoints.mapNotNull { joint ->
            buildJointGuidance(joint, angles, visibilityThreshold, closeThreshold)
        }

        // Must have guidance for ALL tracked joints — if any is missing (null angle /
        // invalid visibility) the frame is invalid. Prevents vacuous-truth confirmation
        // when the user isn't visible.
        val allJointsPresent = jointGuidances.size == variant.trackedJoints.size
        val allJointsValid = allJointsPresent && jointGuidances.all { it.level == GuidanceLevel.GREEN }
        jointWindow.add(allJointsValid)

        // ── Camera position guidance ──────────────────────────────────────
        val expectedCamera = variant.cameraPosition.takeIf { it.isNotBlank() }
        val cameraGuidance = if (
            expectedCamera != null &&
            landmarks != null &&
            landmarks.size >= 33 &&
            SettingsManager.settings.setupValidation.cameraTipEnabled
        ) {
            val detected = CameraPositionDetector.detect(landmarks).position
            cameraWindow.add(detected)
            buildCameraGuidance(cameraWindow.dominantPosition(), expectedCamera)
        } else {
            null
        }

        // ── Progress & confirmation ────────────────────────────────────────
        val progress = SetupProgress(
            percent = (jointWindow.validRatio() * 100).toInt().coerceIn(0, 100),
            isConfirmed = jointWindow.isConfirmed()
        )

        // ── Worst joint for voice guidance ────────────────────────────────
        val worstJoint = jointGuidances
            .filter { it.level != GuidanceLevel.GREEN }
            .maxByOrNull { it.distance }

        if (progress.isConfirmed) {
            Log.d(TAG, "Pose confirmed via rolling window")
        }

        return SetupResult(
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

    private fun buildCameraGuidance(
        dominantDetected: CameraPositionDetector.DetectedCameraPosition?,
        expectedPosition: String
    ): CameraGuidance {
        val isCorrect = dominantDetected != null &&
                CameraPositionDetector.matchesExpected(dominantDetected, expectedPosition)

        val detectedStr = dominantDetected?.let {
            CameraPositionDetector.toJsonCameraPosition(it)
        } ?: "unknown"

        val tip: LocalizedText? = if (!isCorrect) {
            buildCameraTip(expectedPosition)
        } else null

        return CameraGuidance(
            isCorrect = isCorrect,
            detectedPosition = detectedStr,
            expectedPosition = expectedPosition,
            tip = tip
        )
    }

    private fun buildCameraTip(expectedPosition: String): LocalizedText {
        return when (expectedPosition) {
            "side_view" -> LocalizedText(ar = "جانبي ↻", en = "Side ↻")
            "front_view" -> LocalizedText(ar = "أمامي ↻", en = "Front ↻")
            "back_view" -> LocalizedText(ar = "خلفي ↻", en = "Back ↻")
            else -> LocalizedText(ar = "عدّل ↻", en = "Adjust ↻")
        }
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
     * Returns whether enough cooldown has passed to speak voice guidance.
     * Caller should check this before calling FeedbackManager.speakSetupGuidance().
     */
    fun shouldSpeakGuidance(worstJoint: JointGuidance?): Boolean {
        if (worstJoint == null) return false
        val cooldownMs = SettingsManager.settings.setupValidation.voiceCooldownMs
            .takeIf { it > 0L } ?: 2500L
        val now = System.currentTimeMillis()
        val sameJoint = worstJoint.jointCode == lastVoiceJointCode
        val cooldownOk = (now - lastVoiceTimeMs) >= cooldownMs
        // Always speak on first occurrence or when joint changes, or after cooldown
        return !sameJoint || cooldownOk
    }

    /** Mark that voice guidance was spoken for [joint]. */
    fun onVoiceGuidanceSpoken(joint: JointGuidance) {
        lastVoiceJointCode = joint.jointCode
        lastVoiceTimeMs = System.currentTimeMillis()
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
        cameraWindow.reset()
        lastVoiceJointCode = null
        lastVoiceTimeMs = 0L
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

/**
 * Full result of one setup validation pass.
 */
data class SetupResult(
    val joints: List<JointGuidance>,
    val camera: CameraGuidance?,
    val progress: SetupProgress,
    val worstJoint: JointGuidance?,
    val isConfirmed: Boolean
) {
    companion object {
        fun empty() = SetupResult(
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

/**
 * Rolling window that tracks camera positions and returns the dominant one.
 */
class RollingCameraWindow(val size: Int, val required: Int) {

    private val frames = ArrayDeque<CameraPositionDetector.DetectedCameraPosition>(size)

    fun add(position: CameraPositionDetector.DetectedCameraPosition) {
        if (frames.size >= size) frames.removeFirst()
        frames.addLast(position)
    }

    /** Returns the most frequent position if it meets [required] threshold, else null. */
    fun dominantPosition(): CameraPositionDetector.DetectedCameraPosition? {
        if (frames.isEmpty()) return null
        val counts = frames.groupingBy { it }.eachCount()
        val (dominant, count) = counts.maxByOrNull { it.value } ?: return null
        return if (count >= required) dominant else null
    }

    fun reset() = frames.clear()
}
