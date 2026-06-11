package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.engine.Phase
import com.movit.core.training.testing.FrameFixture
import com.movit.core.training.testing.ParityRunner
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertTrue

class MovitTrainingEngineParityTest {
    @Test
    fun squatGoldenReplay_isSelfConsistent() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val fixture = squatCycleFixture()
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 2)
    }

    @Test
    fun plankHoldGoldenReplay_entersHoldPhase() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("plank.json"))
        val fixture = FrameFixture.fromAngleSequence(
            name = "plank-hold",
            anglesByFrame = List(12) { mapOf("left_hip" to 170.0) },
            startTimestampMs = 1_000L,
            stepMs = 250L,
        )
        val result = ParityRunner.run(config, fixture, targetReps = 1)
        assertTrue(result.isHoldExercise)
        assertTrue(
            result.traces.any { it.holdActive || it.phase == Phase.COUNT },
            "expected hold/COUNT phase in trace: ${result.traces}",
        )
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 1)
    }

    @Test
    fun bilateralShoulderGoldenReplay_isSelfConsistent() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("bilateral-shoulder-mobility.json"))
        val fixture = FrameFixture.fromAngleSequence(
            name = "shoulder-raise",
            anglesByFrame = listOf(
                mapOf("right_shoulder" to 60.0),
                mapOf("right_shoulder" to 90.0),
                mapOf("right_shoulder" to 150.0),
                mapOf("right_shoulder" to 160.0),
                mapOf("right_shoulder" to 70.0),
                mapOf("right_shoulder" to 60.0),
            ),
            startTimestampMs = 0L,
            stepMs = 400L,
        )
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 2)
    }

    @Test
    fun positionChecksDeskGoldenReplay_isSelfConsistent() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("position-checks-desk.json"))
        val fixture = FrameFixture.fromAngleSequence(
            name = "desk-seated",
            anglesByFrame = listOf(
                mapOf("left_elbow" to 90.0),
                mapOf("left_elbow" to 95.0),
                mapOf("left_elbow" to 100.0),
            ),
            startTimestampMs = 500L,
            stepMs = 300L,
        )
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 1)
    }

    private fun squatCycleFixture(): FrameFixture = FrameFixture.fromAngleSequence(
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
}
