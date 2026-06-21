package com.movit.storage

/**
 * Legacy offline workout execution payload (pre-Outbox [AnalyticsStorage] queue).
 * Self-contained types — legacy training models removed in WS-4.
 */
data class RepMetrics(
    val rom: Short = 0,
    val symmetry: Short? = null,
    val stability: Short = 0,
    val tempo: IntArray = intArrayOf(0, 0, 0),
    val velocity: Short? = null,
    val formScore: Short = 0,
    val alignmentAccuracy: Short = 0,
    val velocityLoss: Short? = null,
)

data class WorkoutExecutionMetrics(
    val avgRom: Short = 0,
    val avgSymmetry: Short? = null,
    val avgStability: Short = 0,
    val avgTempo: IntArray = intArrayOf(0, 0, 0),
    val avgVelocity: Short? = null,
    val avgFormScore: Short = 0,
    val avgAlignmentAccuracy: Short = 0,
    val totalTUT: Int = 0,
    val totalVolume: Float? = null,
    val maxWeight: Float? = null,
    val est1RM: Float? = null,
    val formConsistency: Short? = null,
    val fatigueIndex: Short? = null,
    val velocityLoss: Short? = null,
    val tempoConsistency: Short? = null,
)

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
