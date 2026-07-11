package com.movit.core.training.geometry

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.math.abs
import kotlin.math.sqrt

/** Elbow-only 3D angle corrector (ported from legacy). */
class ElbowAngleEstimator {
    companion object {
        private const val LEFT = 0
        private const val RIGHT = 1
        private const val DZ_SMOOTH_ALPHA = 0.18f
        private const val OUTPUT_SMOOTH = 0.25
        private const val STRAIGHT_ARM_GATE = 150.0
        private const val STRAIGHT_ARM_BOOST = 0.50
        private const val LOW_DEPTH = 0.15f
        private const val MID_DEPTH = 0.40f
        private const val HIGH_DEPTH = 0.60f
        private const val INFLATION_GATE = 12.0
        private const val CORRECTION_SCALE = 0.55f
        private const val MAX_CORRECTION = 0.25f
        private const val SIDE_GATE_HIGH = 0.85f
        private const val SIDE_GATE_LOW = 0.40f
        private const val HOLD_TIMEOUT_MS = 500L
        private const val MAX_ELBOW_ANGLE = 180.0
    }

    private val smUaDz = floatArrayOf(0f, 0f)
    private val smFaDz = floatArrayOf(0f, 0f)
    private val smoothOut = doubleArrayOf(Double.NaN, Double.NaN)
    private val lastStable = doubleArrayOf(Double.NaN, Double.NaN)
    private val lastStableTs = longArrayOf(0L, 0L)

    /** Latest per-side diagnostics after [correct]; index 0=left, 1=right. */
    var lastDiagnostics: Array<ElbowCorrectionDiagnostics?> = arrayOf(null, null)
        private set

