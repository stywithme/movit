package com.movit.core.training.engine.policy

import com.movit.core.training.engine.JointAngleTracker

/**
 * Turns per-joint angle confidence (WP-23) into two engine decisions, and keeps the session
 * statistics that feed the camera-placement hint (WP-24) and the M-E gate (WP-26).
 *
 * Two thresholds, deliberately different (see [AngleConfidenceDefaults]):
 * - below [AngleConfidenceDefaults.SCORING_MIN] a joint stops being *scored* — the rep still
 *   counts, it just is not graded on a measurement we do not trust;
 * - below [AngleConfidenceDefaults.PHASE_FREEZE_MIN] a **primary** joint freezes the phase
 *   machine, so no rep can close on a frame where the driving angle is not observable.
 *
 * Joints with no entry in the confidence map are fully trusted — see the contract on
 * `PoseFrame.angleConfidence`.
 *
 * Not thread-safe: called from the pose-frame worker thread only, like the rest of the engine.
 */
class AngleConfidenceMonitor(
    private val primaryJointCodes: Set<String>,
    private val scoringMin: Float = AngleConfidenceDefaults.SCORING_MIN,
    private val freezeMin: Float = AngleConfidenceDefaults.PHASE_FREEZE_MIN,
) {
    private val framesByJoint = mutableMapOf<String, Int>()
    private val lowFramesByJoint = mutableMapOf<String, Int>()
    private var measuredFrames = 0
    private var frozenFrames = 0

    fun reset() {
        framesByJoint.clear()
        lowFramesByJoint.clear()
        measuredFrames = 0
        frozenFrames = 0
    }

    /**
     * @param confidence joint-keyed confidence in **capture** space (already front-camera
     *   mirrored by `PoseFrame.mirrored`).
     * @param isBilateralFlipped when true, tracked joint `X` reads the angle of its mirror —
     *   the confidence key has to make the same hop or it describes the wrong arm.
     */
    fun evaluate(confidence: Map<String, Float>, isBilateralFlipped: Boolean): AngleConfidenceVerdict {
        if (confidence.isEmpty()) return AngleConfidenceVerdict.TRUSTED
        var untrusted: MutableSet<String>? = null
        var freeze = false
        measuredFrames++
        for ((sourceCode, value) in confidence) {
            val jointCode = if (isBilateralFlipped) {
                JointAngleTracker.mirrorJointCode(sourceCode)
            } else {
                sourceCode
            }
            framesByJoint[jointCode] = (framesByJoint[jointCode] ?: 0) + 1
            if (value >= scoringMin) continue
            lowFramesByJoint[jointCode] = (lowFramesByJoint[jointCode] ?: 0) + 1
            (untrusted ?: mutableSetOf<String>().also { untrusted = it }).add(jointCode)
            if (value < freezeMin && jointCode in primaryJointCodes) freeze = true
        }
        if (freeze) frozenFrames++
        val codes = untrusted
        if (codes == null) return AngleConfidenceVerdict.TRUSTED
        return AngleConfidenceVerdict(untrustedJointCodes = codes, freezePhase = freeze)
    }

    /** Session-to-date summary; the input WP-24 uses to decide whether to suggest a camera move. */
    fun snapshot(): AngleConfidenceReport = AngleConfidenceReport(
        measuredFrames = measuredFrames,
        frozenFrameShare = share(frozenFrames, measuredFrames),
        lowConfidenceShareByJoint = framesByJoint.mapValues { (joint, frames) ->
            share(lowFramesByJoint[joint] ?: 0, frames)
        },
    )

    private fun share(part: Int, total: Int): Float =
        if (total <= 0) 0f else part.toFloat() / total.toFloat()
}

data class AngleConfidenceVerdict(
    /** Excluded from quality scoring and joint feedback this frame. */
    val untrustedJointCodes: Set<String>,
    /** A primary joint is unobservable — hold the phase instead of advancing it. */
    val freezePhase: Boolean,
) {
    companion object {
        val TRUSTED = AngleConfidenceVerdict(emptySet(), freezePhase = false)
    }
}

data class AngleConfidenceReport(
    val measuredFrames: Int,
    /** Share of frames where a primary joint froze the phase machine. */
    val frozenFrameShare: Float,
    /** Per joint: share of frames whose angle was too untrusted to score. */
    val lowConfidenceShareByJoint: Map<String, Float>,
)
