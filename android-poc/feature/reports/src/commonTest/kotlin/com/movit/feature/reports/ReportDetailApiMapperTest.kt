package com.movit.feature.reports

import com.movit.core.network.dto.JointMetricsDto
import com.movit.resources.strings.ReportDetailStrings
import com.movit.core.network.dto.ExerciseMetricsSummaryDto
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.SetMetricsDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReportDetailApiMapperTest {

    @Test
    fun mapsExerciseMetricsToDetail() {
        runBlocking {
            val strings = ReportDetailStrings.load("en")
            val response = MetricsApiResponse(
                success = true,
                scope = "exercise",
                summary = ExerciseMetricsSummaryDto(
                    exerciseSlug = "barbell-squat",
                    exerciseName = "Barbell Squat",
                    averageFormScore = 92f,
                    setsCompleted = 4,
                    totalReps = 40,
                    totalDurationMs = 720_000L,
                    dropOffRate = 6f,
                    sets = listOf(
                        SetMetricsDto(setNumber = 1, averageFormScore = 95f, totalReps = 10),
                        SetMetricsDto(setNumber = 2, averageFormScore = 97f, totalReps = 10),
                        SetMetricsDto(setNumber = 3, averageFormScore = 90f, totalReps = 10),
                        SetMetricsDto(setNumber = 4, averageFormScore = 86f, totalReps = 10),
                    ),
                ),
            )

            val detail = ReportDetailApiMapper.map("barbell-squat", response, strings)

            assertNotNull(detail)
            assertEquals("Barbell Squat", detail.exerciseName)
            assertEquals(92, detail.formScore)
            assertEquals(4, detail.formBySetValues.size)
            assertEquals(2, detail.repCompare.size)
            assertEquals("Best · Set 2", detail.repCompare.first().label)
            assertTrue(detail.joints.isEmpty())
        }
    }

    @Test
    fun mapsJointBreakdownWhenPresent() {
        val joints = ReportDetailApiMapper.mapJoints(
            listOf(
                JointMetricsDto(jointCode = "left_knee", jointName = "Knees", score = 94f),
                JointMetricsDto(jointCode = "left_hip", jointName = "Hips", score = 88f),
                JointMetricsDto(jointCode = "spine", jointName = "", score = 76f),
            ),
        )

        assertEquals(3, joints.size)
        assertEquals("Knees", joints[0].label)
        assertEquals(94, joints[0].scorePercent)
        assertEquals(ReportScoreTone.Success, joints[0].tone)
        assertEquals("Spine", joints[2].label)
        assertEquals(ReportScoreTone.Primary, joints[2].tone)
    }
}
