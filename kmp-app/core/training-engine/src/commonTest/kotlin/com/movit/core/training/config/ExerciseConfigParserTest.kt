package com.movit.core.training.config

import com.movit.core.training.engine.CountingMethod
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseConfigParserTest {

    @Test
    fun squatFixture_parsesPrimaryJointsAndCounting() {
        val json = readExerciseFixture("squat.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertEquals(CountingMethod.UP_DOWN, config.countingMethod)
        assertTrue(config.getPrimaryJoints().size >= 2)
        assertTrue(config.validationIssues().isEmpty())
    }

    @Test
    fun plankFixture_parsesHoldExercise() {
        val json = readExerciseFixture("plank.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertEquals(CountingMethod.HOLD, config.countingMethod)
        assertTrue(config.isHoldExercise())
    }

    @Test
    fun bilateralFixture_parsesBilateralFlag() {
        val json = readExerciseFixture("bilateral-shoulder-mobility.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertTrue(config.isBilateral)
    }

    @Test
    fun positionChecksFixture_hasPositionChecks() {
        val json = readExerciseFixture("position-checks-desk.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertTrue(config.hasAnyPositionChecks())
        assertTrue(config.poseVariants.first().positionChecks.isNotEmpty())
    }

}
