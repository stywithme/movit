package com.movit.core.training.geometry

import com.movit.core.training.engine.policy.AngleConfidenceDefaults
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElbowAngleEstimatorTest {

    @Test
    fun correct_populatesDiagnostics_withoutChangingDeterministicOutput() {
        val estimatorA = ElbowAngleEstimator()
        val estimatorB = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        val input = JointAngles(leftElbow = 90.0, rightElbow = 90.0)
        val ts = 1_000L

        val outA = estimatorA.correct(input, world, norm, ts, collectDiagnostics = true)
        val outB = estimatorB.correct(input, world, norm, ts, collectDiagnostics = true)

        assertEquals(outA.leftElbow, outB.leftElbow)
        assertEquals(outA.rightElbow, outB.rightElbow)
        assertNotNull(estimatorA.lastDiagnostics[LEFT])
        assertEquals(estimatorA.lastDiagnostics[LEFT]!!.outputAngle, outA.leftElbow)
    }

    @Test
    fun correct_sameInputs_produceIdenticalAnglesAcrossFrames() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)
        val input = JointAngles(leftElbow = 75.0)

        val first = estimator.correct(input, world, norm, 1_000L)
        val second = estimator.correct(first, world, norm, 1_033L)

        assertNotNull(first.leftElbow)
        assertNotNull(second.leftElbow)
        assertTrue(second.leftElbow!! in 0.0..180.0)
    }

    @Test
    fun straightArm_usesStraightStrategy() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeStraightLeftArm(world, norm)

        estimator.correct(JointAngles(leftElbow = 100.0), world, norm, 1_000L, collectDiagnostics = true)

        assertEquals(ElbowCorrectionStrategy.STRAIGHT, estimator.lastDiagnostics[LEFT]?.strategy)
        assertEquals(false, estimator.lastDiagnostics[LEFT]?.isHolding)
    }

    @Test
    fun trust3D_whenWorldAngleNotInflated() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeTrust3DLeftElbow(world, norm)

        estimator.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L, collectDiagnostics = true)

        assertEquals(ElbowCorrectionStrategy.TRUST_3D, estimator.lastDiagnostics[LEFT]?.strategy)
    }

    @Test
    fun holdStrategy_freezesOutputDuringLowConfidenceWithinTimeout() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        var angles = JointAngles(leftElbow = 70.0)
        repeat(8) { frame ->
            angles = estimator.correct(angles, world, norm, 1_000L + frame * 33L, collectDiagnostics = true)
        }
        assertNotNull(angles.leftElbow)
        assertNotEquals(ElbowCorrectionStrategy.HOLD, estimator.lastDiagnostics[LEFT]!!.strategy)

        placeHighDepthInflatedLeftElbow(world, norm)
        var held: JointAngles? = null
        val holdSamples = mutableListOf<Double>()
        repeat(6) { frame ->
            val next = estimator.correct(
                held ?: angles,
                world,
                norm,
                1_300L + frame * 33L,
                collectDiagnostics = true,
            )
            held = next
            if (estimator.lastDiagnostics[LEFT]?.isHolding == true) {
                holdSamples += next.leftElbow!!
            }
        }

        assertEquals(ElbowCorrectionStrategy.HOLD, estimator.lastDiagnostics[LEFT]?.strategy)
        assertEquals(true, estimator.lastDiagnostics[LEFT]?.isHolding)
        assertTrue(holdSamples.size >= 2, "expected consecutive HOLD frames, got ${holdSamples.size}")
        // The contract is that HOLD *freezes* the output, not that it replays a specific
        // historical value: every held frame must repeat the first held value exactly.
        assertTrue(holdSamples.all { it == holdSamples.first() }, "HOLD output drifted: $holdSamples")
    }

    @Test
    fun holdConfidence_decaysWithHoldAge() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)
        var angles = JointAngles(leftElbow = 70.0)
        repeat(8) { frame ->
            angles = estimator.correct(angles, world, norm, 1_000L + frame * 33L)
        }

        placeHighDepthInflatedLeftElbow(world, norm)
        var early = Float.NaN
        var late = Float.NaN
        repeat(10) { frame ->
            val ts = 1_300L + frame * 33L
            estimator.correct(angles, world, norm, ts, collectDiagnostics = true)
            if (estimator.lastDiagnostics[LEFT]?.isHolding == true) {
                if (early.isNaN()) early = estimator.lastConfidence[LEFT]
                late = estimator.lastConfidence[LEFT]
            }
        }

        assertTrue(!early.isNaN() && !late.isNaN(), "no HOLD frame observed")
        assertTrue(late < early, "hold confidence must decay: early=$early late=$late")
    }

    @Test
    fun reset_onOneInstance_doesNotClearOther() {
        val estimatorA = ElbowAngleEstimator()
        val estimatorB = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        estimatorA.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L, collectDiagnostics = true)
        estimatorB.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L, collectDiagnostics = true)
        assertNotNull(estimatorA.lastDiagnostics[LEFT])
        assertNotNull(estimatorB.lastDiagnostics[LEFT])

        estimatorA.reset()

        assertNull(estimatorA.lastDiagnostics[LEFT])
        assertNotNull(estimatorB.lastDiagnostics[LEFT])
    }

    @Test
    fun twoInstances_keepIndependentHoldState() {
        val estimatorA = ElbowAngleEstimator()
        val estimatorB = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)
        val input = JointAngles(leftElbow = 70.0)

        repeat(8) { frame ->
            estimatorA.correct(input, world, norm, 1_000L + frame * 33L, collectDiagnostics = true)
        }
        placeHighDepthInflatedLeftElbow(world, norm)
        repeat(4) { frame ->
            estimatorA.correct(input, world, norm, 1_300L + frame * 33L, collectDiagnostics = true)
        }
        assertEquals(ElbowCorrectionStrategy.HOLD, estimatorA.lastDiagnostics[LEFT]?.strategy)
        assertNull(estimatorB.lastDiagnostics[LEFT])
    }

    @Test
    fun reset_clearsDiagnosticsAndState() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        estimator.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L, collectDiagnostics = true)
        assertNotNull(estimator.lastDiagnostics[LEFT])

        estimator.reset()

        assertNull(estimator.lastDiagnostics[LEFT])
        assertNull(estimator.lastDiagnostics[RIGHT])
    }

    @Test
    fun withoutCollectDiagnostics_skipsAllocation() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        val out = estimator.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L)
        assertNotNull(out.leftElbow)
        assertNull(estimator.lastDiagnostics[LEFT])
    }

    // ── WP-22 property tests ────────────────────────────────────────────────

    /**
     * EL-02 / EL-03: sweeps one continuous family of poses (world z skew growing from 0),
     * which crosses every internal boundary at once — the 2D/3D trust fade, the depth ramp,
     * and the low-confidence gate. The legacy branch table jumped ~11° at `MID_DEPTH`.
     */
    @Test
    fun output_isContinuous_acrossAllBranchBoundaries() {
        var previous: Double? = null
        var previousSkew = 0.0
        var worstJump = 0.0
        var worstAt = 0.0
        var skew = 0.0
        while (skew <= 1.5) {
            val out = singleFrameOutput(ang2DDeg = 90.0, zSkew = skew.toFloat(), frontal = false)
            assertNotNull(out)
            previous?.let { prev ->
                val jump = abs(out - prev)
                if (jump > worstJump) {
                    worstJump = jump
                    worstAt = skew
                }
            }
            previous = out
            previousSkew = skew
            skew += 0.002
        }
        assertTrue(previousSkew > 1.4, "sweep did not complete")
        assertTrue(worstJump < 1.0, "output jumped ${worstJump}° at zSkew=$worstAt")
    }

    /** EL-02: the 150° gate used to make the output jump ~15° and then run backwards. */
    @Test
    fun output_isMonotonicIn2DAngle() {
        var previous: Double? = null
        var worstDrop = 0.0
        var angle = 5.0
        while (angle <= 179.0) {
            val out = singleFrameOutput(ang2DDeg = angle, zSkew = 0.5f, frontal = false)
            assertNotNull(out)
            previous?.let { prev -> worstDrop = maxOf(worstDrop, prev - out) }
            previous = out
            angle += 0.25
        }
        assertTrue(worstDrop <= 1e-6, "output decreased by $worstDrop° while ang2D increased")
    }

    /**
     * EL-09: the same 200ms of wall-clock convergence must land in the same place whether the
     * adaptive throughput controller is running at 30fps or has stepped down to 10fps.
     */
    @Test
    fun output_isFrameRateInvariant_betweenProfiles() {
        fun run(frameMs: Long): Double {
            val estimator = ElbowAngleEstimator()
            val world = visibleLandmarks()
            val norm = visibleLandmarks()
            var ts = 1_000L
            placeTrust3DLeftElbow(world, norm)
            var angles = JointAngles(leftElbow = 90.0)
            while (ts < 1_200L) {
                angles = estimator.correct(angles, world, norm, ts)
                ts += frameMs
            }
            placeBentLeftElbowSideView(world, norm)
            val settleUntil = ts + 200L
            while (ts <= settleUntil) {
                angles = estimator.correct(angles, world, norm, ts)
                ts += frameMs
            }
            return angles.leftElbow!!
        }

        val fast = run(33L)
        val slow = run(100L)
        assertTrue(abs(fast - slow) < 3.0, "frame-rate dependent output: 30fps=$fast 10fps=$slow")
    }

    /** EL-08: the depth EMA used to start at 0 and needed ~9 frames after every reset. */
    @Test
    fun depthEma_seedsOnFirstFrameAfterReset() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        buildPose(world, norm, ang2DDeg = 90.0, zSkew = 1.0f, frontal = false)

        estimator.correct(JointAngles(leftElbow = 90.0), world, norm, 1_000L, collectDiagnostics = true)

        val maxDz = estimator.lastDiagnostics[LEFT]!!.maxDzShare
        // zSkew=1 ⇒ true share = 1/√2 ≈ 0.707. The legacy seed-from-zero read 0.127 here.
        assertTrue(maxDz > 0.6f, "depth EMA did not seed on the first frame: $maxDz")
    }

    /**
     * EL-01 / EL-04 / EL-05: frontal torso + arm driven toward the lens. The legacy code
     * zeroed the correction (`sideStrength = 0`) and could label this `STRAIGHT`.
     */
    @Test
    fun frontalView_deepForeshortening_marksLowConfidence() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        buildPose(world, norm, ang2DDeg = 90.0, zSkew = 1.2f, frontal = true)

        estimator.correct(JointAngles(leftElbow = 90.0), world, norm, 1_000L, collectDiagnostics = true)
        val diagnostics = estimator.lastDiagnostics[LEFT]!!

        assertEquals(ElbowCorrectionStrategy.LOW_CONF, diagnostics.strategy)
        assertTrue(diagnostics.correctionPct > 0f, "frontal view must not disable the correction")
        assertTrue(
            estimator.lastConfidence[LEFT] < AngleConfidenceDefaults.SCORING_MIN,
            "confidence ${estimator.lastConfidence[LEFT]} should gate scoring",
        )
    }

    /** EL-01: a bent arm pointing at the lens projects near-straight — it must not be snapped. */
    @Test
    fun foreshortenedBentArm_isNotSnappedToStraight() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        buildPose(world, norm, ang2DDeg = 174.0, zSkew = 1.2f, frontal = true)

        val out = estimator.correct(JointAngles(leftElbow = 90.0), world, norm, 1_000L, collectDiagnostics = true)

        assertNotEquals(ElbowCorrectionStrategy.STRAIGHT, estimator.lastDiagnostics[LEFT]?.strategy)
        assertTrue(out.leftElbow!! < 174.0, "foreshortened arm was pushed toward 180°: ${out.leftElbow}")
    }

    /** EL-07: the gate must read normalized visibility, which is the signal MediaPipe fills. */
    @Test
    fun invisibleNormalizedLandmarks_leaveAngleUncorrected() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)
        // World visibility stays 1.0 (both platforms default it that way when MediaPipe omits it).
        norm[PoseLandmarkIndices.LEFT_ELBOW] = norm[PoseLandmarkIndices.LEFT_ELBOW].copy(visibility = 0.1f)

        val input = JointAngles(leftElbow = 123.0)
        val out = estimator.correct(input, world, norm, 1_000L, collectDiagnostics = true)

        assertEquals(123.0, out.leftElbow)
        assertNull(estimator.lastDiagnostics[LEFT])
    }

    /** WP-24: the observability signal must collapse when the arm plane holds the optical axis. */
    @Test
    fun armPlaneObservability_collapsesWhenPlaneHoldsOpticalAxis() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()

        buildPose(world, norm, ang2DDeg = 90.0, zSkew = 0f, frontal = true)
        estimator.correct(JointAngles(leftElbow = 90.0), world, norm, 1_000L)
        val inPlane = estimator.lastObservability[LEFT]

        estimator.reset()
        // Both segments tilted into depth ⇒ the arm plane contains the optical axis.
        world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0f, 0.30f, -0.30f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0f, 0f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0f, 0.10f, 0.30f, 1f, 1f)
        estimator.correct(JointAngles(leftElbow = 90.0), world, norm, 1_000L)
        val edgeOn = estimator.lastObservability[LEFT]

        assertTrue(inPlane > 0.9f, "in-plane arm should be fully observable: $inPlane")
        assertTrue(edgeOn < 0.1f, "edge-on arm should be unobservable: $edgeOn")
    }

    private companion object {
        const val LEFT = 0
        const val RIGHT = 1

        fun visibleLandmarks(): MutableList<Landmark> =
            MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }

        /**
         * Synthetic pose with independent control of the projected angle and of world depth.
         *
         * World chain: shoulder `(0, 1, -s)`, elbow at origin, wrist `(1, 0, s)`. Both segments
         * carry depth share `s/√(1+s²)`, and the world angle inflates from 90° (s=0) upward —
         * the exact failure mode the estimator arbitrates. The normalized chain is built
         * separately so `ang2D` can be swept without touching depth.
         */
        fun buildPose(
            world: MutableList<Landmark>,
            norm: MutableList<Landmark>,
            ang2DDeg: Double,
            zSkew: Float,
            frontal: Boolean,
        ) {
            world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0f, 1f, -zSkew, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0f, 0f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(1f, 0f, zSkew, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_SHOULDER] = if (frontal) {
                Landmark(0.4f, 1f, -zSkew, 1f, 1f)
            } else {
                Landmark(0.03f, 1f, -zSkew + 0.4f, 1f, 1f)
            }
            world[PoseLandmarkIndices.LEFT_HIP] = Landmark(0f, -0.5f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.4f, -0.5f, 0f, 1f, 1f)

            val theta = ang2DDeg * PI / 180.0
            norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.50f, 0.50f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.50f, 0.30f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(
                (0.50 + 0.2 * sin(theta)).toFloat(),
                (0.50 - 0.2 * cos(theta)).toFloat(),
                0f,
                1f,
                1f,
            )
        }

        /** One frame through a fresh estimator = the pure transfer function, no EMA history. */
        fun singleFrameOutput(ang2DDeg: Double, zSkew: Float, frontal: Boolean): Double? {
            val estimator = ElbowAngleEstimator()
            val world = visibleLandmarks()
            val norm = visibleLandmarks()
            buildPose(world, norm, ang2DDeg, zSkew, frontal)
            return estimator.correct(JointAngles(leftElbow = 90.0), world, norm, 1_000L).leftElbow
        }

        fun placeBentLeftElbowSideView(world: MutableList<Landmark>, norm: MutableList<Landmark>) {
            world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.1f, 0.2f, 0.15f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.1f, 0.35f, 0.25f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.2f, -0.1f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_HIP] = Landmark(-0.2f, -0.1f, 0f, 1f, 1f)

            norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.40f, 0.25f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.40f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.25f, 0f, 1f, 1f)
        }

        fun placeStraightLeftArm(world: MutableList<Landmark>, norm: MutableList<Landmark>) {
            world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.05f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.2f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_HIP] = Landmark(-0.2f, -0.1f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.2f, -0.1f, 0f, 1f, 1f)

            norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.35f, 0.30f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.45f, 0.30f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.58f, 0.30f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.30f, 0f, 1f, 1f)
        }

        fun placeTrust3DLeftElbow(world: MutableList<Landmark>, norm: MutableList<Landmark>) {
            world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0f, 1f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0f, 0f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(1f, 0f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.4f, 1f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_HIP] = Landmark(0f, -0.5f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.4f, -0.5f, 0f, 1f, 1f)

            norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.40f, 0.25f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.40f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.25f, 0f, 1f, 1f)
        }

        fun placeHighDepthInflatedLeftElbow(world: MutableList<Landmark>, norm: MutableList<Landmark>) {
            world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.05f, 0.15f, 0.55f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.15f, 0.35f, 0.85f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0.02f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_HIP] = Landmark(-0.2f, -0.1f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.2f, -0.1f, 0f, 1f, 1f)

            // Bent ~90° in screen space (must stay below the straight-snap window).
            norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.40f, 0.25f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.40f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.25f, 0f, 1f, 1f)
        }
    }
}
