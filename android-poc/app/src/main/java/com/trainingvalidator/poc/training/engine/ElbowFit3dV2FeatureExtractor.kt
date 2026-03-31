package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 38-D feature vector for the FIT3D v2 elbow correction model.
 * Must match `tools/elbow-h3.6m/fit3d_test/feature_engineering.py  compute_fit3d_v2` exactly.
 */
object ElbowFit3dV2FeatureExtractor {

    const val FEATURE_COUNT = 38
    private const val EPS = 1e-7f

    val FEATURE_NAMES = arrayOf(
        "ang2d_xy", "ang3d_xyz", "ang_xz", "ang_yz",
        "ua_len_2d", "fa_len_2d", "ua_len_3d", "fa_len_3d",
        "dz_share_ua", "dz_share_fa", "len_ratio_2d", "dz_sign_diff",
        "facing_ratio", "shoulder_w_norm", "hip_w_norm",
        "torso_dx_norm", "torso_dy_norm", "shoulder_depth_asym",
        "se_dx_2d", "se_dy_2d", "ew_dx_2d", "ew_dy_2d",
        "sw_dx_2d", "sw_dy_2d",
        "ua_body_x", "ua_body_y", "ua_body_z",
        "fa_body_x", "fa_body_y", "fa_body_z",
        "ang_body_yz", "ang_body_xy",
        "vis_shoulder", "vis_elbow", "vis_wrist",
        "pres_shoulder", "pres_elbow", "pres_wrist",
    )

