package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 26-D feature vector for [ElbowCorrectionMlpClassifier]. Must match
 * `tools/elbow-mlp/feature_engineering.py` exactly.
 *
 * Feature Groups (26 total):
 *   A (0-1):   Angle measurements
 *   B (2-9):   Arm segment geometry
 *   C (10-13): Camera & body orientation
 *   D (14-17): Arm spatial context
 *   E (18-21): Reliability signals
 *   F (22-25): World landmark positions (elbow-relative)
 */
object ElbowMlpFeatureExtractor {

    const val FEATURE_COUNT = 26
    private const val EPS = 1e-7f

    val FEATURE_NAMES = arrayOf(
        "ang2D", "ang3D",
        "ua_len_2d", "fa_len_2d", "ua_len_3d", "fa_len_3d",
        "dz_share_ua", "dz_share_fa", "len_ratio_2d", "dz_sign_diff",
        "facing_ratio", "shoulder_w_norm", "spine_angle", "arm_body_angle",
        "wrist_drop", "wrist_reach", "elbow_drop", "elbow_forward",
        "vis_shoulder", "vis_elbow", "vis_wrist", "torso_len",
        "w_sh_x", "w_sh_y", "w_wr_x", "w_wr_y",
    )

    /**
     * Compute 26 features for one elbow.
     *
     * @param normLandmarks  MediaPipe normalized landmarks (33 items)
     * @param worldLandmarks MediaPipe world landmarks (33 items)
     * @param side "right" or "left" — left features are mirrored to right geometry
     * @return FloatArray(26), or null if landmarks are invalid
     */
    fun computeFeatures(
        normLandmarks: List<SmoothedLandmark>,
        worldLandmarks: List<SmoothedLandmark>,
        side: String = "right",
    ): FloatArray? {
        if (normLandmarks.size < 33 || worldLandmarks.size < 33) return null

        val n = normLandmarks
        val w = worldLandmarks

        val shI: Int; val elI: Int; val wrI: Int
        if (side == "right") {
            shI = BodyLandmarks.RIGHT_SHOULDER
            elI = BodyLandmarks.RIGHT_ELBOW
            wrI = BodyLandmarks.RIGHT_WRIST
        } else {
            shI = BodyLandmarks.LEFT_SHOULDER
            elI = BodyLandmarks.LEFT_ELBOW
            wrI = BodyLandmarks.LEFT_WRIST
        }
        val mirror = if (side == "right") 1f else -1f

        // Torso reference (2D + 3D)
        val shMid2dX = (n[BodyLandmarks.LEFT_SHOULDER].x + n[BodyLandmarks.RIGHT_SHOULDER].x) / 2f
        val shMid2dY = (n[BodyLandmarks.LEFT_SHOULDER].y + n[BodyLandmarks.RIGHT_SHOULDER].y) / 2f
        val hipMid2dX = (n[BodyLandmarks.LEFT_HIP].x + n[BodyLandmarks.RIGHT_HIP].x) / 2f
        val hipMid2dY = (n[BodyLandmarks.LEFT_HIP].y + n[BodyLandmarks.RIGHT_HIP].y) / 2f
        val torsoLen = dist2d(shMid2dX, shMid2dY, hipMid2dX, hipMid2dY)
        if (torsoLen < 0.02f) return null

        val shMid3d = xyz(w, BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER)
        val hipMid3d = xyz(w, BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP)
        val torsoLen3d = dist3d(shMid3d, hipMid3d) + EPS

        // Group A: Angle measurements
        val ba2d = floatArrayOf(n[shI].x - n[elI].x, n[shI].y - n[elI].y)
        val bc2d = floatArrayOf(n[wrI].x - n[elI].x, n[wrI].y - n[elI].y)
        val ang2D = angleDeg2d(ba2d, bc2d) / 180f

        val ba3d = floatArrayOf(w[shI].x - w[elI].x, w[shI].y - w[elI].y, w[shI].z - w[elI].z)
        val bc3d = floatArrayOf(w[wrI].x - w[elI].x, w[wrI].y - w[elI].y, w[wrI].z - w[elI].z)
        val ang3D = angleDeg3d(ba3d, bc3d) / 180f

        // Group B: Arm segment geometry
        val ua2dLen = dist2d(n[shI].x, n[shI].y, n[elI].x, n[elI].y)
        val fa2dLen = dist2d(n[elI].x, n[elI].y, n[wrI].x, n[wrI].y)
        val ua3dLen = dist3dLm(w[shI], w[elI]) + EPS
        val fa3dLen = dist3dLm(w[elI], w[wrI]) + EPS

        val uaLen2d = ua2dLen / torsoLen
        val faLen2d = fa2dLen / torsoLen
        val uaLen3d = ua3dLen / torsoLen
        val faLen3d = fa3dLen / torsoLen

        val dzUa = abs(w[elI].z - w[shI].z)
        val dzFa = abs(w[wrI].z - w[elI].z)
        val dzShareUa = dzUa / ua3dLen
        val dzShareFa = dzFa / fa3dLen

        val lenRatio2d = fa2dLen / (ua2dLen + EPS)

        val signUa = if (w[elI].z - w[shI].z >= 0) 1f else -1f
        val signFa = if (w[wrI].z - w[elI].z >= 0) 1f else -1f
        val dzSignDiff = signUa * signFa

        // Group C: Camera & body orientation
        val shDist2d = abs(n[BodyLandmarks.LEFT_SHOULDER].x - n[BodyLandmarks.RIGHT_SHOULDER].x)
        val shDist3d = dist3dLm(w[BodyLandmarks.LEFT_SHOULDER], w[BodyLandmarks.RIGHT_SHOULDER]) + EPS
        val facingRatio = minOf(shDist2d / shDist3d, 1.5f)

        val shoulderWNorm = shDist2d / torsoLen

        val torsoVec2d = floatArrayOf(hipMid2dX - shMid2dX, hipMid2dY - shMid2dY)
        val xAxis = floatArrayOf(1f, 0f)
        val spineAngle = angleDeg2d(torsoVec2d, xAxis) / 180f

        val armVec2d = floatArrayOf(n[elI].x - n[shI].x, n[elI].y - n[shI].y)
        val armBodyAngle = angleDeg2d(armVec2d, torsoVec2d) / 180f

        // Group D: Arm spatial context
        val wristDrop = (n[wrI].y - n[elI].y) / torsoLen
        val wristReach = mirror * (n[wrI].x - n[shI].x) / torsoLen
        val elbowDrop = (n[elI].y - n[shI].y) / torsoLen
        val elbowForward = (w[elI].z - w[shI].z) / torsoLen3d

        // Group E: Reliability signals
        val visShoulder = n[shI].visibility
        val visElbow = n[elI].visibility
        val visWrist = n[wrI].visibility

        // Group F: World landmark positions (elbow-relative)
        val wShX = mirror * (w[shI].x - w[elI].x) / ua3dLen
        val wShY = (w[shI].y - w[elI].y) / ua3dLen
        val wWrX = mirror * (w[wrI].x - w[elI].x) / fa3dLen
        val wWrY = (w[wrI].y - w[elI].y) / fa3dLen

        return floatArrayOf(
            ang2D, ang3D,
            uaLen2d, faLen2d, uaLen3d, faLen3d,
            dzShareUa, dzShareFa, lenRatio2d, dzSignDiff,
            facingRatio, shoulderWNorm, spineAngle, armBodyAngle,
            wristDrop, wristReach, elbowDrop, elbowForward,
            visShoulder, visElbow, visWrist, torsoLen,
            wShX, wShY, wWrX, wWrY,
        )
    }

    // --- helpers ---

    private fun dist2d(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist3dLm(a: SmoothedLandmark, b: SmoothedLandmark): Float {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun dist3d(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun xyz(lm: List<SmoothedLandmark>, i1: Int, i2: Int): FloatArray {
        return floatArrayOf(
            (lm[i1].x + lm[i2].x) / 2f,
            (lm[i1].y + lm[i2].y) / 2f,
            (lm[i1].z + lm[i2].z) / 2f,
        )
    }

    private fun angleDeg2d(v1: FloatArray, v2: FloatArray): Float {
        val dot = v1[0] * v2[0] + v1[1] * v2[1]
        val m1 = sqrt(v1[0] * v1[0] + v1[1] * v1[1]) + EPS
        val m2 = sqrt(v2[0] * v2[0] + v2[1] * v2[1]) + EPS
        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    private fun angleDeg3d(v1: FloatArray, v2: FloatArray): Float {
        val dot = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2]
        val m1 = sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]) + EPS
        val m2 = sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2]) + EPS
        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }
}
