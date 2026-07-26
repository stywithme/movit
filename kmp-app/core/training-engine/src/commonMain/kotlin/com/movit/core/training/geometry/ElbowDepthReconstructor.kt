package com.movit.core.training.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * WP-25 — bone-length constrained depth reconstruction (behind a flag, default off).
 *
 * The heuristic arbiter picks between two biased measurements. This estimator instead uses a
 * *physical* constraint the camera cannot violate: **a bone does not change length**. Under
 * scaled-orthographic projection a segment of true length `L` that appears with length `l`
 * must be tilted out of the image plane by
 *
 * ```
 * |dz| = L · √(1 − (l / L)²)
 * ```
 *
 * `L` is unknown in metres, but it does not need to be: the longest projection the segment has
 * shown recently *is* the in-plane length, and normalising every length by the on-screen
 * shoulder width makes that estimate independent of how far the user stands from the camera.
 *
 * That leaves one genuine unknown — the **sign** of each `dz`. A global flip does not change
 * the elbow angle, so only the relative sign matters and exactly two candidate angles exist.
 * They are resolved by temporal continuity, falling back to MediaPipe's world-z sign as a weak
 * prior. Crucially, the spread between the two candidates is itself the honest confidence
 * signal: when they agree the answer is certain, when they diverge no method can be sure.
 *
 * Why this is not a repeat of `ElbowAngleEstimator v1` (which drifted and was removed): v1
 * emitted a value with no way to know when to distrust it. Here maturity and hypothesis spread
 * gate the output before it can reach the engine, and calibration is reset at every exercise
 * boundary with the rest of the angle state.
 *
 * State is per-side and per-instance; not thread-safe (pose-frame worker thread only).
 */
class ElbowDepthReconstructor {
    companion object {
        private const val SIDES = 2

        /** Frames of calibration before any output is produced. */
        const val MIN_SAMPLES = 30

        /** Sliding-window length for the in-plane length estimate. */
        private const val WINDOW_MS = 2_000L

        /** Below this on-screen torso scale the normalisation is meaningless. */
        private const val MIN_TORSO_SCALE = 1e-4f

        /** Segments shorter than this (normalised) are noise, not measurements. */
        private const val MIN_SEGMENT = 1e-3f

        private const val DEG_PER_RAD = 180.0 / kotlin.math.PI
    }

    private val uaWindow = SlidingMax(SIDES)
    private val faWindow = SlidingMax(SIDES)
    private val samples = IntArray(SIDES)

    fun reset() {
        uaWindow.reset()
        faWindow.reset()
        samples.fill(0)
    }

    /**
     * @param side 0 = left, 1 = right.
     * @param torsoScale on-screen shoulder width (aspect-corrected) — the distance normaliser.
     * @param worldUaDz signed `z` of the upper-arm vector from MediaPipe world landmarks.
     * @param worldFaDz signed `z` of the forearm vector from MediaPipe world landmarks.
     * @param previousAngleDeg last accepted output for this side, or `NaN`.
     * @return `null` while calibration is immature or the geometry is degenerate.
     */
    @Suppress("LongParameterList")
    fun estimate(
        side: Int,
        shoulderX: Float,
        shoulderY: Float,
        elbowX: Float,
        elbowY: Float,
        wristX: Float,
        wristY: Float,
        torsoScale: Float,
        worldUaDz: Float,
        worldFaDz: Float,
        previousAngleDeg: Double,
        timestampMs: Long,
    ): ElbowDepthEstimate? {
        if (torsoScale < MIN_TORSO_SCALE) return null
        val uaX = (shoulderX - elbowX) / torsoScale
        val uaY = (shoulderY - elbowY) / torsoScale
        val faX = (wristX - elbowX) / torsoScale
        val faY = (wristY - elbowY) / torsoScale
        val uaLen = sqrt(uaX * uaX + uaY * uaY)
        val faLen = sqrt(faX * faX + faY * faY)
        if (uaLen < MIN_SEGMENT || faLen < MIN_SEGMENT) return null

        val uaMax = uaWindow.observe(side, uaLen, timestampMs)
        val faMax = faWindow.observe(side, faLen, timestampMs)
        samples[side]++
        if (samples[side] < MIN_SAMPLES) return null
        if (!uaWindow.isMature(side) || !faWindow.isMature(side)) return null
        if (uaMax < MIN_SEGMENT || faMax < MIN_SEGMENT) return null

        val uaDz = outOfPlaneLength(uaLen, uaMax)
        val faDz = outOfPlaneLength(faLen, faMax)

        // Only the *relative* sign changes the angle — a global flip is the same arm.
        val aligned = angleBetween(uaX, uaY, uaDz, faX, faY, faDz) ?: return null
        val opposed = angleBetween(uaX, uaY, uaDz, faX, faY, -faDz) ?: return null

        val pickAligned = when {
            !previousAngleDeg.isNaN() ->
                abs(aligned - previousAngleDeg) <= abs(opposed - previousAngleDeg)
            // Weak prior: MediaPipe's z is unreliable in magnitude but its *sign* still carries
            // some information about which way each segment leans.
            else -> (worldUaDz >= 0f) == (worldFaDz >= 0f)
        }
        val angle = if (pickAligned) aligned else opposed
        val spread = abs(aligned - opposed)

        // The two hypotheses ARE the uncertainty: agreement means the answer is forced by the
        // geometry, divergence means a coin flip decided it.
        val confidence = (1.0 - spread / 180.0).coerceIn(0.0, 1.0).toFloat()

        return ElbowDepthEstimate(
            angleDeg = angle,
            confidence = confidence,
            hypothesisSpreadDeg = spread,
            upperArmDepthShare = depthShare(uaDz, uaMax),
            forearmDepthShare = depthShare(faDz, faMax),
        )
    }