    fun computeFeatures(
        normLandmarks: List<SmoothedLandmark>,
        worldLandmarks: List<SmoothedLandmark>,
        side: String = "right",
    ): FloatArray? {
        if (normLandmarks.size < 33 || worldLandmarks.size < 33) return null

        val n = normLandmarks
        val w = worldLandmarks

        val shI: Int; val elI: Int; val wrI: Int; val oppShI: Int
        val mirror: Float
        if (side == "right") {
            shI = BodyLandmarks.RIGHT_SHOULDER
            elI = BodyLandmarks.RIGHT_ELBOW
            wrI = BodyLandmarks.RIGHT_WRIST
            oppShI = BodyLandmarks.LEFT_SHOULDER
            mirror = 1f
        } else {
            shI = BodyLandmarks.LEFT_SHOULDER
            elI = BodyLandmarks.LEFT_ELBOW
            wrI = BodyLandmarks.LEFT_WRIST
            oppShI = BodyLandmarks.RIGHT_SHOULDER
            mirror = -1f
        }

        val shMid2dX = (n[BodyLandmarks.LEFT_SHOULDER].x + n[BodyLandmarks.RIGHT_SHOULDER].x) / 2f
        val shMid2dY = (n[BodyLandmarks.LEFT_SHOULDER].y + n[BodyLandmarks.RIGHT_SHOULDER].y) / 2f
        val hipMid2dX = (n[BodyLandmarks.LEFT_HIP].x + n[BodyLandmarks.RIGHT_HIP].x) / 2f
        val hipMid2dY = (n[BodyLandmarks.LEFT_HIP].y + n[BodyLandmarks.RIGHT_HIP].y) / 2f
        val torsoVecX = shMid2dX - hipMid2dX
        val torsoVecY = shMid2dY - hipMid2dY
        val torsoLen2d = sqrt(torsoVecX * torsoVecX + torsoVecY * torsoVecY)
        if (torsoLen2d < 0.02f) return null

        val shMid3d = mid3d(w, BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER)
        val hipMid3d = mid3d(w, BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP)
        val torsoLen3d = dist3d(shMid3d, hipMid3d) + EPS
        if (torsoLen3d < 1e-4f) return null

        // 2D arm vectors
        val uaDx2d = n[elI].x - n[shI].x; val uaDy2d = n[elI].y - n[shI].y
        val faDx2d = n[wrI].x - n[elI].x; val faDy2d = n[wrI].y - n[elI].y
        val swDx2d = n[wrI].x - n[shI].x; val swDy2d = n[wrI].y - n[shI].y

        // 3D arm vectors
        val ua3dX = w[elI].x - w[shI].x; val ua3dY = w[elI].y - w[shI].y; val ua3dZ = w[elI].z - w[shI].z
        val fa3dX = w[wrI].x - w[elI].x; val fa3dY = w[wrI].y - w[elI].y; val fa3dZ = w[wrI].z - w[elI].z

        val ua3dLen = sqrt(ua3dX * ua3dX + ua3dY * ua3dY + ua3dZ * ua3dZ) + EPS
        val fa3dLen = sqrt(fa3dX * fa3dX + fa3dY * fa3dY + fa3dZ * fa3dZ) + EPS

        // Angle vectors for ba/bc
        val baXy = floatArrayOf(n[shI].x - n[elI].x, n[shI].y - n[elI].y)
        val bcXy = floatArrayOf(n[wrI].x - n[elI].x, n[wrI].y - n[elI].y)
        val baXz = floatArrayOf(w[shI].x - w[elI].x, w[shI].z - w[elI].z)
        val bcXz = floatArrayOf(w[wrI].x - w[elI].x, w[wrI].z - w[elI].z)
        val baYz = floatArrayOf(w[shI].y - w[elI].y, w[shI].z - w[elI].z)
        val bcYz = floatArrayOf(w[wrI].y - w[elI].y, w[wrI].z - w[elI].z)
        val baXyz = floatArrayOf(w[shI].x - w[elI].x, w[shI].y - w[elI].y, w[shI].z - w[elI].z)
        val bcXyz = floatArrayOf(w[wrI].x - w[elI].x, w[wrI].y - w[elI].y, w[wrI].z - w[elI].z)

        val ang2dXy = angleDeg2d(baXy, bcXy) / 180f
        val ang3dXyz = angleDeg3d(baXyz, bcXyz) / 180f
        val angXz = angleDeg2d(baXz, bcXz) / 180f
        val angYz = angleDeg2d(baYz, bcYz) / 180f

        val uaLen2dRaw = sqrt(uaDx2d * uaDx2d + uaDy2d * uaDy2d)
        val faLen2dRaw = sqrt(faDx2d * faDx2d + faDy2d * faDy2d)
        val uaLen2d = uaLen2dRaw / torsoLen2d
        val faLen2d = faLen2dRaw / torsoLen2d
        val uaLen3d = ua3dLen / torsoLen2d
        val faLen3d = fa3dLen / torsoLen2d

        val dzUa = abs(w[elI].z - w[shI].z)
        val dzFa = abs(w[wrI].z - w[elI].z)
        val dzShareUa = dzUa / ua3dLen
        val dzShareFa = dzFa / fa3dLen
        val lenRatio2d = faLen2dRaw / (uaLen2dRaw + EPS)
        val signUa = if (w[elI].z - w[shI].z >= 0) 1f else -1f
        val signFa = if (w[wrI].z - w[elI].z >= 0) 1f else -1f
        val dzSignDiff = signUa * signFa

        val shoulderW2d = abs(n[BodyLandmarks.LEFT_SHOULDER].x - n[BodyLandmarks.RIGHT_SHOULDER].x)
        val shoulderW3d = dist3dLm(w[BodyLandmarks.LEFT_SHOULDER], w[BodyLandmarks.RIGHT_SHOULDER]) + EPS
        val hipW2d = abs(n[BodyLandmarks.LEFT_HIP].x - n[BodyLandmarks.RIGHT_HIP].x)

        val facingRatio = min(shoulderW2d / shoulderW3d, 1.5f)
        val shoulderWNorm = shoulderW2d / torsoLen2d
        val hipWNorm = hipW2d / torsoLen2d
        val torsoDxNorm = torsoVecX / torsoLen2d
        val torsoDyNorm = torsoVecY / torsoLen2d
        val shoulderDepthAsym = mirror * ((w[oppShI].z - w[shI].z) / shoulderW3d)

        val seDx2d = mirror * (uaDx2d / torsoLen2d)
        val seDy2d = uaDy2d / torsoLen2d
        val ewDx2d = mirror * (faDx2d / torsoLen2d)
        val ewDy2d = faDy2d / torsoLen2d
        val swDx2dF = mirror * (swDx2d / torsoLen2d)
        val swDy2dF = swDy2d / torsoLen2d

        // Body-frame arm vectors
        val bodyAxes = computeBodyAxes(w) ?: return null
        val uaBodyRaw = toBodyFrame(floatArrayOf(ua3dX, ua3dY, ua3dZ), bodyAxes)
        val faBodyRaw = toBodyFrame(floatArrayOf(fa3dX, fa3dY, fa3dZ), bodyAxes)
        val uaBody = safeUnit(uaBodyRaw)
        val faBody = safeUnit(faBodyRaw)
        uaBody[0] *= mirror
        faBody[0] *= mirror

        val angBodyYz = angleDeg2d(
            floatArrayOf(uaBody[1], uaBody[2]),
            floatArrayOf(faBody[1], faBody[2])
        ) / 180f
        val angBodyXy = angleDeg2d(
            floatArrayOf(uaBody[0], uaBody[1]),
            floatArrayOf(faBody[0], faBody[1])
        ) / 180f

        val visShoulder = n[shI].visibility
        val visElbow = n[elI].visibility
        val visWrist = n[wrI].visibility
        val presShoulder = n[shI].presence
        val presElbow = n[elI].presence
        val presWrist = n[wrI].presence

        return floatArrayOf(
            ang2dXy, ang3dXyz, angXz, angYz,
            uaLen2d, faLen2d, uaLen3d, faLen3d,
            dzShareUa, dzShareFa, lenRatio2d, dzSignDiff,
            facingRatio, shoulderWNorm, hipWNorm,
            torsoDxNorm, torsoDyNorm, shoulderDepthAsym,
            seDx2d, seDy2d, ewDx2d, ewDy2d,
            swDx2dF, swDy2dF,
            uaBody[0], uaBody[1], uaBody[2],
            faBody[0], faBody[1], faBody[2],
            angBodyYz, angBodyXy,
            visShoulder, visElbow, visWrist,
            presShoulder, presElbow, presWrist,
        )
    }

