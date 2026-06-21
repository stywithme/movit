package com.movit.core.training.geometry

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
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

        val outA = estimatorA.correct(input, world, norm, ts)
        val outB = estimatorB.correct(input, world, norm, ts)

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

        estimator.correct(JointAngles(leftElbow = 100.0), world, norm, 1_000L)

        assertEquals(ElbowCorrectionStrategy.STRAIGHT, estimator.lastDiagnostics[LEFT]?.strategy)
        assertEquals(false, estimator.lastDiagnostics[LEFT]?.isHolding)
    }

    @Test
    fun trust3D_whenWorldAngleNotInflated() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeTrust3DLeftElbow(world, norm)

        estimator.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L)

        assertEquals(ElbowCorrectionStrategy.TRUST_3D, estimator.lastDiagnostics[LEFT]?.strategy)
    }

    @Test
    fun holdStrategy_appearsDuringLowConfidenceWithinTimeout() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        var angles = JointAngles(leftElbow = 70.0)
        repeat(8) { frame ->
            angles = estimator.correct(angles, world, norm, 1_000L + frame * 33L)
        }
        assertNotNull(angles.leftElbow)
        assertNotEquals(ElbowCorrectionStrategy.HOLD, estimator.lastDiagnostics[LEFT]!!.strategy)

        placeHighDepthInflatedLeftElbow(world, norm)
        var held = angles
        repeat(4) { frame ->
            held = estimator.correct(held, world, norm, 1_300L + frame * 33L)
        }

        assertEquals(ElbowCorrectionStrategy.HOLD, estimator.lastDiagnostics[LEFT]?.strategy)
        assertEquals(true, estimator.lastDiagnostics[LEFT]?.isHolding)
        assertEquals(angles.leftElbow, held.leftElbow)
    }

    @Test
    fun reset_clearsDiagnosticsAndState() {
        val estimator = ElbowAngleEstimator()
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        placeBentLeftElbowSideView(world, norm)

        estimator.correct(JointAngles(leftElbow = 80.0), world, norm, 1_000L)
        assertNotNull(estimator.lastDiagnostics[LEFT])

        estimator.reset()

        assertNull(estimator.lastDiagnostics[LEFT])
        assertNull(estimator.lastDiagnostics[RIGHT])
    }

  private companion object {
        const val LEFT = 0
        const val RIGHT = 1

        fun visibleLandmarks(): MutableList<Landmark> =
            MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }

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

        fun placeBentLeftElbowMatching2D3D(world: MutableList<Landmark>, norm: MutableList<Landmark>) =
            placeTrust3DLeftElbow(world, norm)

        fun placeHighDepthInflatedLeftElbow(world: MutableList<Landmark>, norm: MutableList<Landmark>) {
            world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.05f, 0.15f, 0.55f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.15f, 0.35f, 0.85f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0.02f, 1f, 1f)
            world[PoseLandmarkIndices.LEFT_HIP] = Landmark(-0.2f, -0.1f, 0f, 1f, 1f)
            world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.2f, -0.1f, 0f, 1f, 1f)

            // Bent ~90° in screen space (must stay below STRAIGHT_ARM_GATE=150°).
            norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.40f, 0.25f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.40f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
            norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.25f, 0f, 1f, 1f)
        }
    }
}
