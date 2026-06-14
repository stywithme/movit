package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Parity with legacy [com.trainingvalidator.poc.analysis.AngleCalculator.calculateAllAnglesSmoothed]
 * when world landmarks are supplied with use3D=true.
 */
class PoseFrameAssemblerTest {

    @Test
    fun limbAngles_use3DWorldWhenAvailable() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee2DProjection(landmarks)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = world)
        val expected3D = JointAngleCalculator.angleDegrees3D(
            pointA = PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
            pointB = PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            pointC = PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
        )
        val projected2D = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_HIP].x, landmarks[PoseLandmarkIndices.LEFT_HIP].y),
            pointB = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_KNEE].x, landmarks[PoseLandmarkIndices.LEFT_KNEE].y),
            pointC = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_ANKLE].x, landmarks[PoseLandmarkIndices.LEFT_ANKLE].y),
        )

        assertNotEquals(projected2D, expected3D, absoluteTolerance = 1.0)
        assertEquals(expected3D, angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    @Test
    fun shoulderHipAnkle_use3DWorldWhenAvailable() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeStandingPose3D(world)
        placeStandingPose2DProjection(landmarks)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = world)

        assertEquals(
            JointAngleCalculator.angleDegrees3D(
                PosePoint3D(world[PoseLandmarkIndices.LEFT_ELBOW].x, world[PoseLandmarkIndices.LEFT_ELBOW].y, world[PoseLandmarkIndices.LEFT_ELBOW].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_SHOULDER].x, world[PoseLandmarkIndices.LEFT_SHOULDER].y, world[PoseLandmarkIndices.LEFT_SHOULDER].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
            ),
            angles.leftShoulder!!,
            absoluteTolerance = 0.01,
        )
        assertEquals(
            JointAngleCalculator.angleDegrees3D(
                PosePoint3D(world[PoseLandmarkIndices.LEFT_SHOULDER].x, world[PoseLandmarkIndices.LEFT_SHOULDER].y, world[PoseLandmarkIndices.LEFT_SHOULDER].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            ),
            angles.leftHip!!,
            absoluteTolerance = 0.01,
        )
        assertEquals(
            JointAngleCalculator.angleDegrees3D(
                PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_FOOT_INDEX].x, world[PoseLandmarkIndices.LEFT_FOOT_INDEX].y, world[PoseLandmarkIndices.LEFT_FOOT_INDEX].z),
            ),
            angles.leftAnkle!!,
            absoluteTolerance = 0.01,
        )
    }

    @Test
    fun limbAngles_fallbackTo2D_whenWorldNull() {
        val landmarks = visibleLandmarks()
        placeBentLeftKnee2DProjection(landmarks)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = null)
        val expected2D = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_HIP].x, landmarks[PoseLandmarkIndices.LEFT_HIP].y),
            pointB = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_KNEE].x, landmarks[PoseLandmarkIndices.LEFT_KNEE].y),
            pointC = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_ANKLE].x, landmarks[PoseLandmarkIndices.LEFT_ANKLE].y),
        )

        assertEquals(expected2D, angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    @Test
    fun limbAngles_fallbackTo2D_whenWorldLandmarkNotVisible() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee2DProjection(landmarks)
        world[PoseLandmarkIndices.LEFT_KNEE] = world[PoseLandmarkIndices.LEFT_KNEE].copy(visibility = 0.1f)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = world)
        val expected2D = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_HIP].x, landmarks[PoseLandmarkIndices.LEFT_HIP].y),
            pointB = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_KNEE].x, landmarks[PoseLandmarkIndices.LEFT_KNEE].y),
            pointC = PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_ANKLE].x, landmarks[PoseLandmarkIndices.LEFT_ANKLE].y),
        )

        assertEquals(expected2D, angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    @Test
    fun mirrorAngles_restoresAnatomicalLimbLabels_afterFrontCameraCalculation() {
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        // Front camera: MediaPipe LEFT indices carry the person's right (straight) leg.
        placeStraightRightLeg3DAtLeftSide(world)
        placeStraightRightLeg3DAtLeftSide(norm)
        placeBentLeftKnee3DAtRightSide(world)
        placeBentLeftKnee3DAtRightSide(norm)

        val rawFrontAngles = PoseFrameAssembler.calculateAngles(norm, worldLandmarks = world)
        val anatomical = PoseLandmarkMirroring.mirrorAngles(rawFrontAngles)
        val expectedBentKnee = JointAngleCalculator.angleDegrees3D(
            PosePoint3D(world[PoseLandmarkIndices.RIGHT_HIP].x, world[PoseLandmarkIndices.RIGHT_HIP].y, world[PoseLandmarkIndices.RIGHT_HIP].z),
            PosePoint3D(world[PoseLandmarkIndices.RIGHT_KNEE].x, world[PoseLandmarkIndices.RIGHT_KNEE].y, world[PoseLandmarkIndices.RIGHT_KNEE].z),
            PosePoint3D(world[PoseLandmarkIndices.RIGHT_ANKLE].x, world[PoseLandmarkIndices.RIGHT_ANKLE].y, world[PoseLandmarkIndices.RIGHT_ANKLE].z),
        )

        assertAngleEquals(expectedBentKnee, anatomical.leftKnee)
        assertAngleEquals(180.0, anatomical.rightKnee)
    }

    @Test
    fun poseFrameMirrored_appliesAngleSwap_only() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee3D(landmarks)

        val frame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 1_000L,
            isFrontCamera = true,
            worldLandmarks = world,
            applyElbowCorrection = false,
        )
        val mirrored = frame.mirrored()

        assertEquals(frame.landmarks, mirrored.landmarks)
        assertEquals(frame.angles.leftKnee, mirrored.angles.rightKnee)
        assertEquals(frame.angles.rightKnee, mirrored.angles.leftKnee)
        assertEquals(false, mirrored.isFrontCamera)
    }

    private fun assertAngleEquals(expected: Double?, actual: Double?) {
        assertNotNull(expected, "expected angle should not be null")
        assertNotNull(actual, "actual angle should not be null")
        assertEquals(expected!!, actual!!, absoluteTolerance = 0.01)
    }

    @Test
    fun worldLandmarksShorterThanNorm_fallsBackTo2D() {
        val landmarks = visibleLandmarks()
        placeBentLeftKnee2DProjection(landmarks)
        val shortWorld = visibleLandmarks().take(20)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = shortWorld)
        assertNotNull(angles.leftKnee)
    }

    private fun visibleLandmarks(): MutableList<Landmark> =
        MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }

    private fun placeBentLeftKnee3D(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.2f, 0.5f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.4f, 0.3f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0f, -0.4f, 0.35f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0f, -0.8f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_FOOT_INDEX] = Landmark(0f, -0.85f, 0.1f, 1f, 1f)
    }

    private fun placeBentLeftKnee2DProjection(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.40f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.55f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.72f, 0f, 1f, 1f)
    }

    private fun placeStraightRightLeg3DAtLeftSide(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.2f, 0.5f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.4f, 0.3f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.2f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.2f, -0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.2f, -0.9f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_FOOT_INDEX] = Landmark(0.2f, -0.95f, 0f, 1f, 1f)
    }

    private fun placeBentLeftKnee3DAtRightSide(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(-0.2f, 0.5f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ELBOW] = Landmark(-0.4f, 0.3f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0f, -0.4f, 0.35f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0f, -0.8f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_FOOT_INDEX] = Landmark(0f, -0.85f, 0.1f, 1f, 1f)
    }

    private fun placeStraightRightLeg3D(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.2f, 0.5f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ELBOW] = Landmark(0.4f, 0.3f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.2f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.2f, -0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.2f, -0.9f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_FOOT_INDEX] = Landmark(0.2f, -0.95f, 0f, 1f, 1f)
    }

    private fun placeStandingPose3D(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.2f, 0.5f, 0.05f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.45f, 0.35f, 0.1f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(-0.15f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(-0.15f, -0.45f, 0.08f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(-0.15f, -0.9f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_FOOT_INDEX] = Landmark(-0.15f, -0.95f, 0.12f, 1f, 1f)
    }

    private fun placeStandingPose2DProjection(landmarks: MutableList<Landmark>) {
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.42f, 0.28f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.35f, 0.38f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.44f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.44f, 0.62f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.44f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_FOOT_INDEX] = Landmark(0.44f, 0.82f, 0f, 1f, 1f)
    }
}