    /** True once the side has enough history for [estimate] to return a value. */
    fun isMature(side: Int): Boolean =
        samples[side] >= MIN_SAMPLES && uaWindow.isMature(side) && faWindow.isMature(side)

    private fun outOfPlaneLength(observed: Float, inPlane: Float): Float {
        val ratio = (observed / inPlane).coerceIn(0f, 1f)
        return inPlane * sqrt((1f - ratio * ratio).coerceAtLeast(0f))
    }

    private fun depthShare(dz: Float, inPlane: Float): Float =
        if (inPlane <= 0f) 0f else (dz / inPlane).coerceIn(0f, 1f)

    private fun angleBetween(
        ax: Float,
        ay: Float,
        az: Float,
        bx: Float,
        by: Float,
        bz: Float,
    ): Double? {
        val magA = sqrt(ax * ax + ay * ay + az * az)
        val magB = sqrt(bx * bx + by * by + bz * bz)
        if (magA < 1e-7f || magB < 1e-7f) return null
        val cos = ((ax * bx + ay * by + az * bz) / (magA * magB)).coerceIn(-1f, 1f)
        return acos(cos.toDouble()) * DEG_PER_RAD
    }

    /**
     * O(1) sliding-window maximum: the current window plus the previous one. Cheap, and it
     * forgets — so a user who steps closer to the camera recalibrates within ~2 windows
     * instead of being stuck behind a stale record length.
     */
    private class SlidingMax(sides: Int) {
        private val current = FloatArray(sides)
        private val previous = FloatArray(sides)
        private val windowStart = LongArray(sides)
        private val completed = BooleanArray(sides)

        fun reset() {
            current.fill(0f)
            previous.fill(0f)
            windowStart.fill(0L)
            completed.fill(false)
        }

        fun observe(side: Int, value: Float, timestampMs: Long): Float {
            if (windowStart[side] == 0L) windowStart[side] = timestampMs
            if (timestampMs - windowStart[side] > WINDOW_MS) {
                previous[side] = current[side]
                current[side] = 0f
                windowStart[side] = timestampMs
                completed[side] = true
            }
            if (value > current[side]) current[side] = value
            return maxOf(current[side], previous[side])
        }

        fun isMature(side: Int): Boolean = completed[side]
    }
}

/**
 * One reconstructed elbow angle plus the evidence behind it.
 *
 * [confidence] is derived, not tuned: it falls out of how far apart the two sign hypotheses
 * are. [hypothesisSpreadDeg] is kept so the debug lab can show *why* a frame was distrusted.
 */
data class ElbowDepthEstimate(
    val angleDeg: Double,
    val confidence: Float,
    val hypothesisSpreadDeg: Double,
    val upperArmDepthShare: Float,
    val forearmDepthShare: Float,
)
