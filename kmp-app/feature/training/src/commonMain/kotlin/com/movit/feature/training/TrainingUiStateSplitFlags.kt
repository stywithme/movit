package com.movit.feature.training

import kotlin.concurrent.Volatile

/**
 * WP-07 R1 — separate high-frequency overlay flow from session chrome state.
 *
 * Default ON. M1 device baseline was unavailable at ship; M2 proof awaits device.
 * Set `false` only as an emergency kill (clears overlay publishes) — full rollback
 * requires restoring dual-write into [TrainingSessionUiState].
 */
object TrainingUiStateSplitFlags {
    @Volatile
    var r1OverlayFlowEnabled: Boolean = true
}
