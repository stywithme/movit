package com.trainingvalidator.poc.analysis

import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Elbow-only angle corrector — zero calibration, stateless beyond EMA smoothers.
 *
 * Uses the "depth-imbalance" between upper-arm and forearm to decide
 * the correction DIRECTION:
 *
 *  • forearm dzShare ≤ upper-arm dzShare  →  2D overestimates  →  correct DOWN
 *  • forearm dzShare >  upper-arm dzShare  →  2D under-estimates  →  blend TOWARD 3D
 *
 * Additionally, near-straight arms (>150°) receive a gentle boost toward 180°
 * to compensate for MediaPipe's tendency to never report fully straight joints.
 */
class ElbowAngleEstimator {

    companion object {
        private const val LEFT = 0
        private const val RIGHT = 1

        private const val DZ_SMOOTH_ALPHA = 0.08f
        private const val CORRECTION_SCALE = 0.80f
        private const val MAX_CORRECTION = 0.35f
        private const val BLEND_DIVISOR = 0.40f
        private const val MAX_BLEND = 0.50f
        private const val OUTPUT_SMOOTH = 0.30
        private const val HOLD_TIMEOUT_MS = 400L
        private const val MAX_ELBOW_ANGLE = 180.0
        private const val INFLATION_GATE = 5.0
        private const val STRAIGHT_ARM_GATE = 150.0
        private const val STRAIGHT_ARM_BOOST = 0.50
    }

    private val smUaDz    = floatArrayOf(0f, 0f)
    private val smFaDz    = floatArrayOf(0f, 0f)
    private val smoothOut = doubleArrayOf(Double.NaN, Double.NaN)

    private val lastStable   = doubleArrayOf(Double.NaN, Double.NaN)
    private val lastStableTs = longArrayOf(0L, 0L)

    var lastDiagnostics: Array<ElbowDiagnostics?> = arrayOf(null, null)
        private set

    // ========================== public API ==========================

    fun correct(
        angles: JointAngles,
        worldLandmarks: List<SmoothedLandmark>,
        normLandmarks: List<SmoothedLandmark>,
        timestampMs: Long
    ): JointAngles {
        val req = maxOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_ELBOW, BodyLandmarks.RIGHT_ELBOW,
            BodyLandmarks.LEFT_WRIST, BodyLandmarks.RIGHT_WRIST,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP
        ) + 1
        if (worldLandmarks.size < req || normLandmarks.size < req) return angles

        val facing = computeFacingRatio(worldLandmarks)

        val left = estimateSide(
            LEFT, facing, worldLandmarks, normLandmarks, timestampMs,
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.LEFT_ELBOW, BodyLandmarks.LEFT_WRIST
        ) ?: angles.leftElbow

        val right = estimateSide(
            RIGHT, facing, worldLandmarks, normLandmarks, timestampMs,
            BodyLandmarks.RIGHT_SHOULDER, BodyLandmarks.RIGHT_ELBOW, BodyLandmarks.RIGHT_WRIST
        ) ?: angles.rightElbow

