package com.movit.core.training.engine

import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.position.PositionError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepCounterTest {

    @Test
    fun zonePeakScoring_usesWeakerOfUpAndDownPeaks() {
        var now = 0L
        val counter = RepCounter(
            minRepIntervalMs = 0L,
            primaryJoints = setOf("elbow"),
            timeProvider = { now },
        )

        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PERFECT,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PAD,
                    isPrimary = true,
                    currentZone = ZoneType.DOWN_ZONE,
                ),
            ),
        )

        now = 100L
        counter.completeRep()

        assertEquals(1, counter.count)
        assertEquals(60f, counter.getLastRepResult()?.score)
        assertTrue(counter.getLastRepResult()?.isCounted == true)
    }

    @Test
    fun positionError_reducesScoreAndUncountsRep() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PERFECT,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.addPositionError("knee_over_toe")
        counter.completeRep()

        val result = counter.getLastRepResult()!!
        assertEquals(85f, result.score)
        assertFalse(result.isCounted)
        assertEquals(JointState.WARNING, result.worstState)
        assertEquals(listOf("knee_over_toe"), result.positionErrorCheckIds)
        assertEquals(1, result.positionErrors.size)
        assertEquals("knee_over_toe", result.positionErrors.first().checkId)
    }

    @Test
    fun positionError_storesFullSnapshotFromPipeline() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PERFECT,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        val pipelineError = PositionError(
            checkId = "left_knee_above_ankle",
            type = PositionCheckType.VERTICAL_COMPARISON,
            severity = CheckSeverity.ERROR,
            message = LocalizedText(en = "Knee too far forward", ar = "الركبة أمامية جداً"),
            actualValue = 1.25,
            threshold = 1.0,
            landmark1 = "left_knee",
            landmark2 = "left_ankle",
        )
        counter.addPositionError(pipelineError)
        counter.completeRep()

        val result = counter.getLastRepResult()!!
        val snapshot = result.positionErrors.single()
        assertEquals(pipelineError.checkId, snapshot.checkId)
        assertEquals(PositionCheckType.VERTICAL_COMPARISON, snapshot.type)
        assertEquals(CheckSeverity.ERROR, snapshot.severity)
        assertEquals("Knee too far forward", snapshot.message.en)
        assertEquals(1.25, snapshot.actualValue)
        assertEquals(1.0, snapshot.threshold)
        assertEquals("left_knee", snapshot.landmark1)
        assertEquals("left_ankle", snapshot.landmark2)
        assertEquals(listOf("left_knee_above_ankle"), result.positionErrorCheckIds)
        assertEquals(1, result.getTotalErrorCount())
    }

    @Test
    fun getMostCommonPositionErrors_aggregatesAcrossReps() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        val perfect = mapOf(
            "elbow" to JointStateInfo("elbow", JointState.PERFECT, isPrimary = true, currentZone = ZoneType.UP_ZONE),
        )
        counter.updateJointStates(perfect)
        counter.addPositionError("knee_over_toe")
        counter.completeRep()
        counter.updateJointStates(perfect)
        counter.addPositionError("knee_over_toe")
        counter.addPositionError("hip_shift")
        counter.completeRep()

        assertEquals(mapOf("knee_over_toe" to 2, "hip_shift" to 1), counter.getMostCommonPositionErrors())
        assertEquals(2, counter.repResults.positionErrorRepCount())
    }

    @Test
    fun holdExercise_scoresFromTimeTracking() {
        var now = 1L
        val counter = RepCounter(
            minRepIntervalMs = 0L,
            isHoldExercise = true,
            timeProvider = { now },
        )

        counter.updateJointStates(mapOf("core" to JointStateInfo("core", JointState.PERFECT, isPrimary = true)))
        now = 801L
        counter.updateJointStates(mapOf("core" to JointStateInfo("core", JointState.NORMAL, isPrimary = true)))
        now = 1001L
        counter.completeRep()

        assertEquals(96f, counter.getLastRepResult()!!.score, absoluteTolerance = 0.1f)
    }
}
