package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostChannelsAndGateTest {

    @Test
    fun calculateAngles_fillsCrossWristNeckSpine() {
        val landmarks = visiblePose()
        val world = visiblePose()
        placeShouldersAndHips(world)
        placeShouldersAndHips(landmarks)

        val angles = PoseFrameAssembler.calculateAngles(
            landmarks = VirtualLandmarks.ensureAppended(landmarks),
            worldLandmarks = world,
        )
        assertNotNull(angles.leftShoulderCross)
        assertNotNull(angles.rightShoulderCross)
        assertNotNull(angles.leftHipCross)
        assertNotNull(angles.rightHipCross)
        assertNotNull(angles.leftWrist)
        assertNotNull(angles.rightWrist)
        assertNotNull(angles.neckLeft)
        assertNotNull(angles.neckRight)
        assertNotNull(angles.neckSpine)
        assertNotNull(angles.spine)
    }

    @Test
    fun f2_gatesOnNormalizedVisibility_notWorldVisibility() {
        val landmarks = visiblePose()
        val world = visiblePose()
        placeBentLeftKnee(world)
        placeBentLeftKnee(landmarks)
        world[PoseLandmarkIndices.LEFT_KNEE] =
            world[PoseLandmarkIndices.LEFT_KNEE].copy(visibility = 0.05f)

        val angles = PoseFrameAssembler.calculateAngles(
            landmarks = landmarks,
            worldLandmarks = world,
            visibilityThreshold = 0.5f,
        )
        val expected3D = JointAngleCalculator.angleDegrees3D(
            PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
        )
        assertEquals(expected3D!!, angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    @Test
    fun aspectYScale_identityWhenUnknown() {
        assertEquals(1f, PoseFrameAssembler.aspectYScale(0, 480))
        assertEquals(0.75f, PoseFrameAssembler.aspectYScale(640, 480), absoluteTolerance = 0.001f)
    }

    private fun visiblePose(): MutableList<Landmark> =
        MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }

    private fun placeShouldersAndHips(lm: MutableList<Landmark>) {
        lm[PoseLandmarkIndices.NOSE] = Landmark(0.5f, 0.2f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.4f, 0.3f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.6f, 0.3f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.35f, 0.4f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.RIGHT_ELBOW] = Landmark(0.65f, 0.4f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.3f, 0.5f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.RIGHT_WRIST] = Landmark(0.7f, 0.5f, 0f, 1f, 1f)
        lm[19] = Landmark(0.28f, 0.55f, 0f, 1f, 1f)
        lm[20] = Landmark(0.72f, 0.55f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.42f, 0.55f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.58f, 0.55f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.42f, 0.7f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.58f, 0.7f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.42f, 0.85f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.58f, 0.85f, 0f, 1f, 1f)
    }

    private fun placeBentLeftKnee(lm: MutableList<Landmark>) {
        lm[PoseLandmarkIndices.LEFT_HIP] = Landmark(0f, 0f, 0f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0f, -0.4f, 0.35f, 1f, 1f)
        lm[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0f, -0.8f, 0f, 1f, 1f)
    }
}