        return angles.copy(leftElbow = left, rightElbow = right)
    }

    // ========================== internals ==========================

    private fun computeFacingRatio(w: List<SmoothedLandmark>): Float {
        val ls = w[BodyLandmarks.LEFT_SHOULDER]; val rs = w[BodyLandmarks.RIGHT_SHOULDER]
        if (!ls.isVisible(0.3f) || !rs.isVisible(0.3f)) return 0.5f
        val xy  = dist2D(ls.x, ls.y, rs.x, rs.y)
        val xyz = dist3D(ls.x, ls.y, ls.z, rs.x, rs.y, rs.z)
        return if (xyz > 0.01f) (xy / xyz).coerceIn(0f, 1f) else 0.5f
    }

    private fun estimateSide(
        side: Int, facingRatio: Float,
        world: List<SmoothedLandmark>, norm: List<SmoothedLandmark>,
        ts: Long, sIdx: Int, eIdx: Int, wIdx: Int
    ): Double? {
        val ws = world[sIdx]; val we = world[eIdx]; val ww = world[wIdx]
        if (!ws.isVisible(0.5f) || !we.isVisible(0.5f) || !ww.isVisible(0.5f))
            return holdOrNull(side, ts)

        val ns = norm[sIdx]; val ne = norm[eIdx]; val nw = norm[wIdx]

        val ang2D = computeAngle(ns.x, ns.y, 0f, ne.x, ne.y, 0f, nw.x, nw.y, 0f)
        val ang3D = computeAngle(ws.x, ws.y, ws.z, we.x, we.y, we.z, ww.x, ww.y, ww.z)

        // ---- per-segment depth ratios (smoothed) ----
        val uaLen = dist3D(ws.x, ws.y, ws.z, we.x, we.y, we.z)
        val uaDzRaw = if (uaLen > 0.01f) abs(we.z - ws.z) / uaLen else 0f
        val faLen = dist3D(we.x, we.y, we.z, ww.x, ww.y, ww.z)
        val faDzRaw = if (faLen > 0.01f) abs(ww.z - we.z) / faLen else 0f

        smUaDz[side] += DZ_SMOOTH_ALPHA * (uaDzRaw - smUaDz[side])
        smFaDz[side] += DZ_SMOOTH_ALPHA * (faDzRaw - smFaDz[side])
        val uaDz = smUaDz[side]
        val faDz = smFaDz[side]
        val maxDz = maxOf(uaDz, faDz)
        val dzImbalance = faDz - uaDz

        // ---- decide correction strategy ----
        val output: Double
        val corrPct: Float
        val rawMax = maxOf(ang2D, ang3D)

        when {
            // 1) Near-straight arm → boost toward 180°
            rawMax > STRAIGHT_ARM_GATE -> {
                output = rawMax + (180.0 - rawMax) * STRAIGHT_ARM_BOOST
                corrPct = 0f
            }
            // 2) Z is NOT inflating → raw 3D is fine
            ang3D <= ang2D + INFLATION_GATE -> {
                output = ang3D
                corrPct = 0f
            }
            // 3) Z inflates AND upper arm has more/equal depth → 2D overestimates → reduce
            //    BUT only from side views. When facing the camera, 2D is accurate
            //    and reducing it makes things worse.
            dzImbalance <= 0f -> {
                val sideStrength = ((0.90f - facingRatio) / 0.45f).coerceIn(0f, 1f)
                val bent = (1.0 - ang2D / 180.0).coerceIn(0.0, 1.0).toFloat()
                corrPct = (maxDz * CORRECTION_SCALE * bent * sideStrength).coerceAtMost(MAX_CORRECTION)
                output = ang2D * (1.0 - corrPct)
            }
            // 4) Z inflates AND forearm has more depth → 2D under-estimates → blend toward 3D
            else -> {
                val t = (dzImbalance / BLEND_DIVISOR).coerceIn(0f, MAX_BLEND)
                output = ang2D + (ang3D - ang2D) * t
                corrPct = -t
            }
        }

        val clamped = output.coerceIn(0.0, MAX_ELBOW_ANGLE)

        val smoothed = if (smoothOut[side].isNaN()) clamped
        else smoothOut[side] + OUTPUT_SMOOTH * (clamped - smoothOut[side])
        smoothOut[side] = smoothed

        lastStable[side] = smoothed
        lastStableTs[side] = ts

        lastDiagnostics[side] = ElbowDiagnostics(
            facingRatio  = facingRatio,
            screenAngle  = ang2D,
            worldAngle   = ang3D,
            maxDzShare   = maxDz,
            dzImbalance  = dzImbalance,
            correctionPct = corrPct,
            outputAngle  = smoothed,
            isHolding    = false,
            uaDzShare    = uaDz,
            faDzShare    = faDz
        )
        return smoothed
    }

    private fun holdOrNull(side: Int, ts: Long): Double? {
        if (lastStable[side].isNaN()) return null
        return if (ts - lastStableTs[side] < HOLD_TIMEOUT_MS) lastStable[side] else null
    }

    // ========================== math ==========================

    private fun dist2D(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist3D(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2; val dz = z1 - z2
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun computeAngle(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Double {
        val baX = ax - bx; val baY = ay - by; val baZ = az - bz
        val bcX = cx - bx; val bcY = cy - by; val bcZ = cz - bz
        val dot = baX * bcX + baY * bcY + baZ * bcZ
        val magBA = sqrt(baX * baX + baY * baY + baZ * baZ)
        val magBC = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)
        if (magBA < 1e-7f || magBC < 1e-7f) return 0.0
        val cos = (dot / (magBA * magBC)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos.toDouble()))
    }
}

data class ElbowDiagnostics(
    val facingRatio: Float,
    val screenAngle: Double,
    val worldAngle: Double,
    val maxDzShare: Float,
    val dzImbalance: Float,
    val correctionPct: Float,
    val outputAngle: Double?,
    val isHolding: Boolean,
    val uaDzShare: Float,
    val faDzShare: Float
)