    fun correct(
        angles: JointAngles,
        worldLandmarks: List<Landmark>,
        normLandmarks: List<Landmark>,
        timestampMs: Long,
        aspectYScale: Float = 1f,
        collectDiagnostics: Boolean = false,
    ): JointAngles {
        if (worldLandmarks.size < 33 || normLandmarks.size < 33) return angles
        val facing = computeFacingRatio(worldLandmarks)
        val left = estimateSide(
            LEFT, facing, worldLandmarks, normLandmarks, timestampMs,
            PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_WRIST,
            aspectYScale, collectDiagnostics,
        ) ?: angles.leftElbow
        val right = estimateSide(
            RIGHT, facing, worldLandmarks, normLandmarks, timestampMs,
            PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_WRIST,
            aspectYScale, collectDiagnostics,
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

    private fun computeFacingRatio(w: List<Landmark>): Float {
        val ls = w[PoseLandmarkIndices.LEFT_SHOULDER]
        val rs = w[PoseLandmarkIndices.RIGHT_SHOULDER]
        if (!ls.isVisible(0.3f) || !rs.isVisible(0.3f)) return 0.5f
        val xy = dist2D(ls.x, ls.y, rs.x, rs.y)
        val xyz = dist3D(ls.x, ls.y, ls.z, rs.x, rs.y, rs.z)
        return if (xyz > 0.01f) (xy / xyz).coerceIn(0f, 1f) else 0.5f
    }

    private fun computeSideStrength(facingRatio: Float): Float =
        ((SIDE_GATE_HIGH - facingRatio) / (SIDE_GATE_HIGH - SIDE_GATE_LOW)).coerceIn(0f, 1f)

    private fun estimateSide(
        side: Int,
        facingRatio: Float,
        world: List<Landmark>,
        norm: List<Landmark>,
        ts: Long,
        sIdx: Int,
        eIdx: Int,
        wIdx: Int,
        aspectYScale: Float,
        collectDiagnostics: Boolean,
    ): Double? {
        val ws = world[sIdx]; val we = world[eIdx]; val ww = world[wIdx]
        if (!ws.isVisible(0.5f) || !we.isVisible(0.5f) || !ww.isVisible(0.5f)) return holdOrNull(side, ts)
        val ns = norm[sIdx]; val ne = norm[eIdx]; val nw = norm[wIdx]
        // F3: aspect-correct normalized 2D before ang2D.
        val ang2D = computeAngle(
            ns.x, ns.y * aspectYScale, 0f,
            ne.x, ne.y * aspectYScale, 0f,
            nw.x, nw.y * aspectYScale, 0f,
        ) ?: return holdOrNull(side, ts)
        val ang3D = computeAngle(ws.x, ws.y, ws.z, we.x, we.y, we.z, ww.x, ww.y, ww.z)
            ?: return holdOrNull(side, ts)
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
        val output: Double
        val corrPct: Float
        var isLowConfidence = false
        val strategy: ElbowCorrectionStrategy
        when {
            ang2D > STRAIGHT_ARM_GATE -> {
                output = ang2D + (180.0 - ang2D) * STRAIGHT_ARM_BOOST
                corrPct = 0f
                strategy = ElbowCorrectionStrategy.STRAIGHT
            }
            ang3D <= ang2D + INFLATION_GATE -> {
                output = ang3D
                corrPct = 0f
                strategy = ElbowCorrectionStrategy.TRUST_3D
            }
            maxDz < LOW_DEPTH -> {
                output = ang2D
                corrPct = 0f
                strategy = ElbowCorrectionStrategy.TRUST_2D
            }
            maxDz < MID_DEPTH -> {
                val depthFactor = (maxDz - LOW_DEPTH) / (MID_DEPTH - LOW_DEPTH)
                corrPct = (depthFactor * CORRECTION_SCALE * sideStrength).coerceAtMost(MAX_CORRECTION * 0.5f)
                output = ang2D * (1.0 - corrPct)
                strategy = ElbowCorrectionStrategy.MILD_DOWN
            }
            else -> {
                val depthFactor = ((maxDz - MID_DEPTH) / (1.0f - MID_DEPTH)).coerceIn(0f, 1f)
                corrPct = ((0.5f + depthFactor * 0.5f) * CORRECTION_SCALE * sideStrength).coerceAtMost(MAX_CORRECTION)
                output = ang2D * (1.0 - corrPct)
                isLowConfidence = maxDz > HIGH_DEPTH
                strategy = if (isLowConfidence) {
                    ElbowCorrectionStrategy.LOW_CONF
                } else {
                    ElbowCorrectionStrategy.DEEP_DOWN
                }
            }
        }
        val clamped = output.coerceIn(0.0, MAX_ELBOW_ANGLE)
        if (isLowConfidence && !lastStable[side].isNaN() && ts - lastStableTs[side] < HOLD_TIMEOUT_MS) {
            smoothOut[side] = lastStable[side]
            if (collectDiagnostics) {
                lastDiagnostics[side] = ElbowCorrectionDiagnostics(
                    strategy = ElbowCorrectionStrategy.HOLD,
                    facingRatio = facingRatio,
                    screenAngle = ang2D,
                    worldAngle = ang3D,
                    maxDzShare = maxDz,
                    dzImbalance = faDz - uaDz,
                    correctionPct = corrPct,
                    outputAngle = lastStable[side],
                    isHolding = true,
                    uaDzShare = uaDz,
                    faDzShare = faDz,
                )
            }
            return lastStable[side]
        }
        val smoothed = if (smoothOut[side].isNaN()) clamped
        else smoothOut[side] + OUTPUT_SMOOTH * (clamped - smoothOut[side])
        smoothOut[side] = smoothed
        if (!isLowConfidence) {
            lastStable[side] = smoothed
            lastStableTs[side] = ts
        }
        if (collectDiagnostics) {
            lastDiagnostics[side] = ElbowCorrectionDiagnostics(
                strategy = strategy,
                facingRatio = facingRatio,
                screenAngle = ang2D,
                worldAngle = ang3D,
                maxDzShare = maxDz,
                dzImbalance = faDz - uaDz,
                correctionPct = corrPct,
                outputAngle = smoothed,
                isHolding = false,
                uaDzShare = uaDz,
                faDzShare = faDz,
            )
        }
        return smoothed
    }

    private fun holdOrNull(side: Int, ts: Long): Double? {
        if (lastStable[side].isNaN()) return null
        return if (ts - lastStableTs[side] < HOLD_TIMEOUT_MS) lastStable[side] else null
    }

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
        cx: Float, cy: Float, cz: Float,
    ): Double? {
        val baX = ax - bx; val baY = ay - by; val baZ = az - bz
        val bcX = cx - bx; val bcY = cy - by; val bcZ = cz - bz
        val dot = baX * bcX + baY * bcY + baZ * bcZ
        val magBA = sqrt(baX * baX + baY * baY + baZ * baZ)
        val magBC = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)
        if (magBA < 1e-7f || magBC < 1e-7f) return null
        val cos = (dot / (magBA * magBC)).coerceIn(-1f, 1f)
        return kotlin.math.acos(cos.toDouble()) * 180.0 / kotlin.math.PI
    }
}
