package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detects body posture: standing, sitting, lying (prone/supine/side), or unknown.
 *
 * Uses the angle of the body's main axis (shoulders → hips) relative to the image
 * horizontal for the primary standing/lying split.
 *
 * **Sitting** is detected within the "upright" zone using a vote of up to three
 * signals: (1) torso vs thigh angle, (2) thigh vs shin bend at the knee, (3) hip–knee
 * vertical proximity vs torso length. At least two must agree; if knees are not
 * visible enough, the torso–thigh signal alone is used (legacy behavior).
 *
 * **Lying sub-classification**:
 *   • Y-stacking of paired landmarks → candidate side-lying
 *   • Z-depth **guard**: if Y-stacking is detected but paired landmarks show
 *     large Z-separation, the stacking is a perspective artifact from prone/supine
 *     viewed from the side — NOT genuine side-lying. The candidate is rejected.
 *   • **Cross product** of spine × limb-pair (rotation-invariant) → primary
 *     prone vs supine signal. Positive = front faces camera = supine.
 *   • Nose offset from body axis → secondary prone/supine
 *   • Face visibility score as final fallback
 *
 * Stateless — wrap with temporal smoothing (majority vote in [PoseSceneDetector])
 * for production use.
 */
object BodyPostureDetector {

    // Body-axis angle thresholds (degrees from horizontal).
    // Standing person: abs angle ≈ 90°; lying person: abs angle ≈ 0° or 180°.
    private const val STANDING_MIN_DEG = 40f
    private const val STANDING_MAX_DEG = 140f
    private const val LYING_THRESHOLD_DEG = 40f

    // Sitting: cosine of angle between torso and thigh vectors.
    // < 0.5 means angle > 60° → thighs diverge from torso → seated.
    private const val SITTING_COS_THRESHOLD = 0.5f
    private const val SITTING_MIN_SEGMENT = 0.03f
    private const val SITTING_MIN_VISIBILITY = 0.3f

    // Thigh vs shin (hip→knee vs knee→ankle): straight leg → cos ≈ 1; bent knee → lower.
    private const val SITTING_KNEE_BEND_COS_MAX = 0.65f

    // Sitting: hips closer to knees vertically (in image) vs standing — ratio |hipY−kneeY|/torso.
    private const val SITTING_HIP_KNEE_VERTICAL_RATIO = 0.42f

    // Y-stacking: when lying on side, BOTH shoulder-pair AND hip-pair must
    // independently show vertical stacking above this threshold.
    private const val Y_STACKING_SIDE_THRESHOLD = 0.12f

    // Z-perspective guard: if Y-stacking is detected but the average Z-diff
    // between paired landmarks exceeds this, the stacking is a perspective
    // artifact (prone/supine from side), not genuine side-lying.
    // Genuine side-lying → pairs at similar depth → Z-diff < 0.10.
    // Prone/supine from side → one pair member behind the other → Z-diff > 0.30.
    private const val Z_PERSPECTIVE_GUARD = 0.20f

    // Cross product of spine × limb-pair vectors for prone/supine (primary).
    // Positive → anatomical front faces camera → supine.
    // Negative → anatomical front faces away → prone.
    private const val CROSS_SUPINE_THRESHOLD = 0.08f
    private const val CROSS_PRONE_THRESHOLD = -0.08f

    // Nose offset from body axis for prone/supine (secondary)
    private const val NOSE_OFFSET_SUPINE_THRESHOLD = -0.04f
    private const val NOSE_OFFSET_PRONE_THRESHOLD = 0.04f

    // Face score as final fallback
    private const val FACE_SUPINE_THRESHOLD = 0.35f
    private const val FACE_PRONE_THRESHOLD = 0.15f

    data class PostureResult(
        val posture: BodyPosture,
        val confidence: Float,
        val bodyAxisAngleDeg: Float
    )

