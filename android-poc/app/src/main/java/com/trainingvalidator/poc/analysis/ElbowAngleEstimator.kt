package com.trainingvalidator.poc.analysis

import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Elbow-only angle corrector — confidence-based, stateless beyond EMA smoothers.
 *
 * Core principle: maxDzShare (the larger depth-ratio of the two arm segments)
 * is treated as a CONFIDENCE indicator, not a correction-direction selector.
 *
 * Strategy (evaluated top to bottom, first match wins):
 *
 *  1. Straight arm (2D > 150°)      → boost toward 180°
 *  2. 3D ≤ 2D + gate               → depth resolved correctly → trust 3D
 *  3. 3D inflated, low depth        → foreshortening negligible → trust 2D
 *  4. 3D inflated, moderate depth   → mild downward correction from 2D
 *  5. 3D inflated, high depth       → both unreliable → hold last stable
 *
 * All downward corrections are gated by sideStrength (0 frontal → 1 side)
 * because foreshortening is minimal when facing the camera.
 */
class ElbowAngleEstimator {

    companion object {
        private const val LEFT = 0
        private const val RIGHT = 1

        // --- Smoothing ---
        private const val DZ_SMOOTH_ALPHA = 0.18f
        private const val OUTPUT_SMOOTH = 0.25

        // --- Straight arm ---
        private const val STRAIGHT_ARM_GATE = 150.0
        private const val STRAIGHT_ARM_BOOST = 0.50

        // --- Depth confidence thresholds ---
        private const val LOW_DEPTH = 0.15f
        private const val MID_DEPTH = 0.40f
        private const val HIGH_DEPTH = 0.60f

        // --- Correction ---
        private const val INFLATION_GATE = 12.0
        private const val CORRECTION_SCALE = 0.55f
        private const val MAX_CORRECTION = 0.25f

        // --- Side-view gating ---
        private const val SIDE_GATE_HIGH = 0.85f
        private const val SIDE_GATE_LOW = 0.40f

        // --- Hold ---
        private const val HOLD_TIMEOUT_MS = 500L
        private const val MAX_ELBOW_ANGLE = 180.0
    }

    private val smUaDz = floatArrayOf(0f, 0f)
    private val smFaDz = floatArrayOf(0f, 0f)
    private val smoothOut = doubleArrayOf(Double.NaN, Double.NaN)

    private val lastStable = doubleArrayOf(Double.NaN, Double.NaN)
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

    fun reset() {
        for (i in 0..1) {
            smUaDz[i] = 0f
            smFaDz[i] = 0f
            smoothOut[i] = Double.NaN
            lastStable[i] = Double.NaN
            lastStableTs[i] = 0L
            lastDiagnostics[i] = null
        }
    }

    // ========================== internals ==========================

    private fun computeFacingRatio(w: List<SmoothedLandmark>): Float {
        val ls = w[BodyLandmarks.LEFT_SHOULDER]; val rs = w[BodyLandmarks.RIGHT_SHOULDER]
        if (!ls.isVisible(0.3f) || !rs.isVisible(0.3f)) return 0.5f
        val xy  = dist2D(ls.x, ls.y, rs.x, rs.y)
        val xyz = dist3D(ls.x, ls.y, ls.z, rs.x, rs.y, rs.z)
        return if (xyz > 0.01f) (xy / xyz).coerceIn(0f, 1f) else 0.5f
    }

    private fun computeSideStrength(facingRatio: Float): Float {
        return ((SIDE_GATE_HIGH - facingRatio) / (SIDE_GATE_HIGH - SIDE_GATE_LOW))
            .coerceIn(0f, 1f)
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

        val sideStrength = computeSideStrength(facingRatio)

        // ---- confidence-based correction ----
        val output: Double
        val corrPct: Float
        var isLowConfidence = false
        val strategy: String

        when {
            // 1) Straight arm — use ang2D ONLY to prevent false positive from inflated ang3D
            ang2D > STRAIGHT_ARM_GATE -> {
                output = ang2D + (180.0 - ang2D) * STRAIGHT_ARM_BOOST
                corrPct = 0f
                strategy = "STRAIGHT"
            }

            // 2) 3D is not inflated beyond 2D → 3D resolved depth correctly
            ang3D <= ang2D + INFLATION_GATE -> {
                output = ang3D
                corrPct = 0f
                strategy = "TRUST_3D"
            }

            // 3-5) 3D is inflated — use depth confidence to decide
            maxDz < LOW_DEPTH -> {
                output = ang2D
                corrPct = 0f
                strategy = "TRUST_2D"
            }

            maxDz < MID_DEPTH -> {
                val depthFactor = (maxDz - LOW_DEPTH) / (MID_DEPTH - LOW_DEPTH)
                corrPct = (depthFactor * CORRECTION_SCALE * sideStrength)
                    .coerceAtMost(MAX_CORRECTION * 0.5f)
                output = ang2D * (1.0 - corrPct)
                strategy = "MILD_DOWN"
            }

            else -> {
                val depthFactor = ((maxDz - MID_DEPTH) / (1.0f - MID_DEPTH))
                    .coerceIn(0f, 1f)
                corrPct = ((0.5f + depthFactor * 0.5f) * CORRECTION_SCALE * sideStrength)
                    .coerceAtMost(MAX_CORRECTION)
                output = ang2D * (1.0 - corrPct)
                isLowConfidence = maxDz > HIGH_DEPTH
                strategy = if (isLowConfidence) "LOW_CONF" else "DEEP_DOWN"
            }
        }

        val clamped = output.coerceIn(0.0, MAX_ELBOW_ANGLE)

        // Hold last stable during low-confidence periods
        if (isLowConfidence && !lastStable[side].isNaN() &&
            ts - lastStableTs[side] < HOLD_TIMEOUT_MS
        ) {
            smoothOut[side] = lastStable[side]

            lastDiagnostics[side] = ElbowDiagnostics(
                facingRatio   = facingRatio,
                screenAngle   = ang2D,
                worldAngle    = ang3D,
                maxDzShare    = maxDz,
                dzImbalance   = faDz - uaDz,
                correctionPct = corrPct,
                outputAngle   = lastStable[side],
                isHolding     = true,
                uaDzShare     = uaDz,
                faDzShare     = faDz,
                strategy      = "HOLD"
            )
            return lastStable[side]
        }

        val smoothed = if (smoothOut[side].isNaN()) clamped
        else smoothOut[side] + OUTPUT_SMOOTH * (clamped - smoothOut[side])
        smoothOut[side] = smoothed

        if (!isLowConfidence) {
            lastStable[side] = smoothed
            lastStableTs[side] = ts
        }

        lastDiagnostics[side] = ElbowDiagnostics(
            facingRatio   = facingRatio,
            screenAngle   = ang2D,
            worldAngle    = ang3D,
            maxDzShare    = maxDz,
            dzImbalance   = faDz - uaDz,
            correctionPct = corrPct,
            outputAngle   = smoothed,
            isHolding     = false,
            uaDzShare     = uaDz,
            faDzShare     = faDz,
            strategy      = strategy
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
    val faDzShare: Float,
    val strategy: String = ""
)
