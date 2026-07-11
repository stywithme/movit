package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Ensures debug-lab frame assembly matches the production camera pipeline entry point,
 * and that assemble actually selects 3D limb angles when world landmarks are present (WP-02).
 */
class PoseFrameAssemblerDebugParityTest {
    private val stickyState = AngleModeStickyState()

    @BeforeTest
    fun setUp() {
        stickyState.reset()
    }

    @AfterTest
    fun tearDown() {
        stickyState.reset()
    }

    @Test
    fun assemble_producesSameAngles_asDirectTrainingPath() {
        val landmarks = squatLandmarks()
        val world = squatLandmarks3D()

        val productionFrame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 42L,
            isFrontCamera = false,
            worldLandmarks = world,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
            applyElbowCorrection = false,
            stickyState = stickyState,
        )
        stickyState.reset()
        val debugFrame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 42L,
            isFrontCamera = false,
            worldLandmarks = world,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
            applyElbowCorrection = false,
            stickyState = stickyState,
        )

        assertEquals(productionFrame.angles.leftKnee, debugFrame.angles.leftKnee)
        assertEquals(productionFrame.angles.rightKnee, debugFrame.angles.rightKnee)
        assertEquals(productionFrame.angles.leftElbow, debugFrame.angles.leftElbow)
    }

    @Test
    fun assemble_leftKnee_matchesAngleDegrees3D() {
        val landmarks = squatLandmarks()
        val world = squatLandmarks3D()

        val frame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 42L,
            isFrontCamera = false,
            worldLandmarks = world,
            applyElbowCorrection = false,
            stickyState = stickyState,
        )
        val expected3D = JointAngleCalculator.angleDegrees3D(
            PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
        )
        val projected2D = JointAngleCalculator.angleDegrees(
            PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_HIP].x, landmarks[PoseLandmarkIndices.LEFT_HIP].y),
            PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_KNEE].x, landmarks[PoseLandmarkIndices.LEFT_KNEE].y),
            PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_ANKLE].x, landmarks[PoseLandmarkIndices.LEFT_ANKLE].y),
        )

        assertNotEquals(projected2D, expected3D!!, absoluteTolerance = 1.0)
        assertEquals(expected3D!!, frame.angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    private fun squatLandmarks(): MutableList<Landmark> {
        val landmarks = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.42f, 0.32f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.58f, 0.32f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.NOSE] = Landmark(0.5f, 0.22f, 0f, 1f, 1f)
        return landmarks
    }

    /** World pose with depth so 3D knee angle differs from 2D projection. */
    private fun squatLandmarks3D(): MutableList<Landmark> {
        val landmarks = squatLandmarks()
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0f, -0.4f, 0.35f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0f, -0.8f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.2f, 0f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.2f, -0.4f, 0.35f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.2f, -0.8f, 0f, 1f, 1f)
        return landmarks
    }
}
