package com.trainingvalidator.poc.training.report

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trainingvalidator.poc.training.models.CategoryInfo
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.ExerciseWorkoutSummary
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.RepResult
import com.trainingvalidator.poc.training.models.WorkoutExecutionMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden parity anchor: legacy [ReportGenerator] summary fields vs KMP builder inputs.
 * Full KMP JSON comparison lives in `MovitPostTrainingReportBuilderTest`.
 */
class PostTrainingReportLegacyParityTest {

  private val gson: Gson = GsonBuilder().serializeNulls().create()

  @Test
  fun reportGenerator_summaryMatchesExpectedGoldenShape() {
    val config = minimalSquatConfig()
    val summary = ExerciseWorkoutSummary(
      exerciseName = "Squat",
      totalReps = 3,
      countedReps = 3,
      invalidatedReps = 0,
      averageScore = 85f,
      countedRatio = 1f,
      durationMs = 84_000L,
      stateBreakdown = mapOf(
        JointState.PERFECT to 1,
        JointState.NORMAL to 2,
      ),
      commonErrors = emptyMap(),
      repDetails = listOf(
        rep(1, 85f, JointState.NORMAL),
        rep(2, 90f, JointState.PERFECT),
        rep(3, 80f, JointState.NORMAL),
      ),
    )
    val executionMetrics = WorkoutExecutionMetrics(
      avgRom = 920,
      avgSymmetry = null,
      avgStability = 780,
      avgTempo = listOf(1100, 350, 850),
      avgVelocity = 42,
      avgFormScore = 850,
      avgAlignmentAccuracy = 880,
      totalTUT = 8400,
      totalVolume = null,
      maxWeight = null,
      est1RM = null,
    )
    val report = ReportGenerator.generate(
      workoutId = "workout-golden-001",
      summary = summary,
      exerciseConfig = config,
      durationMs = 84_000L,
      executionMetrics = executionMetrics,
    )
    val json = gson.toJsonTree(report).asJsonObject
    val summaryJson = json.getAsJsonObject("summary")
    assertEquals(3, summaryJson.get("totalReps").asInt)
    assertEquals(84_000L, summaryJson.get("durationMs").asLong)
    assertEquals("EXCELLENT", summaryJson.get("rating").asString)
    assertEquals(3, summaryJson.get("countedReps").asInt)
    assertEquals(85.0, summaryJson.get("averageScore").asDouble, 0.01)
    val breakdown = summaryJson.getAsJsonObject("stateBreakdown")
    assertEquals(1, breakdown.get("perfectCount").asInt)
    assertEquals(2, breakdown.get("normalCount").asInt)
  }

  private fun rep(num: Int, score: Float, worst: JointState): RepResult = RepResult(
    repNumber = num,
    score = score,
    worstState = worst,
    isCounted = true,
    isInvalidated = false,
    errors = emptyList(),
    positionErrors = emptyList(),
    positionWarningCount = 0,
    positionTipCount = 0,
    phaseTimings = emptyMap(),
    timestamp = num * 1000L,
  )

  private fun minimalSquatConfig(): ExerciseConfig = ExerciseConfig(
    name = LocalizedText(en = "Squat", ar = "سكوات"),
    category = CategoryInfo("strength", LocalizedText(en = "Strength", ar = "قوة")),
    countingMethod = CountingMethod.UP_DOWN,
    poseVariants = emptyList(),
  ).apply { fileName = "bodyweight-squat" }
}
