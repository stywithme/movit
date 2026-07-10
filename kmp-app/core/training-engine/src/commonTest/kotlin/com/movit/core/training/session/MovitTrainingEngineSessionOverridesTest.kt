package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MovitTrainingEngineSessionOverridesTest {
    @Test
    fun targetDurationOverride_winsOverConfigDuration() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("plank.json"))
        assertEquals(30, config.repCountingConfig.duration)

        val engine = MovitTrainingEngine(
            exerciseConfig = config,
            targetDurationSecondsOverride = 45,
        )
        assertEquals(45_000L, engine.resolvedTargetDurationMs())
    }

    @Test
    fun sessionWeight_reachesStopSummary() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val engine = MovitTrainingEngine(
            exerciseConfig = config,
            sessionWeightKg = 42.5f,
        )
        engine.start()
        val summary = engine.stop()
        assertEquals(42.5f, summary.weightKg)
        assertEquals("kg", summary.weightUnit)
    }

    @Test
    fun missingOverrides_keepConfigDefaults() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("plank.json"))
        val engine = MovitTrainingEngine(exerciseConfig = config)
        assertEquals(30_000L, engine.resolvedTargetDurationMs())
        engine.start()
        val summary = engine.stop()
        assertNull(summary.weightKg)
    }
}
