package com.movit.core.training.engine.policy

import kotlin.math.exp

/**
 * WP-24 — the prevention layer.
 *
 * Some camera positions make a joint angle **unobservable**: when the joint's motion plane
 * contains the optical axis, the projection carries no information about the angle and no
 * amount of arbitration or smoothing can recover it. The only fix is to move the camera, so
 * the honest response is to say so.
 *
 * Consumes the per-frame `PoseFrame.angleObservability` signal and turns it into at most one
 * stable hint per episode:
 * - a dt-aware EMA over the "poor geometry" indicator, so a single bad frame never speaks;
 * - hysteresis between [POOR_ENTER] and [POOR_EXIT] so the hint cannot flicker;
 * - a warm-up ([MIN_SAMPLES]) so it never fires on the first frames of a session.
 *
 * Emission timing is the caller's business — the engine only offers the hint outside a rep,
 * because interrupting someone mid-set to talk about camera placement is worse than useless.
 *
 * Not thread-safe: pose-frame worker thread only.
 */
class CameraPlacementAdvisor(
    private val enterShare: Float = POOR_ENTER,
    private val exitShare: Float = POOR_EXIT,
    private val minSamples: Int = MIN_SAMPLES,
) {
    companion object {
        /** Below this, the joint plane is close enough to edge-on that the angle is guesswork. */
        const val OBSERVABILITY_POOR: Float = 0.35f

        /** Share of recent frames that must be poor before the hint appears. */
        const val POOR_ENTER: Float = 0.60f

        /** …and the share it must fall back under before the hint clears. */
        const val POOR_EXIT: Float = 0.30f

        const val MIN_SAMPLES: Int = 20

        /** EMA time constant for the poor-frame share. */
        private const val TAU_MS = 1_500.0
        private const val MAX_DT_MS = 500.0
    }

    private var poorShare = Float.NaN
    private var samples = 0
    private var lastTs = 0L
    private var worstObservability = 1f
    private var worstJointCode: String? = null

    /** True while the geometry is bad enough that a camera move is the real fix. */
    var isAdvising: Boolean = false
        private set

    fun reset() {
        poorShare = Float.NaN
        samples = 0
        lastTs = 0L
        isAdvising = false
        worstObservability = 1f
        worstJointCode = null
    }

    /**
     * Feeds one frame.
     *
     * @return a hint **only on the transition into the advising state**, so the caller emits
     *   once per episode instead of every frame.
     */
    fun onFrame(observability: Map<String, Float>, timestampMs: Long): CameraPlacementHint? {
        if (observability.isEmpty()) return null
        var worst = Float.MAX_VALUE
        var worstCode: String? = null
        for ((jointCode, value) in observability) {
            if (value.isNaN()) continue
            if (value < worst) {
                worst = value
                worstCode = jointCode
            }
        }
        if (worstCode == null) return null

        val dt = if (lastTs <= 0L) Double.NaN else (timestampMs - lastTs).toDouble()
        lastTs = timestampMs
        val poorNow = if (worst < OBSERVABILITY_POOR) 1f else 0f
        poorShare = if (poorShare.isNaN() || dt.isNaN() || dt <= 0.0 || dt > MAX_DT_MS) {
            poorNow
        } else {
            val alpha = (1.0 - exp(-dt.coerceAtMost(MAX_DT_MS) / TAU_MS)).toFloat()
            poorShare + alpha * (poorNow - poorShare)
        }
        samples++
        worstObservability = worst
        worstJointCode = worstCode

        if (!isAdvising && samples >= minSamples && poorShare >= enterShare) {
            isAdvising = true
            return CameraPlacementHint(
                jointCode = worstCode,
                observability = worst,
                poorFrameShare = poorShare,
            )
        }
        if (isAdvising && poorShare <= exitShare) {
            isAdvising = false
        }
        return null
    }
}

/**
 * "This camera position cannot measure this joint." Carries the evidence so the UI can decide
 * how loudly to say it — and so the session report can prove it later.
 */
data class CameraPlacementHint(
    val jointCode: String,
    val observability: Float,
    val poorFrameShare: Float,
) {
    /** Resolved by the UI layer through the string catalogue. */
    val messageCode: String get() = MESSAGE_CODE

    companion object {
        const val MESSAGE_CODE: String = "training_camera_angle_hint"
    }
}
