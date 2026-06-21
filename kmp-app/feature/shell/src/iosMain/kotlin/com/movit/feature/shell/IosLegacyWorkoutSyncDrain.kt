package com.movit.feature.shell

import com.movit.core.data.MovitData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val legacyDrainScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * WS-10 parity hook for iOS shell startup / resume.
 *
 * Android drains legacy analytics pending workouts via [com.movit.host.LegacyWorkoutSyncDrain].
 * No iOS legacy analytics queue exists (greenfield KMP shell); KMP Outbox owns post-cutover uploads.
 */
internal object IosLegacyWorkoutSyncDrain {
    fun drainPendingExecutions() {
        if (!MovitData.isInstalled) return
        legacyDrainScope.launch {
            runCatching { MovitData.drainLegacyWorkoutExecutions() }
        }
    }
}

internal actual fun drainLegacyWorkoutExecutionsOnResume() {
    IosLegacyWorkoutSyncDrain.drainPendingExecutions()
}
