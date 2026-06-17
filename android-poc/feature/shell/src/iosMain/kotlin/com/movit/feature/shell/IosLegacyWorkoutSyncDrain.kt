package com.movit.feature.shell

import com.movit.core.data.MovitData

/**
 * WS-10 parity hook for iOS shell startup / resume.
 *
 * Android drains legacy [com.movit.storage.AnalyticsStorage] pending workouts via
 * [com.movit.host.LegacyWorkoutSyncDrain] in [com.movit.host.MovitDataInstall].
 * No iOS legacy analytics queue exists (greenfield KMP shell); KMP Outbox owns post-cutover uploads.
 */
internal object IosLegacyWorkoutSyncDrain {
    fun drainPendingExecutions() {
        if (!MovitData.isInstalled) return
        // No-op: no legacy iOS analytics/pending directory to migrate.
    }
}

internal actual fun drainLegacyWorkoutExecutionsOnResume() {
    IosLegacyWorkoutSyncDrain.drainPendingExecutions()
}
