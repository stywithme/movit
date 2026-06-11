package com.movit.core.data

/**
 * Phase 07 WS-10 rollout gate (D11). Set from the Android shell entry point at [MovitData.install].
 * When enabled, shell training entry points must not bounce to legacy [TrainingActivity].
 */
object MovitTrainingKmpGate {
    var enabled: Boolean = false
}
