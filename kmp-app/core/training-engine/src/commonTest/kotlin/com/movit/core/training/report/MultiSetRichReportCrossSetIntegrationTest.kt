package com.movit.core.training.report

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.RepResult
import com.movit.core.training.journal.WorkoutExecutionMetrics
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.testing.readExerciseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Builder + cross-set merge path for multi-set rich reports (no ViewModel / SQL cache).
 */
class MultiSetRichReportCrossSetIntegrationTest {

    @Test
    fun threeSets_merge_producesCrossSetBestWorstAndFormBySet() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val setScores = listOf(80f, 95f, 70f)
        val reports = setScores.mapIndexed { index, score ->
            buildSetReport(
                config = config,
                id = "exec-set-${index + 1}",
                setNumber = index + 1,
                averageScore = score,
                repScores = listOf(score - 5f, score),
            )
        }

        val merged = MovitPostTrainingReportCrossSetAggregator.merge(reports)
        assertNotNull(merged)
        assertEquals(3, merged.setSummaries.size)
        assertEquals(3, merged.repTimeline.map { it.setNumber }.distinct().size)
        assertEquals(2, merged.bestReps.single().setNumber)
        assertEquals(2, merged.bestReps.single().repNumber)
        assertEquals(95f, merged.bestReps.single().score)
        assertEquals(3, merged.worstRep?.setNumber)
        assertEquals(1, merged.worstRep?.repNumber)
        assertEquals(65f, merged.worstRep?.score)
        assertEquals(3, merged.setSummaries.count { it.setNumber in 1..3 })
    }

    @Test
    fun setTwoRepOne_peakDiffersFromSetOne_afterBuilder() {
        val config = ExerciseConfigParser.parseConfigJson(readExerciseFixture("squat.json"))
        val setOne = buildSetReport(config, "set-1", 1, 80f, listOf(75f, 80f))
        val setTwo = buildSetReport(config, "set-2", 2, 90f, listOf(90f, 88f))

        assertEquals(1, setOne.repTimeline.first { it.repNumber == 1 }.setNumber)
        assertEquals(2, setTwo.repTimeline.first { it.repNumber == 1 }.setNumber)
        assertEquals(80f, setOne.bestReps.maxBy { it.score }.score)
        assertEquals(90f, setTwo.bestReps.maxBy { it.score }.score)

        val merged = MovitPostTrainingReportCrossSetAggregator.merge(listOf(setOne, setTwo))
        assertNotNull(merged)
        assertEquals(2, merged.setSummaries.size)
        assertEquals(2, merged.repTimeline.count { it.repNumber == 1 })
    }

    private fun buildSetReport(
        config: com.movit.core.training.config.ExerciseConfig,
        id: String,
        setNumber: Int,
        averageScore: Float,
        repScores: List<Float>,
    ): MovitPostTrainingReport {
        val upload = WorkoutUpload(
            id = id,
            exerciseId = "bodyweight-squat",
            timestamp = setNumber.toLong(),
            durationMs = 60_000,
            totalReps = repScores.size,
            countedReps = repScores.size,
            invalidReps = 0,
            repMetrics = emptyList(),
            executionMetrics = WorkoutExecutionMetrics(
                avgRom = 900,
                avgSymmetry = null,
                avgStability = null,
                avgTempo = emptyList(),
                avgVelocity = 0,
                avgFormScore = (averageScore * 10).toInt().toShort(),
                avgAlignmentAccuracy = null,
                totalTUT = 60_000,
                totalVolume = null,
                maxWeight = null,
                est1RM = null,
            ),
        )
        return MovitPostTrainingReportBuilder.build(
            upload = upload,
            summary = ExerciseWorkoutSummary(
                exerciseName = config.name.en,
                totalReps = repScores.size,
                countedReps = repScores.size,
                invalidatedReps = 0,
                averageScore = averageScore,
                countedRatio = 1f,
                durationMs = 60_000L,
                repDetails = repScores.mapIndexed { index, score ->
                    RepResult(
                        repNumber = index + 1,
                        score = score,
                        worstState = JointState.NORMAL,
                        isCounted = true,
                    )
                },
            ),
            exerciseConfig = config,
            setNumber = setNumber,
            repsTarget = repScores.size,
        )
    }
}
