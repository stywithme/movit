package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.position.BodyPosture
import com.movit.core.training.position.ExpectedDirection
import com.movit.core.training.position.PositionCheckDebugStatus
import com.movit.core.training.position.VisibleRegion
import com.movit.core.training.session.SetupPhase
import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.config.PositionOperator
import com.movit.core.data.MovitData
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.MovitThemeModeStorage
import com.movit.core.network.MovitJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrainingDebugAnalyzerTest {
    private val analyzer = TrainingDebugAnalyzer()

    @Test
    fun analyze_angleTab_returnsDiagnosticsForAllSelectedJoints() {
        val frame = squatFrame(kneeAngle = 95.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                selectedJoints = setOf("left_knee", "right_knee"),
                activeTab = TrainingDebugTab.ANGLE_DIAGNOSTICS,
            ),
        )

        assertTrue(result.hasPose)
        assertEquals(2, result.angleDiagnostics.size)
        assertEquals(2, result.overlayState.selectedJointHighlights.size)
    }

    @Test
    fun analyze_angleTab_returnsDiagnosticsForSelectedJoint() {
        val frame = squatFrame(kneeAngle = 95.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                selectedJoints = setOf("left_knee"),
                activeTab = TrainingDebugTab.ANGLE_DIAGNOSTICS,
            ),
        )

        assertTrue(result.hasPose)
        assertEquals(1, result.angleDiagnostics.size)
        assertEquals("left_knee", result.angleDiagnostics.first().displayJointCode)
        assertNotNull(result.angleDiagnostics.first().displayedAngle)
        assertEquals(frame.poseFrame.angles.leftKnee, result.angleDiagnostics.first().displayedAngle)
    }

    @Test
    fun analyze_elbowTab_includesElbowDiagnosticsWhenPortWired() {
        val frame = bentElbowFrame()
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                selectedJoints = setOf("left_elbow"),
                activeTab = TrainingDebugTab.ANGLE_DIAGNOSTICS,
            ),
        )

        val elbow = result.angleDiagnostics.first().elbowDiagnostics
        assertNotNull(elbow)
        assertNotNull(elbow.strategy)
    }

    @Test
    fun analyze_positionTab_reportsSyntheticCheckStatus() {
        val frame = squatFrame(kneeAngle = 95.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                activeTab = TrainingDebugTab.POSITION_CHECK,
                positionCheck = DebugPositionCheckConfig(
                    checkType = PositionCheckType.VERTICAL_COMPARISON,
                    primaryLandmark = "left_knee",
                    secondaryLandmark = "left_ankle",
                    operator = PositionOperator.SHOULD_NOT_EXCEED,
                    threshold = 1.0,
                ),
            ),
        )

        assertNotNull(result.positionDebug)
        assertEquals(PositionCheckDebugStatus.PASS, result.positionDebug?.status)
    }

    @Test
    fun analyze_sceneTab_reportsAxisMatch() {
        val frame = squatFrame(kneeAngle = 170.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                activeTab = TrainingDebugTab.CAMERA_SCENE,
                sceneExpectation = DebugSceneExpectationConfig(
                    postures = listOf(BodyPosture.STANDING),
                    directions = listOf(ExpectedDirection.FRONT),
                    regions = listOf(VisibleRegion.FULL_BODY),
                ),
            ),
        )

        assertNotNull(result.sceneResult)
        assertNotNull(result.axisMatch)
    }

    @Test
    fun analyze_setupGateTab_populatesProbe() {
        val frame = squatFrame(kneeAngle = 90.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(activeTab = TrainingDebugTab.SETUP_GATE),
        )

        assertNotNull(result.setupProbe)
        assertEquals("Probe (squat default)", result.setupExerciseLabel)
        assertFalse(result.setupProbe!!.phase == SetupPhase.ANGLES && result.setupProbe.isConfirmed)
    }

    @Test
    fun analyze_setupGate_unknownSlug_fallsBackToProbe() {
        val frame = squatFrame(kneeAngle = 90.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(activeTab = TrainingDebugTab.SETUP_GATE),
            exerciseSlug = "missing-exercise-slug",
        )

        assertEquals("Probe (squat default)", result.setupExerciseLabel)
    }

    @Test
    fun analyze_setupGate_resolvesExerciseSlugFromTrainingConfig() {
        val platform = TestMovitPlatformBindings()
        val localStore = InMemoryMovitLocalStore()
        MovitData.install(
            platform = platform,
            localStoreFactory = MovitLocalStoreFactory { localStore },
        )
        val squatJson = """
            {
              "id": "ex-1",
              "slug": "bodyweight-squat",
              "name": {"ar": "قرفصاء", "en": "Squat"},
              "countingMethod": "up_down",
              "poseVariants": [{
                "cameraPosition": "front_view",
                "trackedJoints": [{
                  "joint": "left_knee",
                  "role": "primary",
                  "startPose": {"min": 150, "max": 180},
                  "upRange": {"perfect": {"min": 130, "max": 180}},
                  "downRange": {"perfect": {"min": 60, "max": 100}}
                }]
              }]
            }
        """.trimIndent()
        MovitData.trainingConfig.applySyncExercises(
            exercises = listOf(MovitJson.parseToJsonElement(squatJson)),
            isFullSync = true,
        )

        val result = analyzer.analyze(
            frame = squatFrame(kneeAngle = 90.0),
            config = TrainingDebugConfig(activeTab = TrainingDebugTab.SETUP_GATE),
            exerciseSlug = "bodyweight-squat",
        )

        assertEquals("bodyweight-squat", result.setupExerciseLabel)
    }

    @Test
    fun analyze_setupGate_tiltCorrection_usesTiltAwareGate() {
        val tilt = FakeDeviceTiltSource(correctionRadians = 0.1f)
        val frame = squatFrame(kneeAngle = 90.0)
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                activeTab = TrainingDebugTab.SETUP_GATE,
                tiltCorrectionEnabled = true,
            ),
            tiltSource = tilt,
        )

        assertNotNull(result.setupProbe)
        analyzer.resetAnalysisState("tilt toggle")
        val afterReset = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(
                activeTab = TrainingDebugTab.SETUP_GATE,
                tiltCorrectionEnabled = false,
            ),
            tiltSource = tilt,
        )
        assertNotNull(afterReset.setupProbe)
    }

    @Test
    fun resetAnalysisState_clearsElbowHold() {
        val frame = bentElbowFrame()
        analyzer.analyze(frame, TrainingDebugConfig(selectedJoints = setOf("left_elbow")))
        analyzer.resetAnalysisState("test")
        val afterReset = analyzer.analyze(frame, TrainingDebugConfig(selectedJoints = setOf("left_elbow")))
        assertTrue(afterReset.hasPose)
    }

    @Test
    fun analyzerUsesPoseFrameAngles_fromAssembler() {
        val landmarks = squatLandmarks()
        val direct = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = 1_000L,
            isFrontCamera = false,
            worldLandmarks = landmarks,
        )
        val frame = TrainingDebugFrameFactory.fromLandmarks(
            landmarks = landmarks,
            worldLandmarks = landmarks,
            timestampMs = 1_000L,
            isFrontCamera = false,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
        )
        val result = analyzer.analyze(
            frame = frame,
            config = TrainingDebugConfig(selectedJoints = setOf("left_knee")),
        )

        assertEquals(direct.angles.leftKnee, frame.poseFrame.angles.leftKnee)
        assertEquals(direct.angles.leftKnee, result.angleDiagnostics.first().displayedAngle)
    }

    private fun squatFrame(kneeAngle: Double): TrainingDebugFrameInput =
        TrainingDebugFrameFactory.fromLandmarks(
            landmarks = squatLandmarks(),
            worldLandmarks = squatLandmarks(),
            timestampMs = 1_000L,
            isFrontCamera = false,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
        )

    private fun bentElbowFrame(): TrainingDebugFrameInput {
        val landmarks = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.35f, 0.30f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.42f, 0.42f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.50f, 0.55f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.30f, 0f, 1f, 1f)
        val world = landmarks.toMutableList()
        world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(-0.3f, 0.4f, 0f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(-0.1f, 0.2f, 0.15f, 1f, 1f)
        world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(0.1f, 0.35f, 0.25f, 1f, 1f)
        world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.3f, 0.4f, 0f, 1f, 1f)
        return TrainingDebugFrameFactory.fromLandmarks(
            landmarks = landmarks,
            worldLandmarks = world,
            timestampMs = 1_000L,
            isFrontCamera = false,
            analysisImageWidth = 640,
            analysisImageHeight = 480,
        )
    }

    private fun squatLandmarks(): List<Landmark> {
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

    private class FakeDeviceTiltSource(
        override val correctionRadians: Float,
    ) : DeviceTiltPort {
        override val isAvailable: Boolean = true
        override val rollDegrees: Float = correctionRadians * (180f / kotlin.math.PI.toFloat())
    }

    private class TestMovitPlatformBindings : MovitPlatformBindings {
        private val cache = mutableMapOf<String, String>()

        override fun apiBaseUrl(): String = "https://test.movit.local"
        override fun authHeader(): String? = "Bearer test"
        override fun preferredLanguage(): String = "en"
        override fun userDisplayName(fallback: String): String = fallback
        override fun readCache(store: String, key: String): String? = cache["$store::$key"]
        override fun readAllCacheEntries(store: String): Map<String, String> = emptyMap()
        override fun writeCache(store: String, key: String, value: String) {
            cache["$store::$key"] = value
        }
        override fun removeCache(store: String, key: String) {
            cache.remove("$store::$key")
        }
        override fun isProUser(): Boolean = true
        override fun activeUserProgramId(): String? = null
        override fun setActiveUserProgramId(userProgramId: String?) = Unit
        override fun refreshToken(): String? = null
        override fun tokenExpiresAtEpochMs(): Long = 0L
        override fun updateAuthTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) = Unit
        override fun persistAuthSession(snapshot: AuthSessionSnapshot) = Unit
        override fun clearAuthSession() = Unit
        override fun themeMode(): String = MovitThemeModeStorage.SYSTEM
        override fun setThemeMode(mode: String) = Unit
        override fun applyPreferredLanguage(languageCode: String) = Unit
        override fun updateUserSettings(preferredLanguage: String?, voiceFeedback: Boolean?, notifications: Boolean?) = Unit
        override fun clearLegacyUserCaches() = Unit
    }
}
