package com.movit.core.training.session

import com.movit.core.training.engine.Phase
import com.movit.core.training.testing.FrameFixture
import com.movit.core.training.testing.ParityRunner
import com.movit.core.training.testing.parseParityConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * G2 — frozen parity harness on recorded [parity_*.json] fixtures (KMP-only).
 * Replaces legacy [com.movit.training.engine.testing.TrainingEngineParityTest].
 */
class FrozenParityFixtureTest {

    @Test
    fun squat_selfConsistent() {
        val config = parseParityConfig("parity_squat.json")
        val fixture = squatSequence()
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 3)
        val r1 = ParityRunner.run(config, fixture, targetReps = 3)
        val r2 = ParityRunner.run(config, fixture, targetReps = 3)
        assertTrue(r1.traces.map { it.comparable() } == r2.traces.map { it.comparable() })
    }

    @Test
    fun curl_selfConsistent() {
        val config = parseParityConfig("parity_curl.json")
        ParityRunner.assertSelfConsistent(config, curlSequence(), targetReps = 2)
    }

    @Test
    fun plank_selfConsistent() {
        val config = parseParityConfig("parity_plank.json")
        val fixture = plankSequence()
        val result = ParityRunner.run(config, fixture, targetReps = 1)
        assertTrue(result.isHoldExercise)
        assertTrue(
            result.traces.any { it.holdActive || it.phase == Phase.COUNT },
            "expected hold/COUNT phase in trace: ${result.traces}",
        )
        ParityRunner.assertSelfConsistent(config, fixture, targetReps = 1)
    }

    @Test
    fun visibility_selfConsistent() {
        val config = parseParityConfig("parity_visibility.json")
        ParityRunner.assertSelfConsistent(config, visibilitySequence(), targetReps = 1)
    }

    @Test
    fun position_selfConsistent() {
        val config = parseParityConfig("parity_position.json")
        ParityRunner.assertSelfConsistent(config, positionSequence(), targetReps = 1)
    }

    private fun squatSequence(): FrameFixture = FrameFixture.fromAngleSequence(
        name = "squat-angles",
        isFrontCamera = false,
        anglesByFrame = listOf(175.0, 170.0, 75.0, 75.0, 80.0, 175.0, 175.0, 72.0, 170.0)
            .map { mapOf("right_knee" to it, "left_knee" to it) },
        startTimestampMs = 200L,
        stepMs = 200L,
    )

    private fun curlSequence(): FrameFixture = FrameFixture.fromAngleSequence(
        name = "curl-angles",
        isFrontCamera = true,
        anglesByFrame = listOf(165.0, 165.0, 45.0, 50.0, 160.0, 50.0, 165.0)
            .map { mapOf("left_elbow" to it, "right_elbow" to 175.0) },
        startTimestampMs = 200L,
        stepMs = 200L,
    )

    private fun plankSequence(): FrameFixture = FrameFixture.fromAngleSequence(
        name = "plank-angles",
        isFrontCamera = true,
        anglesByFrame = List(13) { mapOf("right_elbow" to 90.0, "left_elbow" to 90.0) },
        startTimestampMs = 250L,
        stepMs = 250L,
    )

    private fun visibilitySequence(): FrameFixture = FrameFixture.fromAngleSequence(
        name = "visibility-angles",
        isFrontCamera = false,
        anglesByFrame = listOf(175.0, 170.0, 75.0, 75.0, 80.0, 175.0, 170.0)
            .map { mapOf("left_knee" to it, "right_knee" to 175.0) },
        startTimestampMs = 200L,
        stepMs = 200L,
    )

    private fun positionSequence(): FrameFixture = FrameFixture.fromAngleSequence(
        name = "position-angles",
        isFrontCamera = true,
        anglesByFrame = List(9) { mapOf("right_elbow" to 100.0, "left_elbow" to 100.0) },
        startTimestampMs = 300L,
        stepMs = 300L,
    )
}
