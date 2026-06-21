package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.engine.RepCounter
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseWorkoutSummaryBuilderTest {
    @Test
    fun build_recordsPoseVariantIndex() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val repCounter = RepCounter(minRepIntervalMs = 500L, targetReps = 12)
        val summary = ExerciseWorkoutSummaryBuilder.build(
            config = config,
            repCounter = repCounter,
            durationMs = 60_000L,
            poseVariantIndex = 2,
        )
        assertEquals(2, summary.poseVariantIndex)
    }
}
