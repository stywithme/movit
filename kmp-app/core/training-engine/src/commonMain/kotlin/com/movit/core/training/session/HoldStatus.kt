package com.movit.core.training.session

/**
 * Aggregated hold-exercise snapshot for UI (replaces multiple legacy StateFlows).
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
