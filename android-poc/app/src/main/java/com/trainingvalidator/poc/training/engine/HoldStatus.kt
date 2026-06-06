package com.trainingvalidator.poc.training.engine

/**
 * Aggregated hold-exercise UI snapshot for one exercise run (replaces many separate [kotlinx.coroutines.flow.StateFlow] fields).
 * Null for rep-based exercises and when no engine hold context exists.
 */
data class HoldStatus(
    val state: HoldState,
    val elapsedMs: Long,
    val remainingMs: Long,
    val progress: Float,
    val graceRemainingMs: Long?,
    val formQuality: Float,
    val errorCount: Int,
    val jointErrorMap: Map<String, Int>,
)
