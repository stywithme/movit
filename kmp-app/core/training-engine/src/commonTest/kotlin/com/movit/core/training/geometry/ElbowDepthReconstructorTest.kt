package com.movit.core.training.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElbowDepthReconstructorTest {

    @Test
    fun immatureCalibration_returnsNothing() {
        val reconstructor = ElbowDepthReconstructor()

        var last: ElbowDepthEstimate? = null
        repeat(ElbowDepthReconstructor.MIN_SAMPLES - 1) { frame ->
            last = feed(reconstructor, trueAngleDeg = 90.0, azimuthDeg = 0.0, ts = frame * 33L)
        }

        assertNull(last, "reconstruction spoke before it had calibrated a bone length")
    }

    @Test
    fun inPlaneArm_reproducesTheProjectedAngle_withFullConfidence() {
        val reconstructor = ElbowDepthReconstructor()
        val estimate = calibrateThen(reconstructor, trueAngleDeg = 70.0, finalAzimuthDeg = 0.0)

        assertNotNull(estimate)
        // Nothing is out of plane, so both sign hypotheses collapse onto the same answer.
        assertTrue(abs(estimate.angleDeg - 70.0) < 2.0, "got ${estimate.angleDeg}")
        assertTrue(estimate.hypothesisSpreadDeg < 1.0)
        assertTrue(estimate.confidence > 0.99f)
    }

    /**
     * The whole point of WP-25: with the forearm swung straight at the lens, the projection is
     * badly foreshortened and the 2D angle is simply wrong — but the bone length has not
     * changed, so the missing depth is recoverable.
     */
    @Test
    fun foreshortenedForearm_recoversTrueAngle_whereProjectionFails() {
        val trueAngle = 70.0
        val reconstructor = ElbowDepthReconstructor()

        val estimate = calibrateThen(reconstructor, trueAngleDeg = trueAngle, finalAzimuthDeg = 90.0)

        assertNotNull(estimate)
        val projected = projectedAngleDeg(trueAngle, azimuthDeg = 90.0)
        val projectionError = abs(projected - trueAngle)
        val reconstructionError = abs(estimate.angleDeg - trueAngle)

        assertTrue(projectionError > 15.0, "fixture is not foreshortened enough: $projectionError")
        assertTrue(
            reconstructionError < projectionError / 3.0,
            "reconstruction $reconstructionError° did not beat projection $projectionError°",
        )
    }

    /**
     * With one segment in the image plane the two sign hypotheses give the *same* angle — the
     * ambiguity is in the 3D pose, not in the joint. A real ambiguity needs both segments out
     * of plane, and then the estimator must say so instead of pretending to know.
     */
    @Test
    fun signAmbiguity_lowersConfidence_whenBothSegmentsLeaveTheImagePlane() {
        val reconstructor = ElbowDepthReconstructor()
        calibrate(reconstructor, trueAngleDeg = 70.0)

        val estimate = feed(
            reconstructor,
            trueAngleDeg = 70.0,
            azimuthDeg = 90.0,
            ts = CALIBRATION_FRAMES * 33L,
            previousAngleDeg = AMBIGUOUS_ALIGNED_DEG,
            upperArmTiltDeg = AMBIGUOUS_TILT_DEG,
        )

        assertNotNull(estimate)
        assertTrue(estimate.hypothesisSpreadDeg > 10.0, "expected a real ambiguity to report")
        assertTrue(estimate.confidence < 1f)
    }

    @Test
    fun temporalContinuity_picksTheHypothesisNearTheLastAnswer() {
        val reconstructor = ElbowDepthReconstructor()
        calibrate(reconstructor, trueAngleDeg = 70.0)
        val ts = CALIBRATION_FRAMES * 33L

        val near = feed(
            reconstructor, 70.0, azimuthDeg = 90.0, ts = ts,
            previousAngleDeg = AMBIGUOUS_ALIGNED_DEG, upperArmTiltDeg = AMBIGUOUS_TILT_DEG,
        )
        val far = feed(
            reconstructor, 70.0, azimuthDeg = 90.0, ts = ts + 33L,
            previousAngleDeg = 170.0, upperArmTiltDeg = AMBIGUOUS_TILT_DEG,
        )

        assertNotNull(near)
        assertNotNull(far)
        assertTrue(
            abs(near.angleDeg - AMBIGUOUS_ALIGNED_DEG) < abs(far.angleDeg - AMBIGUOUS_ALIGNED_DEG),
            "continuity did not steer the sign choice: near=${near.angleDeg} far=${far.angleDeg}",
        )
    }

    /** Normalising by shoulder width is what makes this independent of user distance. */
    @Test
    fun result_isInvariantToUserDistanceFromCamera() {
        val near = ElbowDepthReconstructor()
        val far = ElbowDepthReconstructor()

        val nearEstimate = calibrateThen(near, 70.0, finalAzimuthDeg = 90.0, scale = 1f)
        val farEstimate = calibrateThen(far, 70.0, finalAzimuthDeg = 90.0, scale = 0.35f)

        assertNotNull(nearEstimate)
        assertNotNull(farEstimate)
        assertEquals(nearEstimate.angleDeg, farEstimate.angleDeg, absoluteTolerance = 0.5)
    }

    @Test
    fun reset_forcesRecalibration() {
        val reconstructor = ElbowDepthReconstructor()
        calibrate(reconstructor, trueAngleDeg = 70.0)
        assertTrue(reconstructor.isMature(LEFT))

        reconstructor.reset()

        assertTrue(!reconstructor.isMature(LEFT))
        assertNull(feed(reconstructor, 70.0, azimuthDeg = 0.0, ts = 0L))
    }

    private companion object {
        const val LEFT = 0
        const val CALIBRATION_FRAMES = 90

        /** Upper-arm tilt that puts *both* segments out of plane, creating a real sign ambiguity. */
        const val AMBIGUOUS_TILT_DEG = 40.0

        /** Angle of the "both segments lean the same way" hypothesis for that fixture. */
        const val AMBIGUOUS_ALIGNED_DEG = 30.0

        /**
         * Ground-truth pose. Upper arm lies in the image plane; the forearm sits at
         * `trueAngle` from it and is swung out of plane by `azimuth`, which changes the
         * projection while leaving the real joint angle untouched.
         */
        fun armGeometry(trueAngleDeg: Double, azimuthDeg: Double): Triple<Double, Double, Double> {
            val theta = trueAngleDeg * PI / 180.0
            val phi = azimuthDeg * PI / 180.0
            return Triple(sin(theta) * cos(phi), cos(theta), sin(theta) * sin(phi))
        }

        fun projectedAngleDeg(trueAngleDeg: Double, azimuthDeg: Double): Double {
            val (fx, fy, _) = armGeometry(trueAngleDeg, azimuthDeg)
            val magnitude = sqrt(fx * fx + fy * fy)
            if (magnitude < 1e-9) return 0.0
            return acos((fy / magnitude).coerceIn(-1.0, 1.0)) * 180.0 / PI
        }

        fun feed(
            reconstructor: ElbowDepthReconstructor,
            trueAngleDeg: Double,
            azimuthDeg: Double,
            ts: Long,
            previousAngleDeg: Double = Double.NaN,
            scale: Float = 1f,
            upperArmTiltDeg: Double = 0.0,
        ): ElbowDepthEstimate? {
            val (fx, fy, fz) = armGeometry(trueAngleDeg, azimuthDeg)
            // Elbow at the origin. The upper arm runs along +y, tilted out of plane by
            // `upperArmTiltDeg` (0 during calibration, so its full length is observed).
            val tilt = upperArmTiltDeg * PI / 180.0
            return reconstructor.estimate(
                side = LEFT,
                shoulderX = 0f,
                shoulderY = (cos(tilt) * scale).toFloat(),
                elbowX = 0f,
                elbowY = 0f,
                wristX = (fx * scale).toFloat(),
                wristY = (fy * scale).toFloat(),
                torsoScale = scale,
                worldUaDz = sin(tilt).toFloat(),
                worldFaDz = fz.toFloat(),
                previousAngleDeg = previousAngleDeg,
                timestampMs = ts,
            )
        }

        /** Runs the in-plane frames the estimator needs to learn each bone's true length. */
        fun calibrate(
            reconstructor: ElbowDepthReconstructor,
            trueAngleDeg: Double,
            scale: Float = 1f,
        ) {
            repeat(CALIBRATION_FRAMES) { frame ->
                feed(
                    reconstructor,
                    trueAngleDeg = trueAngleDeg,
                    azimuthDeg = 0.0,
                    ts = frame * 33L,
                    previousAngleDeg = if (frame == 0) Double.NaN else trueAngleDeg,
                    scale = scale,
                )
            }
        }

        fun calibrateThen(
            reconstructor: ElbowDepthReconstructor,
            trueAngleDeg: Double,
            finalAzimuthDeg: Double,
            scale: Float = 1f,
        ): ElbowDepthEstimate? {
            calibrate(reconstructor, trueAngleDeg, scale)
            return feed(
                reconstructor,
                trueAngleDeg = trueAngleDeg,
                azimuthDeg = finalAzimuthDeg,
                ts = CALIBRATION_FRAMES * 33L,
                previousAngleDeg = trueAngleDeg,
                scale = scale,
            )
        }
    }
}
