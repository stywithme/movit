package com.movit.core.training.session

import com.movit.core.training.blueprint.ExerciseBlueprintRegistry
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveExerciseRunnerTest {

    @Test
    fun squatCycle_countsRepsFromSyntheticFrames() {
        val blueprint = ExerciseBlueprintRegistry.resolve("bodyweight-squat")!!
        var now = 0L
        val runner = LiveExerciseRunner(
            blueprint = blueprint,
            targetReps = 2,
            timeProvider = { now },
        )
        var lastReps = 0
        runner.onMetrics = { lastReps = it.repCount }
        runner.start()

        // One full up-down-up cycle (mirrors PhaseStateMachineTest timing).
        now = 0L
        runner.processFrame(frameWithKneeAngle(170.0, timestamp = now))
        now = 200L
        runner.processFrame(frameWithKneeAngle(100.0, timestamp = now))
        now = 400L
        runner.processFrame(frameWithKneeAngle(90.0, timestamp = now))
        now = 600L
        runner.processFrame(frameWithKneeAngle(120.0, timestamp = now))
        now = 1_000L
        runner.processFrame(frameWithKneeAngle(170.0, timestamp = now))

        assertTrue(lastReps >= 1, "expected at least one counted rep, got $lastReps")
    }

    private fun frameWithKneeAngle(kneeAngle: Double, timestamp: Long): PoseFrame {
        val landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }.toMutableList()
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        val frame = PoseFrameAssembler.assemble(landmarks, timestamp, isFrontCamera = true)
        return frame.copy(
            angles = frame.angles.copy(
                leftKnee = kneeAngle,
                rightKnee = kneeAngle,
            ),
        )
    }
}
