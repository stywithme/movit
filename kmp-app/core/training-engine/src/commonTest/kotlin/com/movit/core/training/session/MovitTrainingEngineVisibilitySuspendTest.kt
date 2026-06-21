package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.engine.Phase
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.testing.readExerciseFixture
import com.movit.core.training.visibility.VisibilityCheckResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovitTrainingEngineVisibilitySuspendTest {
    @Test
    fun visibilityWarning_skipsRepCounting() {
        var now = 0L
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val engine = MovitTrainingEngine(
            exerciseConfig = config,
            targetRepsOverride = 5,
            timingPolicy = com.movit.core.training.engine.policy.TimingPolicy(
                visibilityGraceDurationMs = 0L,
                visibilityWarningDurationMs = 1_000L,
                visibilityPauseAfterMs = 5_000L,
            ),
            wallClock = { now },
        )
        engine.start()

        var reps = 0
        engine.onRepCountChanged = { count, _, _ -> reps = count }

        val visibleLandmarks = visibleSquatLandmarks()
        val hiddenLandmarks = visibleSquatLandmarks().map {
            it.copy(visibility = 0.01f, presence = 0.01f)
        }

        fun frame(knee: Double, landmarks: List<Landmark>, ts: Long) = PoseFrame(
            angles = JointAngles(leftKnee = knee, rightKnee = knee),
            landmarks = landmarks,
            isFrontCamera = false,
            timestampMs = ts,
        )

        now = 100L
        engine.processFrame(frame(170.0, visibleLandmarks, now))
        now = 300L
        engine.processFrame(frame(90.0, visibleLandmarks, now))
        now = 500L
        engine.processFrame(frame(170.0, visibleLandmarks, now))
        val repsBeforeWarning = reps

        now = 1_600L
        engine.processFrame(frame(90.0, hiddenLandmarks, now))
        now = 2_700L
        engine.processFrame(frame(90.0, hiddenLandmarks, now))
        assertTrue(engine.testPauseController().isCountingSuspended)

        now = 2_900L
        engine.processFrame(frame(170.0, hiddenLandmarks, now))

        assertEquals(repsBeforeWarning, reps, "rep count must not advance during visibility warning")
    }

    @Test
    fun resume_clearsVisibilityPauseFlags() {
        var now = 0L
        val engine = MovitTrainingEngine(
            exerciseConfig = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json")),
            wallClock = { now },
        )
        engine.start()
        val pauseController = engine.testPauseController()
        pauseController.processVisibilityResult(
            result = VisibilityCheckResult.PauseTraining(
                savedRepCount = 0,
                savedPhase = Phase.IDLE,
                invisibleJoints = listOf("left_knee"),
            ),
            emit = {},
            onAutoResumeComplete = {},
        )
        assertTrue(pauseController.isVisibilityPaused)

        engine.resume()
        assertFalse(pauseController.isVisibilityPaused)
        assertFalse(pauseController.isCountingSuspended)
    }

    private fun visibleSquatLandmarks(): List<Landmark> {
        val landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }.toMutableList()
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_HIP] =
            Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_KNEE] =
            Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_ANKLE] =
            Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_HIP] =
            Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_KNEE] =
            Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_ANKLE] =
            Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.LEFT_SHOULDER] =
            Landmark(0.45f, 0.30f, 0f, 1f, 1f)
        landmarks[com.movit.core.training.model.PoseLandmarkIndices.RIGHT_SHOULDER] =
            Landmark(0.55f, 0.30f, 0f, 1f, 1f)
        return com.movit.core.training.geometry.VirtualLandmarks.ensureAppended(landmarks)
    }
}
