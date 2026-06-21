package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.ElbowCorrectionStrategy
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AngleDiagnosticsBuilderTest {
    @Test
    fun buildOne_frontCamera_keepsSelectedJointAsDiagnosticSource() {
        val landmarks = visibleLandmarks()
        val angles = JointAngles(leftKnee = 120.0)
        val result = AngleDiagnosticsBuilder.buildOne(
            jointCode = "left_knee",
            angles = angles,
            rawNorm = landmarks,
            smoothedNorm = landmarks,
            rawWorld = landmarks,
            smoothedWorld = landmarks,
            isFrontCamera = true,
        )

        assertEquals("left_knee", result.sourceJointCode)
        assertEquals(listOf(23, 25, 27), result.effectiveIndices)
    }

    @Test
    fun buildOne_frontCamera_keepsGeometryAlignedWithDisplayedAngle() {
        val landmarks = visibleLandmarks()
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.1f, 0.6f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.9f, 0.6f, 0f, 1f, 1f)
        val angles = JointAngles(leftKnee = 120.0)
        val result = AngleDiagnosticsBuilder.buildOne(
            jointCode = "left_knee",
            angles = angles,
            rawNorm = landmarks,
            smoothedNorm = landmarks,
            rawWorld = null,
            smoothedWorld = null,
            isFrontCamera = true,
        )

        assertEquals(PoseLandmarkIndices.LEFT_KNEE, result.effectiveIndices[1])
        assertEquals(0.1f, result.normalizedSmoothed?.pointB?.x)
    }

    @Test
    fun buildAll_includesPipelineSourceLabel() {
        val landmarks = visibleLandmarks()
        val angles = PoseFrameAssembler.calculateAngles(landmarks, worldLandmarks = landmarks)
        val diagnostics = AngleDiagnosticsBuilder.buildAll(
            selectedJoints = setOf("left_knee"),
            angles = angles,
            rawNorm = landmarks,
            smoothedNorm = landmarks,
            rawWorld = landmarks,
            smoothedWorld = landmarks,
            isFrontCamera = false,
            elbowDiagnosticsPort = ElbowDiagnosticsPort.NoOp,
        )

        assertEquals("World XYZ", diagnostics.first().pipelineSourceLabel)
        assertNotNull(diagnostics.first().normalizedSmoothed?.xyzAngle)
    }

    @Test
    fun elbowDiagnosticsAdapter_mapsStrategyCodes() {
        val world = visibleLandmarks()
        val norm = visibleLandmarks()
        world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.05f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.2f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.35f, 0.30f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.45f, 0.30f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.58f, 0.30f, 0f, 1f, 1f)
        norm[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.30f, 0f, 1f, 1f)

        PoseFrameAssembler.assemble(
            landmarks = norm,
            timestampMs = 1_000L,
            isFrontCamera = false,
            worldLandmarks = world,
        )
        val snapshot = PoseFrameAssemblerElbowDiagnostics.snapshotForJoint("left_elbow")

        assertNotNull(snapshot)
        assertEquals(ElbowCorrectionStrategy.STRAIGHT.legacyCode, snapshot.strategy)
    }

    private fun visibleLandmarks(): MutableList<Landmark> =
        MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
}
