package com.movit.feature.training

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.position.PositionError
import com.movit.core.training.session.SessionRunState
import com.movit.core.training.session.SetupPhase
import com.movit.designsystem.components.SkeletonJointQuality
import com.movit.designsystem.components.SkeletonOverlayMode
import com.movit.designsystem.components.SkeletonPositionSeverity
import com.movit.designsystem.components.SkeletonSetupDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkeletonOverlayMapperTest {

    @Test
    fun projector_mapsCenterLandmarkToViewCenter() {
        val project = skeletonLandmarkProjector(
            analysisWidth = 640,
            analysisHeight = 480,
            mirrorPreview = false,
        )
        assertNotNull(project)
        val point = project(0.5f, 0.5f, 1080f, 2400f)
        assertEquals(540f, point.x, 0.01f)
        assertEquals(1200f, point.y, 0.01f)
    }

    @Test
    fun projector_returnsNullWhenAnalysisDimensionsMissing() {
        assertEquals(null, skeletonLandmarkProjector(0, 480, mirrorPreview = true))
    }

    @Test
    fun cachedProjector_reusesTransformForSameCanvasSize() {
        val projector = SkeletonLandmarkProjector(640, 480, mirrorPreview = false)
        val first = projector.project(0.5f, 0.5f, 1080f, 2400f)
        val second = projector.project(0.5f, 0.5f, 1080f, 2400f)
        assertEquals(first, second)
    }

    @Test
    fun overlayMode_sceneCheckUntilSetupAngles() {
        assertEquals(
            SkeletonOverlayMode.SCENE_CHECK,
            resolveSkeletonOverlayMode(SessionRunState.SETUP_POSE, SetupPhase.REGION.name),
        )
        assertEquals(
            SkeletonOverlayMode.SETUP_ANGLES,
            resolveSkeletonOverlayMode(SessionRunState.SETUP_POSE, SetupPhase.ANGLES.name),
        )
        assertEquals(
            SkeletonOverlayMode.TRAINING,
            resolveSkeletonOverlayMode(SessionRunState.TRAINING, SetupPhase.ANGLES.name),
        )
    }

    @Test
    fun jointVisuals_markAnySideDimmedJoints() {
        val visuals = mapJointVisuals(
            jointStateInfos = mapOf(
                "left_knee" to JointStateInfo("left_knee", JointState.PERFECT, isPrimary = true),
                "right_knee" to JointStateInfo("right_knee", JointState.WARNING, isPrimary = true),
            ),
            anySideDimmedJointCodes = setOf("left_knee"),
        )
        assertTrue(visuals.getValue("left_knee").dimmed)
        assertEquals(SkeletonJointQuality.WARNING, visuals.getValue("right_knee").quality)
    }

    @Test
    fun positionErrors_mapTopTwoBySeverityWithLandmarkIndices() {
        val marks = mapPositionErrorMarks(
            listOf(
                PositionError(
                    checkId = "tip",
                    type = com.movit.core.training.config.PositionCheckType.HORIZONTAL_ALIGNMENT,
                    severity = CheckSeverity.TIP,
                    message = com.movit.core.training.config.LocalizedText(ar = "x", en = "x"),
                    actualValue = 1.0,
                    threshold = 0.5,
                    landmark1 = "left_knee",
                    landmark2 = "left_ankle",
                ),
                PositionError(
                    checkId = "error",
                    type = com.movit.core.training.config.PositionCheckType.HORIZONTAL_ALIGNMENT,
                    severity = CheckSeverity.ERROR,
                    message = com.movit.core.training.config.LocalizedText(ar = "x", en = "x"),
                    actualValue = 1.0,
                    threshold = 0.5,
                    landmark1 = "right_knee",
                    landmark2 = "right_ankle",
                ),
            ),
        )
        assertEquals(2, marks.size)
        assertEquals(SkeletonPositionSeverity.ERROR, marks.first().severity)
        assertEquals(26, marks.first().landmark1Index)
        assertEquals(28, marks.first().landmark2Index)
    }

    @Test
    fun setupHighlights_mapJointRowsDuringSetupAnglesOnly() {
        val row = SetupJointGuidanceUi(
            jointCode = "left_knee",
            message = "Straighten your knee",
            level = "RED",
            direction = "RAISE",
            currentAngle = 95.0,
            isPrimary = true,
        )
        val parity = buildSkeletonOverlayParityState(
            runState = SessionRunState.SETUP_POSE,
            setupPhase = SetupPhase.ANGLES.name,
            jointStateInfos = emptyMap(),
            anySideDimmedJointCodes = emptySet(),
            positionErrors = emptyList(),
            isBilateralExercise = false,
            isBilateralFlipped = false,
            bilateralSide = null,
            setupJointRows = listOf(row),
            language = "en",
        )
        assertEquals(SkeletonOverlayMode.SETUP_ANGLES, parity.mode)
        assertEquals(1, parity.setupHighlights.size)
        assertEquals(SkeletonSetupDirection.RAISE, parity.setupHighlights.first().direction)
    }

    @Test
    fun bilateralHint_onlyDuringTrainingForBilateralExercises() {
        val parity = buildSkeletonOverlayParityState(
            runState = SessionRunState.TRAINING,
            setupPhase = SetupPhase.ANGLES.name,
            jointStateInfos = emptyMap(),
            anySideDimmedJointCodes = emptySet(),
            positionErrors = emptyList(),
            isBilateralExercise = true,
            isBilateralFlipped = false,
            bilateralSide = com.movit.core.training.bilateral.BilateralSide.LEFT,
            setupJointRows = emptyList(),
            language = "en",
        )
        assertNotNull(parity.bilateralSideHint)
        assertEquals("left", parity.bilateralSideHint?.sideCode)
    }
}
