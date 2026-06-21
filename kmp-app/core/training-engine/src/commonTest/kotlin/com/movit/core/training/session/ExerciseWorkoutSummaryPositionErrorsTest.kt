package com.movit.core.training.session

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.RepCounter
import com.movit.core.training.engine.ZoneType
import com.movit.core.training.engine.positionErrorRepCount
import com.movit.core.training.position.PositionError
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseWorkoutSummaryPositionErrorsTest {

    @Test
    fun build_exposesPositionErrorSnapshotsInRepDetails() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("knee"))
        counter.updateJointStates(
            mapOf(
                "knee" to JointStateInfo(
                    jointCode = "knee",
                    state = JointState.PERFECT,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.addPositionError(
            PositionError(
                checkId = "knee_over_toe",
                type = PositionCheckType.FORWARD_COMPARISON,
                severity = CheckSeverity.ERROR,
                message = LocalizedText(en = "Knee over toe"),
                actualValue = 0.12,
                threshold = 0.05,
                landmark1 = "left_knee",
                landmark2 = "left_foot_index",
            ),
        )
        counter.completeRep()

        val summary = ExerciseWorkoutSummaryBuilder.build(config, counter, durationMs = 5_000L)
        assertEquals(1, summary.repDetails.size)
        val rep = summary.repDetails.single()
        assertEquals(1, rep.positionErrors.size)
        assertEquals("Knee over toe", rep.positionErrors.first().message.en)
        assertEquals(1, summary.repDetails.positionErrorRepCount())
    }
}
