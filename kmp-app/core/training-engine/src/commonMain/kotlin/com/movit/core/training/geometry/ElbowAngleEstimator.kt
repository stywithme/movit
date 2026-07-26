package com.movit.core.training.geometry

import com.movit.core.training.engine.policy.AngleConfidenceDefaults
import com.movit.core.training.engine.policy.VisibilityDefaults
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Elbow-only angle arbiter (WP-21 / WP-22 / WP-23).
 *
 * Monocular RGB cannot observe the elbow angle when the arm plane contains the optical
 * axis. This class therefore does two things: it blends the two available (both biased)
 * measurements — screen-space `ang2D` and MediaPipe world `ang3D` — and it reports **how
 * much that output deserves trust** via [lastConfidence].
 *
 * Design rules (see `Camera-Engine-Elbow-Correction-Plan.md`):
 * - **Confidence is computed before any branch is chosen** — no path escapes the depth gate
 *   (EL-05). The old ordering let `STRAIGHT` run first and ignore depth entirely (EL-01).
 * - **Every transfer function is continuous** in `ang2D`, `maxDz` and `ang3D - ang2D`
 *   (EL-02, EL-03). [ElbowCorrectionStrategy] survives only as a diagnostics label.
 * - **The correction driver is limb depth**, with torso facing as a secondary weight
 *   (EL-04) — the worst case (frontal view, arm toward the lens) used to disable it.
 * - EMA smoothing uses **time constants**, not per-frame alphas, so behaviour is identical
 *   across the 30/15/10 fps throughput profiles and their mid-session downgrades (EL-09).
 *
 * The down-scaling of `ang2D` remains a **tuned bias**, not an inferred error direction —
 * `dzShare` is a confidence signal, not a sign. WP-25 replaces it with a reconstructed
 * depth once bone-length calibration lands; until then the bias is documented, bounded by
 * [MAX_CORRECTION], and always accompanied by a confidence value.
 *
 * State is per-instance and per-side; one instance per frame source (E-05).
 */
class ElbowAngleEstimator {
    companion object {
        private const val LEFT = 0
        private const val RIGHT = 1

        /** EMA time constants (EL-09). 250ms ≈ legacy alpha 0.18 @30fps; 130ms ≈ 0.25 @30fps. */
        private const val TAU_DZ_MS = 250.0
        private const val TAU_OUT_MS = 130.0
        private const val MIN_DT_MS = 1.0
        private const val MAX_DT_MS = 200.0

        /**
         * Straight-arm snap window (EL-02). A truly straight arm always projects to 180°
         * (a pinhole camera maps lines to lines), so any deficit here is landmark noise —
         * a narrow dead-band, never the 30° band the legacy gate compressed.
         */
        private const val STRAIGHT_SNAP_START = 172.0
        private const val STRAIGHT_SNAP_FULL = 180.0

        /** World-3D is trusted while inflation stays below [INFLATION_TRUST]; rejected past [INFLATION_REJECT]. */
        private const val INFLATION_TRUST = 6.0
        private const val INFLATION_REJECT = 18.0

        private const val LOW_DEPTH = 0.15f
        /** Diagnostics label boundary only — no longer a computation branch (EL-03). */
        private const val MID_DEPTH = 0.40f
        private const val HIGH_DEPTH = 0.60f

        private const val MAX_CORRECTION = 0.25f
        /** Torso facing is a secondary weight, never a kill switch (EL-04). */
        private const val FACING_WEIGHT_FLOOR = 0.60f
        private const val SIDE_GATE_HIGH = 0.85f
        private const val SIDE_GATE_LOW = 0.40f

        private const val HOLD_TIMEOUT_MS = 500L
        private const val MAX_ELBOW_ANGLE = 180.0

        private const val TRUST_3D_LABEL_MIN = 0.5
        private const val STRAIGHT_LABEL_MIN = 0.5
        private const val TRUST_2D_LABEL_MAX_CORR = 0.02f

        /** Visibility ramp for the confidence factor: gate → this value maps to 0 → 1. */
        private const val VISIBILITY_FULL = 0.90f
    }

    /**
     * WP-25: use [ElbowDepthReconstructor] as a third candidate, weighted by its own derived
     * confidence. **Off by default** — it stays behind this flag until the M-E recordings say
     * it beats the arbiter on cross-camera consistency (see the plan's WP-26 gate).
     */
    var reconstructionEnabled: Boolean = false

    private val reconstructor = ElbowDepthReconstructor()

