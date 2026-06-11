package com.trainingvalidator.poc.storage

import com.trainingvalidator.poc.training.models.RepMetrics
import com.trainingvalidator.poc.training.models.WorkoutExecutionMetrics

/**
 * Legacy offline workout execution payload (pre-Outbox [AnalyticsStorage] queue).
 * Kept after [com.trainingvalidator.poc.training.analytics.MotionRecorder] removal.
 */
data class RepMetricsData(
    val num: Int,
    val durationMs: Int,
    val worstState: Byte,
    val score: Short,
    val weightKg: Float?,
    val side: String? = null,
    val metrics: RepMetrics,
)

data class WorkoutUpload(
    val id: String,
    val exerciseId: String,
    val timestamp: Long,
    val durationMs: Int,
    val totalReps: Int,
    val countedReps: Int,
    val invalidReps: Int,
    val weightKg: Float?,
    val weightUnit: String,
    val repMetrics: List<RepMetricsData>,
    val executionMetrics: WorkoutExecutionMetrics,
)
