package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 16-D feature vector for [PostureMlpClassifier]. Must match
 * `tools/posture-mlp/feature_engineering.py` exactly.
 */
object PostureMlpFeatureExtractor {

    const val FEATURE_COUNT = 16
    private const val EPS = 1e-6f

    /**
     * Body-axis angle (degrees) shoulder-mid → hip-mid vs horizontal; same basis as [BodyPostureDetector].
     */
    fun computeBodyAxisAngleDeg(landmarks: List<SmoothedLandmark>): Float {
        if (landmarks.size < 33) return 0f
        val ls = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rs = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val lh = landmarks[BodyLandmarks.LEFT_HIP]
        val rh = landmarks[BodyLandmarks.RIGHT_HIP]
        val shoulderCx = (ls.x + rs.x) * 0.5f
        val shoulderCy = (ls.y + rs.y) * 0.5f
        val hipCx = (lh.x + rh.x) * 0.5f
        val hipCy = (lh.y + rh.y) * 0.5f
        val dx = hipCx - shoulderCx
        val dy = hipCy - shoulderCy
        return abs(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
    }

    /**
     * Returns null if torso is too short or landmarks missing.
     */
    fun computeFeatures(landmarks: List<SmoothedLandmark>): FloatArray? {
        if (landmarks.size < 33) return null

        val ls = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rs = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val lh = landmarks[BodyLandmarks.LEFT_HIP]
        val rh = landmarks[BodyLandmarks.RIGHT_HIP]
        val lk = landmarks[BodyLandmarks.LEFT_KNEE]
        val rk = landmarks[BodyLandmarks.RIGHT_KNEE]
        val la = landmarks[BodyLandmarks.LEFT_ANKLE]
        val ra = landmarks[BodyLandmarks.RIGHT_ANKLE]
        val nose = landmarks[BodyLandmarks.NOSE]

        val scx = (ls.x + rs.x) * 0.5f
        val scy = (ls.y + rs.y) * 0.5f
        val hcx = (lh.x + rh.x) * 0.5f
        val hcy = (lh.y + rh.y) * 0.5f
        val dx = hcx - scx
        val dy = hcy - scy
        val torsoLen = sqrt(dx * dx + dy * dy)
        if (torsoLen < 0.02f) return null

        val absAngle = abs(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        val spineAngleNorm = (if (absAngle <= 180f) absAngle else 360f - absAngle) / 180f

        val kcx = (lk.x + rk.x) * 0.5f
        val kcy = (lk.y + rk.y) * 0.5f
        val thighDx = kcx - hcx
        val thighDy = kcy - hcy
        val thighLen = sqrt(thighDx * thighDx + thighDy * thighDy)
        val cosTorsoThigh = if (thighLen < EPS) {
            0f
        } else {
            val c = (dx * thighDx + dy * thighDy) / (torsoLen * thighLen)
            max(-1f, min(1f, c))
        }

        val angL = angleThreePoint(lh.x, lh.y, lk.x, lk.y, la.x, la.y) / 180f
        val angR = angleThreePoint(rh.x, rh.y, rk.x, rk.y, ra.x, ra.y) / 180f

        val shoulderW = abs(ls.x - rs.x)
        val hipW = abs(lh.x - rh.x)
        val shoulderWN = shoulderW / torsoLen
        val hipWN = hipW / torsoLen

        val kneeDrop = (kcy - hcy) / torsoLen
        val ankleDrop = ((la.y + ra.y) * 0.5f - kcy) / torsoLen

        val midTorsoY = (scy + hcy) * 0.5f
        val noseOff = if (nose.visibility > 0.3f) {
            (nose.y - midTorsoY) / torsoLen
        } else {
            0f
        }

        val shVSep = abs(ls.y - rs.y) / torsoLen
        val hipVSep = abs(lh.y - rh.y) / torsoLen

        val visKnee = min(lk.visibility, rk.visibility)
        val visHip = min(lh.visibility, rh.visibility)
        val visSh = min(ls.visibility, rs.visibility)

        val zTorso = abs((ls.z + rs.z) * 0.5f - (lh.z + rh.z) * 0.5f)

        return floatArrayOf(
            spineAngleNorm,
            torsoLen,
            cosTorsoThigh,
            angL,
            angR,
            shoulderWN,
            hipWN,
            kneeDrop,
            ankleDrop,
            noseOff,
            shVSep,
            hipVSep,
            visKnee,
            visHip,
            visSh,
            zTorso,
        )
    }

    private fun angleThreePoint(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
        val v1x = ax - bx
        val v1y = ay - by
        val v2x = cx - bx
        val v2y = cy - by
        val n1 = sqrt(v1x * v1x + v1y * v1y)
        val n2 = sqrt(v2x * v2x + v2y * v2y)
        if (n1 < EPS || n2 < EPS) return 90f
        var c = (v1x * v2x + v1y * v2y) / (n1 * n2)
        c = max(-1f, min(1f, c))
        return Math.toDegrees(acos(c.toDouble())).toFloat()
    }
}