    private val smUaDz = floatArrayOf(Float.NaN, Float.NaN)
    private val smFaDz = floatArrayOf(Float.NaN, Float.NaN)
    private val smoothOut = doubleArrayOf(Double.NaN, Double.NaN)
    private val lastStable = doubleArrayOf(Double.NaN, Double.NaN)
    private val lastStableTs = longArrayOf(0L, 0L)
    private val lastFrameTs = longArrayOf(0L, 0L)

    /** Latest per-side diagnostics after [correct]; index 0=left, 1=right. */
    var lastDiagnostics: Array<ElbowCorrectionDiagnostics?> = arrayOf(null, null)
        private set

    /**
     * Per-side confidence in [0,1] for the last [correct] call (WP-23); `NaN` when that side
     * produced no angle. Always populated — independent of `collectDiagnostics`.
     */
    var lastConfidence: FloatArray = floatArrayOf(Float.NaN, Float.NaN)
        private set

    /**
     * Per-side arm-plane observability in [0,1] (WP-24); `NaN` when unavailable.
     * `0` means the arm plane contains the optical axis — the angle is not observable at all.
     */
    var lastObservability: FloatArray = floatArrayOf(Float.NaN, Float.NaN)
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
        reconstructor.reset()
        for (i in 0..1) {
            smUaDz[i] = Float.NaN
            smFaDz[i] = Float.NaN
            smoothOut[i] = Double.NaN
            lastStable[i] = Double.NaN
            lastStableTs[i] = 0L
            lastFrameTs[i] = 0L
            lastDiagnostics[i] = null
            lastConfidence[i] = Float.NaN
            lastObservability[i] = Float.NaN
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
        val ns = norm[sIdx]; val ne = norm[eIdx]; val nw = norm[wIdx]
        // EL-07: gate on *normalized* visibility — the only signal MediaPipe reliably fills.
        // World landmarks default to 1.0 when the task API omits visibility on both platforms.
        val gate = VisibilityDefaults.ANGLE_GATE
        if (!ns.isVisible(gate) || !ne.isVisible(gate) || !nw.isVisible(gate)) {
            lastConfidence[side] = Float.NaN
            lastObservability[side] = Float.NaN
            return holdOrNull(side, ts)
        }
        val ws = world[sIdx]; val we = world[eIdx]; val ww = world[wIdx]
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

        // EL-08 / EL-09: seed on first sample, then advance with a dt-derived alpha.
        val previousTs = lastFrameTs[side]
        val rawDt = if (previousTs <= 0L) Double.NaN else (ts - previousTs).toDouble()
        val stale = rawDt.isNaN() || rawDt <= 0.0 || rawDt > HOLD_TIMEOUT_MS.toDouble()
        lastFrameTs[side] = ts
        val dzAlpha = if (stale) 1f else emaAlpha(rawDt, TAU_DZ_MS)
        smUaDz[side] = advance(smUaDz[side], uaDzRaw, dzAlpha)
        smFaDz[side] = advance(smFaDz[side], faDzRaw, dzAlpha)
        val uaDz = smUaDz[side]
        val faDz = smFaDz[side]
        val maxDz = maxOf(uaDz, faDz)

        lastObservability[side] = armPlaneObservability(ws, we, ww)

        // ── Confidence-first: every driver is resolved before any output is chosen ──
        val depthRamp = ramp(maxDz, LOW_DEPTH, HIGH_DEPTH)
        val sideStrength = computeSideStrength(facingRatio)
        val facingWeight = FACING_WEIGHT_FLOOR + (1f - FACING_WEIGHT_FLOOR) * sideStrength
        val isLowConfidence = maxDz > HIGH_DEPTH

        // 2D path: depth-driven down-bias, then a narrow straight-arm noise snap that itself
        // fades out with depth (so a foreshortened arm is never snapped toward 180°).
        val corrPct = MAX_CORRECTION * depthRamp * facingWeight
        val corrected2D = ang2D * (1.0 - corrPct)
        val straightT = smoothstep(STRAIGHT_SNAP_START, STRAIGHT_SNAP_FULL, ang2D) *
            (1.0 - depthRamp.toDouble())
        val out2D = corrected2D + (MAX_ELBOW_ANGLE - corrected2D) * straightT

        // 3D path weight: full trust while world-3D agrees with the projection, fading to zero
        // once it is clearly inflated. Continuous replacement of the old hard 12° gate.
        //
        // The straight-arm term also suppresses it: a truly straight arm projects to exactly
        // 180° (lines map to lines), while any z noise can only *bend* a collinear triple —
        // so in the near-straight, low-depth regime the projection is provably the better
        // estimator and world-3D is biased downward.
        val inflation = ang3D - ang2D
        val trust3D = (1.0 - smoothstep(INFLATION_TRUST, INFLATION_REJECT, inflation)) *
            (1.0 - straightT)

        val arbitrated = trust3D * ang3D + (1.0 - trust3D) * out2D

        val visibilityFactor = visibilityFactor(ns, ne, nw, gate)
        val arbiterConfidence = AngleConfidenceDefaults.elbowConfidence(
            visibilityFactor = visibilityFactor,
            depthRamp = depthRamp,
            disagreementDeg = abs(inflation),
        )

        // WP-25: a third candidate built from a physical constraint instead of tuned constants.
        // It only earns weight in proportion to its own derived confidence.
        val reconstruction = if (reconstructionEnabled) {
            reconstruct(side, ns, ne, nw, norm, ws, we, ww, aspectYScale, ts)
        } else {
            null
        }
        val reconWeight = reconstruction?.confidence?.coerceIn(0f, 1f)?.toDouble() ?: 0.0
        val output = reconWeight * (reconstruction?.angleDeg ?: 0.0) + (1.0 - reconWeight) * arbitrated
        val staticConfidence = (
            (1.0 - reconWeight) * arbiterConfidence +
                reconWeight * (reconstruction?.confidence?.toDouble() ?: 0.0)
            ).toFloat()

        val clamped = output.coerceIn(0.0, MAX_ELBOW_ANGLE)
        val strategy = resolveStrategy(isLowConfidence, trust3D, straightT, corrPct, depthRamp)

        if (isLowConfidence && !lastStable[side].isNaN() && ts - lastStableTs[side] < HOLD_TIMEOUT_MS) {
            val holdAge = (ts - lastStableTs[side]).toFloat() / HOLD_TIMEOUT_MS.toFloat()
            val holdConfidence = staticConfidence * (1f - holdAge).coerceIn(0f, 1f)
            smoothOut[side] = lastStable[side]
            lastConfidence[side] = holdConfidence
            if (collectDiagnostics) {
                lastDiagnostics[side] = diagnostics(
                    strategy = ElbowCorrectionStrategy.HOLD,
                    facingRatio = facingRatio,
                    ang2D = ang2D,
                    ang3D = ang3D,
                    maxDz = maxDz,
                    uaDz = uaDz,
                    faDz = faDz,
                    corrPct = corrPct,
                    output = lastStable[side],
                    isHolding = true,
                    confidence = holdConfidence,
                    observability = lastObservability[side],
                )
            }
            return lastStable[side]
        }

        val outAlpha = if (stale) 1.0 else emaAlpha(rawDt, TAU_OUT_MS).toDouble()
        val smoothed = if (smoothOut[side].isNaN()) {
            clamped
        } else {
            smoothOut[side] + outAlpha * (clamped - smoothOut[side])
        }
        smoothOut[side] = smoothed
        if (!isLowConfidence) {
            lastStable[side] = smoothed
            lastStableTs[side] = ts
        }
        lastConfidence[side] = staticConfidence
        if (collectDiagnostics) {
            lastDiagnostics[side] = diagnostics(
                strategy = strategy,
                facingRatio = facingRatio,
                ang2D = ang2D,
                ang3D = ang3D,
                maxDz = maxDz,
                uaDz = uaDz,
                faDz = faDz,
                corrPct = corrPct,
                output = smoothed,
                isHolding = false,
                confidence = staticConfidence,
                observability = lastObservability[side],
            )
        }
        return smoothed
    }

