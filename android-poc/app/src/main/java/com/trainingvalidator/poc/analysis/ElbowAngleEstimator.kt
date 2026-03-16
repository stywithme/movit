package com.trainingvalidator.poc.analysis

import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Specialized elbow angle estimator that compensates for MediaPipe's
 * unreliable wrist Z depth by combining body-plane projection with
 * segment-length constrained reconstruction.
 *
 * Applied ONLY to left_elbow and right_elbow. All other joints pass through unchanged.
 *
 * Pipeline:
 *   Stage 1 → Body orientation detection (frontal vs side view)
 *   Stage 2 → Body-plane projection (angle in body coordinate system)
 *   Stage 3 → Segment-length constrained Z reconstruction
 *   Stage 4 → Multi-signal fusion (weighted by per-stage confidence)
 *   Stage 5 → Confidence gating + hold-last-stable
 */
class ElbowAngleEstimator {

    companion object {
        private const val LEFT = 0
        private const val RIGHT = 1

        private const val CALIBRATION_DECAY = 0.998f
        private const val MIN_CONFIDENCE = 0.25f
        private const val HOLD_TIMEOUT_MS = 400L
        private const val MAX_ELBOW_ANGLE = 180.0
        private const val MIN_SHOULDER_WORLD_LEN = 0.04f
        private const val FORESHORTENING_THRESHOLD = 0.97f
        private const val WARM_UP_FRAMES = 30
    }

    // --- Calibration state ---
    private var maxShoulderScreenWidth = 0f
    private val maxUpperArmScreenLen = floatArrayOf(0f, 0f)
    private val maxForearmScreenLen = floatArrayOf(0f, 0f)
    private var calibrationFrameCount = 0

    // --- Hold-last-stable state ---
    private val lastStableAngle = doubleArrayOf(Double.NaN, Double.NaN)
    private val lastStableTimestamp = longArrayOf(0L, 0L)

    // --- Reusable scratch arrays (avoid per-frame allocation) ---
    private val bodyLateral = FloatArray(3)
    private val bodyUp = FloatArray(3)
    private val bodyForward = FloatArray(3)
    private val tmpVec = FloatArray(3)

    /**
     * Correct elbow angles in [angles] using the specialized pipeline.
     * Returns a new JointAngles with only elbows replaced; everything else unchanged.
     */
    fun correct(
        angles: JointAngles,
        worldLandmarks: List<SmoothedLandmark>,
        normLandmarks: List<SmoothedLandmark>,
        timestampMs: Long
    ): JointAngles {
        val requiredSize = maxOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_ELBOW, BodyLandmarks.RIGHT_ELBOW,
            BodyLandmarks.LEFT_WRIST, BodyLandmarks.RIGHT_WRIST,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP
        ) + 1

        if (worldLandmarks.size < requiredSize || normLandmarks.size < requiredSize) {
            return angles
        }

        calibrationFrameCount++

        // Stage 1: body orientation
        val facingRatio = computeFacingRatio(normLandmarks)

        // Stage 2 prerequisite: build body coordinate axes (shared for both arms)
        val torsoReady = buildBodyAxes(worldLandmarks)

        val correctedLeft = estimateSide(
            LEFT, facingRatio, torsoReady,
            worldLandmarks, normLandmarks, timestampMs,
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.LEFT_ELBOW, BodyLandmarks.LEFT_WRIST
        ) ?: angles.leftElbow

        val correctedRight = estimateSide(
            RIGHT, facingRatio, torsoReady,
            worldLandmarks, normLandmarks, timestampMs,
            BodyLandmarks.RIGHT_SHOULDER, BodyLandmarks.RIGHT_ELBOW, BodyLandmarks.RIGHT_WRIST
        ) ?: angles.rightElbow

