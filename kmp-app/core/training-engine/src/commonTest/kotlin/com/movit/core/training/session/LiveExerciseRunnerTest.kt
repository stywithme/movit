package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.testing.FrameFixture
import com.movit.core.training.testing.ParityRunner
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveExerciseRunnerTest {

    @Test
    fun squatCycle_countsRepsFromSyntheticFrames() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        var now = 0L
        val runner = LiveExerciseRunner(
            exerciseConfig = config,
            targetReps = 2,
            wallClock = { now },
        )
        var lastReps = 0
        runner.onMetrics = { lastReps = it.repCount }
        runner.start()

        val cycle = listOf(170.0, 170.0, 170.0, 100.0, 90.0, 90.0, 120.0, 170.0, 170.0, 170.0)
        cycle.forEachIndexed { index, angle ->
            now = index * 250L
            runner.processFrame(frameWithKneeAngle(angle, timestamp = now))
        }
        now = 2_500L
        runner.processFrame(frameWithKneeAngle(170.0, timestamp = now))

        assertTrue(lastReps >= 1, "expected at least one counted rep, got $lastReps")
    }

    @Test
    fun squatGoldenReplay_isSelfConsistent() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val fixture = FrameFixture.fromAngleSequence(
            name = "squat-cycle",
            anglesByFrame = listOf(
                mapOf("left_knee" to 170.0),
                mapOf("left_knee" to 100.0),
                mapOf("left_knee" to 90.0),
                mapOf("left_knee" to 120.0),
                mapOf("left_knee" to 170.0),
            ),
            startTimestampMs = 0L,
            stepMs = 200L,
        )
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 2)
    }

    private fun frameWithKneeAngle(kneeAngle: Double, timestamp: Long): PoseFrame {
        val landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }.toMutableList()
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        val frame = PoseFrameAssembler.assemble(landmarks, timestamp, isFrontCamera = false)
        return frame.copy(
            angles = frame.angles.copy(
                leftKnee = kneeAngle,
                rightKnee = kneeAngle,
            ),
        )
    }
}