    /** WP-25: feeds the reconstructor in aspect-corrected, torso-normalised screen space. */
    @Suppress("LongParameterList")
    private fun reconstruct(
        side: Int,
        ns: Landmark,
        ne: Landmark,
        nw: Landmark,
        norm: List<Landmark>,
        ws: Landmark,
        we: Landmark,
        ww: Landmark,
        aspectYScale: Float,
        ts: Long,
    ): ElbowDepthEstimate? {
        val leftShoulder = norm[PoseLandmarkIndices.LEFT_SHOULDER]
        val rightShoulder = norm[PoseLandmarkIndices.RIGHT_SHOULDER]
        val torsoScale = dist2D(
            leftShoulder.x,
            leftShoulder.y * aspectYScale,
            rightShoulder.x,
            rightShoulder.y * aspectYScale,
        )
        return reconstructor.estimate(
            side = side,
            shoulderX = ns.x,
            shoulderY = ns.y * aspectYScale,
            elbowX = ne.x,
            elbowY = ne.y * aspectYScale,
            wristX = nw.x,
            wristY = nw.y * aspectYScale,
            torsoScale = torsoScale,
            worldUaDz = ws.z - we.z,
            worldFaDz = ww.z - we.z,
            previousAngleDeg = smoothOut[side],
            timestampMs = ts,
        )
    }

