package com.movit.core.training.geometry

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.LandmarkGroup
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheck
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.config.PositionCondition
import com.movit.core.training.config.PositionOperator
import com.movit.core.training.engine.Phase
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.position.PoseSceneExpectation
import com.movit.core.training.position.PositionCheckDebugStatus
import com.movit.core.training.position.PositionValidator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parity with legacy [com.movit.analysis.AngleCalculator.calculateAllAnglesSmoothed]
 * when world landmarks are supplied with use3D=true.
 */
class PoseFrameAssemblerTest {

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
    fun assemble_with33NormAnd33World_leftKneeEqualsAngleDegrees3D() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee2DProjection(landmarks)

        val frame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 1L,
            isFrontCamera = false,
            worldLandmarks = world,
            applyElbowCorrection = false,
            stickyState = stickyState,
        )
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

        assertNotEquals(projected2D, expected3D!!, absoluteTolerance = 1.0)
        assertEquals(expected3D!!, frame.angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    @Test
    fun stickyMode_requiresThreeFrames_beforeDroppingTo2D() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee2DProjection(landmarks)
        val expected3D = JointAngleCalculator.angleDegrees3D(
            PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
        )
        val expected2D = JointAngleCalculator.angleDegrees(
            PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_HIP].x, landmarks[PoseLandmarkIndices.LEFT_HIP].y),
            PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_KNEE].x, landmarks[PoseLandmarkIndices.LEFT_KNEE].y),
            PosePoint2D(landmarks[PoseLandmarkIndices.LEFT_ANKLE].x, landmarks[PoseLandmarkIndices.LEFT_ANKLE].y),
        )

        // Lock 3D
        val first = PoseFrameAssembler.assemble(
            landmarks, 1L, false, world,
            applyElbowCorrection = false,
            stickyState = stickyState,
        )
        assertEquals(expected3D!!, first.angles.leftKnee!!, absoluteTolerance = 0.01)
        assertTrue(first.angleModeSwitchedJointCodes.isEmpty())

        // F2: world visibility is ignored — drop 3D by withholding world landmarks.
        repeat(2) { i ->
            val frame = PoseFrameAssembler.assemble(
                landmarks, (2 + i).toLong(), false, worldLandmarks = null,
                applyElbowCorrection = false,
                stickyState = stickyState,
            )
            assertEquals(expected2D, frame.angles.leftKnee!!, absoluteTolerance = 0.01)
            assertTrue(frame.angleModeSwitchedJointCodes.isEmpty(), "must not switch before 3 frames")
        }

        // Frame 3 without world: mode switches to 2D
        val switched = PoseFrameAssembler.assemble(
            landmarks, 4L, false, worldLandmarks = null,
            applyElbowCorrection = false,
            stickyState = stickyState,
        )
        assertEquals(expected2D, switched.angles.leftKnee!!, absoluteTolerance = 0.01)
        assertTrue("left_knee" in switched.angleModeSwitchedJointCodes)
    }

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

        assertNotEquals(projected2D, expected3D!!, absoluteTolerance = 1.0)
        assertEquals(expected3D!!, angles.leftKnee!!, absoluteTolerance = 0.01)
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
            )!!,
            angles.leftShoulder!!,
            absoluteTolerance = 0.01,
        )
        assertEquals(
            JointAngleCalculator.angleDegrees3D(
                PosePoint3D(world[PoseLandmarkIndices.LEFT_SHOULDER].x, world[PoseLandmarkIndices.LEFT_SHOULDER].y, world[PoseLandmarkIndices.LEFT_SHOULDER].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            )!!,
            angles.leftHip!!,
            absoluteTolerance = 0.01,
        )
        assertEquals(
            JointAngleCalculator.angleDegrees3D(
                PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
                PosePoint3D(world[PoseLandmarkIndices.LEFT_FOOT_INDEX].x, world[PoseLandmarkIndices.LEFT_FOOT_INDEX].y, world[PoseLandmarkIndices.LEFT_FOOT_INDEX].z),
            )!!,
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
    fun limbAngles_keep3D_whenOnlyWorldVisibilityIsLow_f2() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee2DProjection(landmarks)
        // Fake world visibility (MediaPipe often defaults to 1.0 / stale) must not gate 3D.
        world[PoseLandmarkIndices.LEFT_KNEE] = world[PoseLandmarkIndices.LEFT_KNEE].copy(visibility = 0.1f)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = world)
        val expected3D = JointAngleCalculator.angleDegrees3D(
            PosePoint3D(world[PoseLandmarkIndices.LEFT_HIP].x, world[PoseLandmarkIndices.LEFT_HIP].y, world[PoseLandmarkIndices.LEFT_HIP].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_KNEE].x, world[PoseLandmarkIndices.LEFT_KNEE].y, world[PoseLandmarkIndices.LEFT_KNEE].z),
            PosePoint3D(world[PoseLandmarkIndices.LEFT_ANKLE].x, world[PoseLandmarkIndices.LEFT_ANKLE].y, world[PoseLandmarkIndices.LEFT_ANKLE].z),
        )
        assertEquals(expected3D!!, angles.leftKnee!!, absoluteTolerance = 0.01)
    }

    @Test
    fun limbAngles_fallbackTo2D_whenNormalizedLandmarkNotVisible() {
        val landmarks = visibleLandmarks()
        val world = visibleLandmarks()
        placeBentLeftKnee3D(world)
        placeBentLeftKnee2DProjection(landmarks)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = landmarks[PoseLandmarkIndices.LEFT_KNEE].copy(visibility = 0.1f)

        val angles = PoseFrameAssembler.calculateAngles(landmarks, visibilityThreshold = 0.5f, worldLandmarks = world)
        // Both 3D gate and 2D path require norm visibility → angle unavailable.
        assertEquals(null, angles.leftKnee)
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
        )!!

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

        // D-01: reuse landmark list references (no per-frame copy).
        assertTrue(frame.landmarks === mirrored.landmarks)
        assertTrue(frame.worldLandmarks === mirrored.worldLandmarks)
        assertEquals(frame.angles.leftKnee, mirrored.angles.rightKnee)
        assertEquals(frame.angles.rightKnee, mirrored.angles.leftKnee)
        assertEquals(false, mirrored.isFrontCamera)
    }

    /**
     * WP-08 mirror contract (PF-11 / E-04): front-camera asymmetric L/R visibility —
     * left_elbow angle, visibility, and position check all track the same anatomical side.
     *
     * On front camera, MediaPipe RIGHT indices carry the person's LEFT limb.
     * Engine keeps MediaPipe-indexed landmarks + original isFrontCamera=true for remap.
     */
    @Test
    fun frontCameraMirrorContract_leftElbowAngleVisibilityPosition_sameAnatomicalSide() {
        val landmarks = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 0.1f, 0.1f) }
        // Anatomical LEFT (person) sits on MediaPipe RIGHT indices under front camera.
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.35f, 0.30f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ELBOW] = Landmark(0.30f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_WRIST] = Landmark(0.28f, 0.60f, 0f, 1f, 1f)
        // MediaPipe LEFT (anatomical right) stays low-visibility.
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.65f, 0.30f, 0f, 0.1f, 0.1f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.70f, 0.45f, 0f, 0.1f, 0.1f)
        landmarks[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.72f, 0.60f, 0f, 0.1f, 0.1f)

        val frame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 2_000L,
            isFrontCamera = true,
            worldLandmarks = null,
            applyElbowCorrection = false,
        )
        val working = frame.mirrored()
        assertTrue(frame.landmarks === working.landmarks)

        val expectedAnatomicalLeftElbow = JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(
                landmarks[PoseLandmarkIndices.RIGHT_SHOULDER].x,
                landmarks[PoseLandmarkIndices.RIGHT_SHOULDER].y,
            ),
            pointB = PosePoint2D(
                landmarks[PoseLandmarkIndices.RIGHT_ELBOW].x,
                landmarks[PoseLandmarkIndices.RIGHT_ELBOW].y,
            ),
            pointC = PosePoint2D(
                landmarks[PoseLandmarkIndices.RIGHT_WRIST].x,
                landmarks[PoseLandmarkIndices.RIGHT_WRIST].y,
            ),
        )
        assertAngleEquals(expectedAnatomicalLeftElbow, working.angles.leftElbow)

        // Engine passes original isFrontCamera=true with MediaPipe-indexed landmarks.
        val leftVis = JointLandmarkMapping.computeJointVisibility(
            "left_elbow",
            working.landmarks!!,
            isFrontCamera = true,
        )
        val rightVis = JointLandmarkMapping.computeJointVisibility(
            "right_elbow",
            working.landmarks!!,
            isFrontCamera = true,
        )
        assertTrue(leftVis >= 0.9f, "left_elbow visibility should read anatomical left (MediaPipe right)")
        assertTrue(rightVis < 0.5f, "right_elbow visibility should read anatomical right (MediaPipe left)")

        val validator = PositionValidator(
            positionChecks = listOf(
                PositionCheck(
                    id = "left_elbow_depth",
                    type = PositionCheckType.DEPTH_ALIGNMENT,
                    landmarks = LandmarkGroup(primary = "left_elbow", secondary = "left_wrist"),
                    condition = PositionCondition(
                        operator = PositionOperator.SHOULD_NOT_EXCEED,
                        threshold = 10.0,
                    ),
                    activePhases = listOf("all"),
                    errorMessage = LocalizedText(en = "elbow"),
                    severity = CheckSeverity.TIP,
                    cooldownMs = 0L,
                    minErrorFrames = 1,
                ),
            ),
            sceneExpectation = PoseSceneExpectation.fromLegacyCode("standing_front"),
        )
        // Counting phase so debugChecks are collected (D-04).
        val positionResult = validator.validate(
            working.landmarks!!,
            Phase.DOWN,
            isFrontCamera = true,
        )
        val debug = positionResult.debugChecks.first { it.checkId == "left_elbow_depth" }
        // Front-camera XOR mirrors check names → right_* = MediaPipe right = anatomical left.
        assertEquals("right_elbow", debug.landmark1)
        assertEquals("right_wrist", debug.landmark2)
        assertTrue(
            debug.status == PositionCheckDebugStatus.PASS ||
                debug.status == PositionCheckDebugStatus.FAIL ||
                debug.status == PositionCheckDebugStatus.FAIL_PENDING,
            "check must run against the visible anatomical-left landmarks, not skip for visibility",
        )
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
