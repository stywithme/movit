package com.movit.feature.training

/**
 * Planned program day workout identity for WS-8 mobileWrites (G1 / §14.2).
 */
data class PlannedWorkoutContext(
    val plannedWorkoutId: String,
    val programId: String?,
    val weekNumber: Int,
    val dayNumber: Int,
)
