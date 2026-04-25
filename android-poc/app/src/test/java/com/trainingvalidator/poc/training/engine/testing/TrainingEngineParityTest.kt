package com.trainingvalidator.poc.training.engine.testing

import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parity / regression harness: same fixture run twice on fresh engines must match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.DEFAULT_MANIFEST_NAME, sdk = [33])
class TrainingEngineParityTest {

    private fun resText(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing resource: $path" }
            .bufferedReader().use { it.readText() }

    private fun loadConfig(resourcePath: String) =
        requireNotNull(ExerciseLoader.loadFromJson(resText(resourcePath))) { "Bad JSON: $resourcePath" }

    private fun buildSquatSequence(): FrameFixture {
        val base = defaultJointAngles()
        // Down-up pattern on right_knee
        val seq = listOf(175.0, 170.0, 75.0, 75.0, 80.0, 175.0, 175.0, 72.0, 170.0)
        return FrameFixture(
            name = "squat-angles",
            isFrontCamera = false,
            frames = seq.mapIndexed { i, k ->
                FrameFixtureFrame(
                    index = i,
                    timestampMs = 200L * (i + 1),
                    angles = base.copy(rightKnee = k, leftKnee = k)
                )
            }
        )
    }

    private fun buildCurlSequence(): FrameFixture {
        val base = defaultJointAngles()
        val seq = listOf(165.0, 165.0, 45.0, 50.0, 160.0, 50.0, 165.0)
        return FrameFixture(
            name = "curl-angles",
            isFrontCamera = true,
            frames = seq.mapIndexed { i, e ->
                FrameFixtureFrame(
                    index = i,
                    timestampMs = 200L * (i + 1),
                    angles = base.copy(leftElbow = e, rightElbow = 175.0)
                )
            }
        )
    }

    private fun buildPlankSequence(): FrameFixture {
        val base = defaultJointAngles()
        return FrameFixture(
            name = "plank-angles",
            isFrontCamera = true,
            frames = (0..12).map { i ->
                FrameFixtureFrame(
                    index = i,
                    timestampMs = 250L * (i + 1),
                    angles = base.copy(rightElbow = 90.0, leftElbow = 90.0)
                )
            }
        )
    }

    private fun buildVisibilitySequence(): FrameFixture {
        val base = defaultJointAngles()
        val seq = listOf(175.0, 170.0, 75.0, 75.0, 80.0, 175.0, 170.0)
        return FrameFixture(
            name = "visibility-angles",
            isFrontCamera = false,
            frames = seq.mapIndexed { i, k ->
                FrameFixtureFrame(
                    index = i,
                    timestampMs = 200L * (i + 1),
                    angles = base.copy(leftKnee = k, rightKnee = 175.0)
                )
            }
        )
    }

    private fun buildPositionSequence(): FrameFixture {
        val base = defaultJointAngles()
        return FrameFixture(
            name = "position-angles",
            isFrontCamera = true,
            frames = (0..8).map { i ->
                FrameFixtureFrame(
                    index = i,
                    timestampMs = 300L * (i + 1),
                    angles = base.copy(rightElbow = 100.0, leftElbow = 100.0)
                )
            }
        )
    }

    @Test
    fun squat_selfConsistent() {
        val config = loadConfig("/fixtures/parity_squat.json")
        val fixture = buildSquatSequence()
        ParityRunner.assertSelfConsistent(
            { TrainingEngine(config) },
            fixture
        )
        val r1 = ParityRunner.run(TrainingEngine(config), fixture)
        val r2 = ParityRunner.run(TrainingEngine(config), fixture)
        assertTrue(ParityRunner.compare(r1.traces, r2.traces))
    }

    @Test
    fun curl_selfConsistent() {
        val config = loadConfig("/fixtures/parity_curl.json")
        val fixture = buildCurlSequence()
        ParityRunner.assertSelfConsistent(
            { TrainingEngine(config) },
            fixture
        )
    }

    @Test
    fun plank_selfConsistent() {
        val config = loadConfig("/fixtures/parity_plank.json")
        val fixture = buildPlankSequence()
        ParityRunner.assertSelfConsistent(
            { TrainingEngine(config) },
            fixture
        )
    }

    @Test
    fun visibility_loss_selfConsistent() {
        val config = loadConfig("/fixtures/parity_visibility.json")
        val fix = buildVisibilitySequence()
        ParityRunner.assertSelfConsistent(
            { TrainingEngine(config) },
            fix
        )
    }

    @Test
    fun position_errors_selfConsistent() {
        val config = loadConfig("/fixtures/parity_position.json")
        val fixture = buildPositionSequence()
        ParityRunner.assertSelfConsistent(
            { TrainingEngine(config) },
            fixture
        )
    }
}
