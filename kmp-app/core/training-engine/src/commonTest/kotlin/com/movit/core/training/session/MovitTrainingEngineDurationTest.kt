package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class MovitTrainingEngineDurationTest {
    @Test
    fun stopDuration_excludesManualPauseTime() {
        var wall = 1_000L
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val engine = MovitTrainingEngine(config, wallClock = { wall })

        engine.start()
        engine.processFrame(sampleFrame(timestampMs = 1_000L))
        wall = 4_000L
        engine.processFrame(sampleFrame(timestampMs = 4_000L))
        wall = 5_000L
        engine.pause()
        wall = 9_000L
        engine.resume()
        wall = 11_000L
        engine.processFrame(sampleFrame(timestampMs = 11_000L))

        val summary = engine.stop()
        assertEquals(6_000L, summary.durationMs)
    }

    @Test
    fun pauseResume_forwardsToSessionClock() {
        var wall = 2_000L
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val engine = MovitTrainingEngine(config, wallClock = { wall })

        engine.start()
        engine.processFrame(sampleFrame(timestampMs = 2_000L))
        wall = 6_000L
        engine.processFrame(sampleFrame(timestampMs = 6_000L))
        wall = 7_000L
        engine.pause()
        wall = 10_000L
        engine.resume()
        wall = 12_000L
        engine.processFrame(sampleFrame(timestampMs = 12_000L))

        val summary = engine.stop()
        assertEquals(7_000L, summary.durationMs)
    }

    private fun sampleFrame(timestampMs: Long) = PoseFrame(
        angles = JointAngles(leftKnee = 170.0),
        landmarks = listOf(Landmark(0.5f, 0.5f, 0f, 1f, 1f)),
        isFrontCamera = false,
        timestampMs = timestampMs,
    )
}