    /**
     * WP-24: how well the elbow plane is oriented for measurement.
     *
     * The plane spanned by the upper arm and forearm has normal `n = ua × fa`. Measurement is
     * best when that plane is parallel to the image plane, i.e. `n` is parallel to the optical
     * axis. `|n_z| / ‖n‖` therefore goes to 0 exactly when the plane contains the optical axis
     * and the angle stops being observable.
     */
    private fun armPlaneObservability(s: Landmark, e: Landmark, w: Landmark): Float {
        val uax = s.x - e.x; val uay = s.y - e.y; val uaz = s.z - e.z
        val fax = w.x - e.x; val fay = w.y - e.y; val faz = w.z - e.z
        val nx = uay * faz - uaz * fay
        val ny = uaz * fax - uax * faz
        val nz = uax * fay - uay * fax
        val mag = sqrt(nx * nx + ny * ny + nz * nz)
        if (mag < 1e-7f) return Float.NaN
        return (abs(nz) / mag).coerceIn(0f, 1f)
    }

    private fun resolveStrategy(
        isLowConfidence: Boolean,
        trust3D: Double,
        straightT: Double,
        corrPct: Float,
        depthRamp: Float,
    ): ElbowCorrectionStrategy = when {
        isLowConfidence -> ElbowCorrectionStrategy.LOW_CONF
        trust3D >= TRUST_3D_LABEL_MIN -> ElbowCorrectionStrategy.TRUST_3D
        straightT >= STRAIGHT_LABEL_MIN -> ElbowCorrectionStrategy.STRAIGHT
        corrPct < TRUST_2D_LABEL_MAX_CORR -> ElbowCorrectionStrategy.TRUST_2D
        depthRamp < ramp(MID_DEPTH, LOW_DEPTH, HIGH_DEPTH) -> ElbowCorrectionStrategy.MILD_DOWN
        else -> ElbowCorrectionStrategy.DEEP_DOWN
    }

    @Suppress("LongParameterList")
    private fun diagnostics(
        strategy: ElbowCorrectionStrategy,
        facingRatio: Float,
        ang2D: Double,
        ang3D: Double,
        maxDz: Float,
        uaDz: Float,
        faDz: Float,
        corrPct: Float,
        output: Double?,
        isHolding: Boolean,
        confidence: Float,
        observability: Float,
    ) = ElbowCorrectionDiagnostics(
        strategy = strategy,
        facingRatio = facingRatio,
        screenAngle = ang2D,
        worldAngle = ang3D,
        maxDzShare = maxDz,
        dzImbalance = faDz - uaDz,
        correctionPct = corrPct,
        outputAngle = output,
        isHolding = isHolding,
        uaDzShare = uaDz,
        faDzShare = faDz,
        confidence = confidence,
        armPlaneObservability = observability,
    )

    private fun visibilityFactor(s: Landmark, e: Landmark, w: Landmark, gate: Float): Float {
        val worst = minOf(s.visibility, e.visibility, w.visibility)
        return ramp(worst, gate, VISIBILITY_FULL)
    }

    private fun holdOrNull(side: Int, ts: Long): Double? {
        if (lastStable[side].isNaN()) return null
        if (ts - lastStableTs[side] >= HOLD_TIMEOUT_MS) return null
        val holdAge = (ts - lastStableTs[side]).toFloat() / HOLD_TIMEOUT_MS.toFloat()
        lastConfidence[side] = (1f - holdAge).coerceIn(0f, 1f) * AngleConfidenceDefaults.OCCLUDED_HOLD_CEILING
        return lastStable[side]
    }

    private fun advance(current: Float, sample: Float, alpha: Float): Float =
        if (current.isNaN()) sample else current + alpha * (sample - current)

    private fun emaAlpha(dtMs: Double, tauMs: Double): Float {
        val dt = dtMs.coerceIn(MIN_DT_MS, MAX_DT_MS)
        return (1.0 - exp(-dt / tauMs)).toFloat()
    }

    private fun ramp(value: Float, from: Float, to: Float): Float {
        if (to <= from) return if (value >= to) 1f else 0f
        return ((value - from) / (to - from)).coerceIn(0f, 1f)
    }

    /** C1-continuous 0→1 transition; zero derivative at both edges (no visible kink). */
    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        if (edge1 <= edge0) return if (x >= edge1) 1.0 else 0.0
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
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