    private fun mid3d(lm: List<SmoothedLandmark>, i1: Int, i2: Int): FloatArray {
        return floatArrayOf(
            (lm[i1].x + lm[i2].x) / 2f,
            (lm[i1].y + lm[i2].y) / 2f,
            (lm[i1].z + lm[i2].z) / 2f,
        )
    }

    private fun dist3d(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun dist3dLm(a: SmoothedLandmark, b: SmoothedLandmark): Float {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun safeUnit(v: FloatArray): FloatArray {
        val norm = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (norm < EPS) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(v[0] / norm, v[1] / norm, v[2] / norm)
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

    /**
     * Body-frame axes: X = left_sh→right_sh (shoulder line),
     * Y = hip→shoulder (spine up), Z = cross(X, Y) (forward).
     * Returns 3×3 row-major: axes[0]=X, axes[1]=Y, axes[2]=Z.
     */
    private fun computeBodyAxes(w: List<SmoothedLandmark>): Array<FloatArray>? {
        val lsh = floatArrayOf(w[BodyLandmarks.LEFT_SHOULDER].x, w[BodyLandmarks.LEFT_SHOULDER].y, w[BodyLandmarks.LEFT_SHOULDER].z)
        val rsh = floatArrayOf(w[BodyLandmarks.RIGHT_SHOULDER].x, w[BodyLandmarks.RIGHT_SHOULDER].y, w[BodyLandmarks.RIGHT_SHOULDER].z)
        val lhip = floatArrayOf(w[BodyLandmarks.LEFT_HIP].x, w[BodyLandmarks.LEFT_HIP].y, w[BodyLandmarks.LEFT_HIP].z)
        val rhip = floatArrayOf(w[BodyLandmarks.RIGHT_HIP].x, w[BodyLandmarks.RIGHT_HIP].y, w[BodyLandmarks.RIGHT_HIP].z)

        val xAxis = safeUnit(floatArrayOf(lsh[0] - rsh[0], lsh[1] - rsh[1], lsh[2] - rsh[2]))
        val shMid = floatArrayOf((lsh[0] + rsh[0]) / 2f, (lsh[1] + rsh[1]) / 2f, (lsh[2] + rsh[2]) / 2f)
        val hipMid = floatArrayOf((lhip[0] + rhip[0]) / 2f, (lhip[1] + rhip[1]) / 2f, (lhip[2] + rhip[2]) / 2f)
        val ySeed = safeUnit(floatArrayOf(shMid[0] - hipMid[0], shMid[1] - hipMid[1], shMid[2] - hipMid[2]))

        val zAxis = safeUnit(cross(xAxis, ySeed))
        val zNorm = sqrt(zAxis[0] * zAxis[0] + zAxis[1] * zAxis[1] + zAxis[2] * zAxis[2])
        if (zNorm < EPS) return null

        val yAxis = safeUnit(cross(zAxis, xAxis))
        return arrayOf(xAxis, yAxis, zAxis)
    }

    private fun toBodyFrame(v: FloatArray, axes: Array<FloatArray>): FloatArray {
        return floatArrayOf(
            v[0] * axes[0][0] + v[1] * axes[0][1] + v[2] * axes[0][2],
            v[0] * axes[1][0] + v[1] * axes[1][1] + v[2] * axes[1][2],
            v[0] * axes[2][0] + v[1] * axes[2][1] + v[2] * axes[2][2],
        )
    }

    private fun cross(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )
    }
}