    fun detect(landmarks: List<SmoothedLandmark>): PostureResult {
        if (landmarks.size < 33) return PostureResult(BodyPosture.UNKNOWN, 0f, 0f)

        val lShoulder = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rShoulder = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val lHip = landmarks[BodyLandmarks.LEFT_HIP]
        val rHip = landmarks[BodyLandmarks.RIGHT_HIP]

        val shoulderCx = (lShoulder.x + rShoulder.x) / 2f
        val shoulderCy = (lShoulder.y + rShoulder.y) / 2f
        val hipCx = (lHip.x + rHip.x) / 2f
        val hipCy = (lHip.y + rHip.y) / 2f

        val dx = hipCx - shoulderCx
        val dy = hipCy - shoulderCy
        val absAngle = abs(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        val torsoLength = sqrt(dx * dx + dy * dy)

        return when {
            absAngle < LYING_THRESHOLD_DEG || absAngle > (180f - LYING_THRESHOLD_DEG) -> {
                val posture = classifyLyingType(landmarks, lShoulder, rShoulder, lHip, rHip, torsoLength, dx, dy)
                val lyingConf = (1f - min(absAngle, abs(180f - absAngle)) / LYING_THRESHOLD_DEG)
                    .coerceIn(0.5f, 1f)
                PostureResult(posture, lyingConf, absAngle)
            }

            absAngle in STANDING_MIN_DEG..STANDING_MAX_DEG -> {
                val conf = 1f - (abs(absAngle - 90f) / 50f).coerceIn(0f, 1f)
                val uprightConf = 0.5f + 0.5f * conf

                val sitting = checkSitting(landmarks, dx, dy, torsoLength, hipCx, hipCy)
                if (sitting) {
                    PostureResult(BodyPosture.SITTING, uprightConf, absAngle)
                } else {
                    PostureResult(BodyPosture.STANDING, uprightConf, absAngle)
                }
            }

            else -> {
                PostureResult(BodyPosture.STANDING, 0.35f, absAngle)
            }
        }
    }

    /**
     * Detect sitting using multi-signal voting:
     * 1) Torso vs mid-thigh: seated → thighs not aligned with torso (cos < threshold).
     * 2) Thigh vs shin at each knee: bent knee → cos below [SITTING_KNEE_BEND_COS_MAX].
     * 3) Hip–knee vertical separation vs torso length: seated → hips nearer knees than typical standing.
     * Requires at least 2 of 3 when auxiliary signals are available; otherwise torso–thigh only.
     */
    private fun checkSitting(
        landmarks: List<SmoothedLandmark>,
        torsoDx: Float,
        torsoDy: Float,
        torsoLength: Float,
        hipCx: Float,
        hipCy: Float
    ): Boolean {
        if (torsoLength < SITTING_MIN_SEGMENT) return false

        val lKnee = landmarks[BodyLandmarks.LEFT_KNEE]
        val rKnee = landmarks[BodyLandmarks.RIGHT_KNEE]

        if (lKnee.visibility < SITTING_MIN_VISIBILITY && rKnee.visibility < SITTING_MIN_VISIBILITY) {
            return false
        }

        val kneeCx = (lKnee.x + rKnee.x) / 2f
        val kneeCy = (lKnee.y + rKnee.y) / 2f
        val thighDx = kneeCx - hipCx
        val thighDy = kneeCy - hipCy
        val thighLength = sqrt(thighDx * thighDx + thighDy * thighDy)
        if (thighLength < SITTING_MIN_SEGMENT) return false

        val dotTorsoThigh = torsoDx * thighDx + torsoDy * thighDy
        val cosTorsoThigh = dotTorsoThigh / (torsoLength * thighLength)
        val signalTorsoThigh = cosTorsoThigh < SITTING_COS_THRESHOLD

        val lHip = landmarks[BodyLandmarks.LEFT_HIP]
        val rHip = landmarks[BodyLandmarks.RIGHT_HIP]
        val lAnkle = landmarks[BodyLandmarks.LEFT_ANKLE]
        val rAnkle = landmarks[BodyLandmarks.RIGHT_ANKLE]

        val anklesWeak =
            lAnkle.visibility < SITTING_MIN_VISIBILITY && rAnkle.visibility < SITTING_MIN_VISIBILITY
        if (anklesWeak) {
            return signalTorsoThigh
        }

        val kneeBendOk = legKneeBendSitting(lHip, lKnee, lAnkle) ||
            legKneeBendSitting(rHip, rKnee, rAnkle)

        val verticalSep = abs(hipCy - kneeCy) / torsoLength.coerceAtLeast(SITTING_MIN_SEGMENT)
        val signalHipKneeVertical = verticalSep < SITTING_HIP_KNEE_VERTICAL_RATIO

        val votes = listOf(signalTorsoThigh, kneeBendOk, signalHipKneeVertical).count { it }
        return votes >= 2
    }

    /** Thigh (hip→knee) vs shin (knee→ankle): bent knee suitable for sitting. */
    private fun legKneeBendSitting(
        hip: SmoothedLandmark,
        knee: SmoothedLandmark,
        ankle: SmoothedLandmark
    ): Boolean {
        if (knee.visibility < SITTING_MIN_VISIBILITY) return false
        if (hip.visibility < SITTING_MIN_VISIBILITY || ankle.visibility < SITTING_MIN_VISIBILITY) {
            return false
        }
        val tx = knee.x - hip.x
        val ty = knee.y - hip.y
        val sx = ankle.x - knee.x
        val sy = ankle.y - knee.y
        val tLen = sqrt(tx * tx + ty * ty)
        val sLen = sqrt(sx * sx + sy * sy)
        if (tLen < SITTING_MIN_SEGMENT || sLen < SITTING_MIN_SEGMENT) return false
        val cosThighShin = (tx * sx + ty * sy) / (tLen * sLen)
        return cosThighShin < SITTING_KNEE_BEND_COS_MAX
    }

    /**
     * Determine lying sub-type:
     * 1. Y-stacking of paired landmarks → candidate side-lying
     *    1b. Z-perspective guard → reject if Z-diff too large (perspective artifact)
     * 2. Cross product of spine × limb-pair → primary prone/supine (rotation-invariant)
     * 3. Nose offset from body axis → secondary prone/supine
     * 4. Face visibility → final fallback
     */
    private fun classifyLyingType(
        landmarks: List<SmoothedLandmark>,
        lShoulder: SmoothedLandmark,
        rShoulder: SmoothedLandmark,
        lHip: SmoothedLandmark,
        rHip: SmoothedLandmark,
        torsoLength: Float,
        spineX: Float,
        spineY: Float
    ): BodyPosture {
        // ── Side Lying candidate: Y-stacking ──
        if (torsoLength > 0.03f) {
            val shoulderStacking = abs(lShoulder.y - rShoulder.y) / torsoLength
            val hipStacking = abs(lHip.y - rHip.y) / torsoLength
            val minStacking = min(shoulderStacking, hipStacking)
            if (minStacking > Y_STACKING_SIDE_THRESHOLD) {
                val pairZDiff = (abs(lShoulder.z - rShoulder.z) +
                        abs(lHip.z - rHip.z)) / 2f
                if (pairZDiff < Z_PERSPECTIVE_GUARD) {
                    return BodyPosture.LYING_SIDE
                }
            }
        }

        // ── Prone vs Supine: cross product (primary, rotation-invariant) ──
        val tSq = torsoLength * torsoLength
        if (tSq > 0.001f) {
            val shoulderDx = lShoulder.x - rShoulder.x
            val shoulderDy = lShoulder.y - rShoulder.y
            val hipDx = lHip.x - rHip.x
            val hipDy = lHip.y - rHip.y
            val shoulderCross = spineY * shoulderDx - spineX * shoulderDy
            val hipCross = spineY * hipDx - spineX * hipDy
            val crossScore = (shoulderCross + hipCross) / (2f * tSq)

            if (crossScore > CROSS_SUPINE_THRESHOLD) return BodyPosture.LYING_SUPINE
            if (crossScore < CROSS_PRONE_THRESHOLD) return BodyPosture.LYING_PRONE
        }

        // ── Prone vs Supine: nose offset (secondary) ──
        val nose = landmarks.getOrNull(BodyLandmarks.NOSE)
        if (nose != null && nose.visibility > 0.3f && torsoLength > 0.03f) {
            val bodyMidY = (lShoulder.y + rShoulder.y + lHip.y + rHip.y) / 4f
            val noseOffset = (nose.y - bodyMidY) / torsoLength
            when {
                noseOffset < NOSE_OFFSET_SUPINE_THRESHOLD -> return BodyPosture.LYING_SUPINE
                noseOffset > NOSE_OFFSET_PRONE_THRESHOLD -> return BodyPosture.LYING_PRONE
            }
        }

        // ── Fallback: face visibility ──
        val faceScore = CameraPositionDetector.computeFaceVisibilityScore(landmarks)
        return when {
            faceScore > FACE_SUPINE_THRESHOLD -> BodyPosture.LYING_SUPINE
            faceScore < FACE_PRONE_THRESHOLD -> BodyPosture.LYING_PRONE
            else -> BodyPosture.LYING_SUPINE
        }
    }
}

enum class BodyPosture {
    STANDING,
    LYING_PRONE,
    LYING_SUPINE,
    LYING_SIDE,
    SITTING,
    UNKNOWN
}