        return angles.copy(
            leftElbow = correctedLeft,
            rightElbow = correctedRight
        )
    }

    /** Latest diagnostics for debug display. */
    var lastDiagnostics: Array<ElbowDiagnostics?> = arrayOf(null, null)
        private set

    // ==================== Stage 1: Body Orientation ====================

    private fun computeFacingRatio(norm: List<SmoothedLandmark>): Float {
        val ls = norm[BodyLandmarks.LEFT_SHOULDER]
        val rs = norm[BodyLandmarks.RIGHT_SHOULDER]
        if (!ls.isVisible(0.5f) || !rs.isVisible(0.5f)) return 0.5f

        val width = abs(ls.x - rs.x)
        maxShoulderScreenWidth = maxOf(width, maxShoulderScreenWidth * CALIBRATION_DECAY)

        return if (maxShoulderScreenWidth > 0.01f)
            (width / maxShoulderScreenWidth).coerceIn(0f, 1f)
        else 0.5f
    }

    // ==================== Stage 2: Body-Plane Projection ====================

    /**
     * Build body-coordinate axes from torso landmarks.
     * Returns true if axes were built successfully.
     */
    private fun buildBodyAxes(world: List<SmoothedLandmark>): Boolean {
        val ls = world[BodyLandmarks.LEFT_SHOULDER]
        val rs = world[BodyLandmarks.RIGHT_SHOULDER]
        val lh = world[BodyLandmarks.LEFT_HIP]
        val rh = world[BodyLandmarks.RIGHT_HIP]

        if (!ls.isVisible(0.3f) || !rs.isVisible(0.3f) ||
            !lh.isVisible(0.3f) || !rh.isVisible(0.3f)
        ) return false

        // Lateral axis: left shoulder → right shoulder
        val latX = rs.x - ls.x
        val latY = rs.y - ls.y
        val latZ = rs.z - ls.z
        val latLen = len3(latX, latY, latZ)
        if (latLen < 0.01f) return false
        bodyLateral[0] = latX / latLen
        bodyLateral[1] = latY / latLen
        bodyLateral[2] = latZ / latLen

        // Spine direction: hip midpoint → shoulder midpoint
        val smX = (ls.x + rs.x) * 0.5f; val smY = (ls.y + rs.y) * 0.5f; val smZ = (ls.z + rs.z) * 0.5f
        val hmX = (lh.x + rh.x) * 0.5f; val hmY = (lh.y + rh.y) * 0.5f; val hmZ = (lh.z + rh.z) * 0.5f
        val spX = smX - hmX; val spY = smY - hmY; val spZ = smZ - hmZ
        val spLen = len3(spX, spY, spZ)
        if (spLen < 0.01f) return false

        // Forward = lateral × spine
        cross(bodyLateral[0], bodyLateral[1], bodyLateral[2],
            spX / spLen, spY / spLen, spZ / spLen, bodyForward)
        val fwdLen = len3(bodyForward[0], bodyForward[1], bodyForward[2])
        if (fwdLen < 0.001f) return false
        bodyForward[0] /= fwdLen; bodyForward[1] /= fwdLen; bodyForward[2] /= fwdLen

        // Up = forward × lateral (ensures orthogonal right-handed system)
        cross(bodyForward[0], bodyForward[1], bodyForward[2],
            bodyLateral[0], bodyLateral[1], bodyLateral[2], bodyUp)
        val upLen = len3(bodyUp[0], bodyUp[1], bodyUp[2])
        if (upLen < 0.001f) return false
        bodyUp[0] /= upLen; bodyUp[1] /= upLen; bodyUp[2] /= upLen

        return true
    }

    private fun bodyPlaneAngle(
        world: List<SmoothedLandmark>,
        shoulderIdx: Int, elbowIdx: Int, wristIdx: Int
    ): Double {
        val lh = world[BodyLandmarks.LEFT_HIP]
        val rh = world[BodyLandmarks.RIGHT_HIP]
        val hmX = (lh.x + rh.x) * 0.5f
        val hmY = (lh.y + rh.y) * 0.5f
        val hmZ = (lh.z + rh.z) * 0.5f

        val s = world[shoulderIdx]
        val e = world[elbowIdx]
        val w = world[wristIdx]

        // Project onto sagittal plane (body Y + body Z only, discard lateral X).
        // 3D angle is rotation-invariant so using all 3 body axes gives the same
        // result as raw world XYZ. The sagittal projection removes the noisy
        // lateral component and measures flexion/extension directly.
        val sBy = toBody(s.x - hmX, s.y - hmY, s.z - hmZ, bodyUp)
        val sBz = toBody(s.x - hmX, s.y - hmY, s.z - hmZ, bodyForward)

        val eBy = toBody(e.x - hmX, e.y - hmY, e.z - hmZ, bodyUp)
        val eBz = toBody(e.x - hmX, e.y - hmY, e.z - hmZ, bodyForward)

        val wBy = toBody(w.x - hmX, w.y - hmY, w.z - hmZ, bodyUp)
        val wBz = toBody(w.x - hmX, w.y - hmY, w.z - hmZ, bodyForward)

        return angle3D(0f, sBy, sBz, 0f, eBy, eBz, 0f, wBy, wBz)
    }

    private fun toBody(rx: Float, ry: Float, rz: Float, axis: FloatArray): Float {
        return rx * axis[0] + ry * axis[1] + rz * axis[2]
    }

    // ==================== Stage 3: Segment-Length Constrained ====================

    private fun segmentConstrainedAngle(
        side: Int,
        world: List<SmoothedLandmark>,
        norm: List<SmoothedLandmark>,
        shoulderIdx: Int, elbowIdx: Int, wristIdx: Int
    ): Double {
        val ns = norm[shoulderIdx]; val ne = norm[elbowIdx]; val nw = norm[wristIdx]

        val upperArmScreen = dist2D(ns.x, ns.y, ne.x, ne.y)
        val forearmScreen = dist2D(ne.x, ne.y, nw.x, nw.y)

        maxUpperArmScreenLen[side] = maxOf(upperArmScreen, maxUpperArmScreenLen[side] * CALIBRATION_DECAY)
        maxForearmScreenLen[side] = maxOf(forearmScreen, maxForearmScreenLen[side] * CALIBRATION_DECAY)

        val maxUA = maxUpperArmScreenLen[side]
        val maxFA = maxForearmScreenLen[side]

        val uaRatio = if (maxUA > 0.01f) (upperArmScreen / maxUA).coerceIn(0f, 1f) else 1f
        val faRatio = if (maxFA > 0.01f) (forearmScreen / maxFA).coerceIn(0f, 1f) else 1f

        // Depth component from foreshortening
        val uaDz = if (uaRatio < FORESHORTENING_THRESHOLD) sqrt(1f - uaRatio * uaRatio) * maxUA else 0f
        val faDz = if (faRatio < FORESHORTENING_THRESHOLD) sqrt(1f - faRatio * faRatio) * maxFA else 0f

        // Z direction from world landmarks (sign only)
        val ws = world[shoulderIdx]; val we = world[elbowIdx]; val ww = world[wristIdx]
        val uaZSign = sign(we.z - ws.z)
        val faZSign = sign(ww.z - we.z)

        // Reconstruct 3D vectors: screen XY + computed Z
        val baX = ns.x - ne.x
        val baY = ns.y - ne.y
        val baZ = uaDz * uaZSign

        val bcX = nw.x - ne.x
        val bcY = nw.y - ne.y
        val bcZ = faDz * faZSign

        return angle3D(
            baX + 0f, baY + 0f, baZ,  // +0f avoids smart-cast issues
            0f, 0f, 0f,               // vertex at origin
            bcX + 0f, bcY + 0f, bcZ
        )
    }

    // ==================== Stage 4 & 5: Fusion + Gating ====================

    private fun estimateSide(
        side: Int,
        facingRatio: Float,
        torsoReady: Boolean,
        world: List<SmoothedLandmark>,
        norm: List<SmoothedLandmark>,
        timestampMs: Long,
        shoulderIdx: Int, elbowIdx: Int, wristIdx: Int
    ): Double? {
        val s = world[shoulderIdx]; val e = world[elbowIdx]; val w = world[wristIdx]
        if (!s.isVisible(0.5f) || !e.isVisible(0.5f) || !w.isVisible(0.5f)) {
            return holdOrNull(side, timestampMs)
        }

        // --- Stage 2: body-plane angle ---
        var bpAngle = Double.NaN
        var bpConf = 0f
        if (torsoReady) {
            bpAngle = bodyPlaneAngle(world, shoulderIdx, elbowIdx, wristIdx)
            val ls = world[BodyLandmarks.LEFT_SHOULDER]
            val rs = world[BodyLandmarks.RIGHT_SHOULDER]
            val shoulderWorldLen = dist3D(ls.x, ls.y, ls.z, rs.x, rs.y, rs.z)
            bpConf = when {
                shoulderWorldLen < MIN_SHOULDER_WORLD_LEN -> 0.3f
                else -> 0.75f + 0.25f * (1f - facingRatio)
            }
        }

        // --- Stage 3: segment-constrained angle ---
        val scAngle = segmentConstrainedAngle(side, world, norm, shoulderIdx, elbowIdx, wristIdx)

        val ns = norm[shoulderIdx]; val ne = norm[elbowIdx]; val nw = norm[wristIdx]
        val upperArmScreen = dist2D(ns.x, ns.y, ne.x, ne.y)
        val forearmScreen = dist2D(ne.x, ne.y, nw.x, nw.y)
        val maxUA = maxUpperArmScreenLen[side]
        val maxFA = maxForearmScreenLen[side]
        val uaRatio = if (maxUA > 0.01f) (upperArmScreen / maxUA).coerceIn(0f, 1f) else 1f
        val faRatio = if (maxFA > 0.01f) (forearmScreen / maxFA).coerceIn(0f, 1f) else 1f

        val scConf = when {
            calibrationFrameCount < WARM_UP_FRAMES -> 0.1f
            minOf(uaRatio, faRatio) > 0.93f -> 0.15f
            else -> 0.3f + 0.3f * facingRatio
        }

        // --- Stage 4: fusion ---
        val totalWeight = bpConf + scConf
        val fusedAngle: Double
        val fusedConf: Float

        if (totalWeight < 0.01f) {
            lastDiagnostics[side] = ElbowDiagnostics(
                facingRatio, Double.NaN, 0f, scAngle, 0f, Double.NaN, 0f,
                isHolding = true, calibrationProgress(side)
            )
            return holdOrNull(side, timestampMs)
        }

        if (bpAngle.isNaN()) {
            fusedAngle = scAngle
            fusedConf = scConf
        } else {
            fusedAngle = (bpAngle * bpConf + scAngle * scConf) / totalWeight
            fusedConf = (totalWeight / 2f).coerceIn(0f, 1f)
        }

        // --- Stage 5: confidence gate ---
        val isHolding: Boolean
        val outputAngle: Double?
        if (fusedConf < MIN_CONFIDENCE) {
            isHolding = true
            outputAngle = holdOrNull(side, timestampMs)
        } else {
            val clamped = fusedAngle.coerceIn(0.0, MAX_ELBOW_ANGLE)
            lastStableAngle[side] = clamped
            lastStableTimestamp[side] = timestampMs
            isHolding = false
            outputAngle = clamped
        }

        lastDiagnostics[side] = ElbowDiagnostics(
            facingRatio = facingRatio,
            bodyPlaneAngle = if (bpAngle.isNaN()) null else bpAngle,
            bodyPlaneConfidence = bpConf,
            constrainedAngle = scAngle,
            constrainedConfidence = scConf,
            fusedAngle = outputAngle,
            fusedConfidence = fusedConf,
            isHolding = isHolding,
            calibrationProgress = calibrationProgress(side)
        )

        return outputAngle
    }

    private fun holdOrNull(side: Int, timestampMs: Long): Double? {
        if (lastStableAngle[side].isNaN()) return null
        val elapsed = timestampMs - lastStableTimestamp[side]
        return if (elapsed < HOLD_TIMEOUT_MS) lastStableAngle[side] else null
    }

    private fun calibrationProgress(side: Int): Float {
        val frameProgress = (calibrationFrameCount.toFloat() / WARM_UP_FRAMES).coerceIn(0f, 1f)
        val lenProgress = if (maxUpperArmScreenLen[side] > 0.01f && maxForearmScreenLen[side] > 0.01f) 1f else 0f
        return frameProgress * 0.7f + lenProgress * 0.3f
    }

    // ==================== Vector math (zero allocation) ====================

    private fun len3(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    private fun dist2D(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist3D(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2; val dz = z1 - z2
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun cross(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        out: FloatArray
    ) {
        out[0] = ay * bz - az * by
        out[1] = az * bx - ax * bz
        out[2] = ax * by - ay * bx
    }

    /**
     * Angle at vertex B between points A-B-C in 3D (degrees).
     */
    private fun angle3D(
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

        val cosAngle = (dot / (magBA * magBC)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosAngle.toDouble()))
    }
}

/**
 * Diagnostics snapshot for one elbow (left or right).
 * Exposed to DebugActivity Angle Lab for inspection.
 */
data class ElbowDiagnostics(
    val facingRatio: Float,
    val bodyPlaneAngle: Double?,
    val bodyPlaneConfidence: Float,
    val constrainedAngle: Double?,
    val constrainedConfidence: Float,
    val fusedAngle: Double?,
    val fusedConfidence: Float,
    val isHolding: Boolean,
    val calibrationProgress: Float
)
