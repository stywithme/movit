package com.movit.core.data.sync

import com.movit.core.data.MovitData

/**
 * Platform hook for silent periodic background sync (F11).
 *
 * Android: WorkManager periodic worker.
 * iOS: BGAppRefreshTask via [registerIosBackgroundSyncAtLaunch] + [schedule].
 */
expect object BackgroundSyncScheduler {
    fun schedule()
    fun cancel()
}

internal sealed class BackgroundSyncRunOutcome {
    data object NoOp : BackgroundSyncRunOutcome()
    data object Completed : BackgroundSyncRunOutcome()
    data object Failed : BackgroundSyncRunOutcome()
}

internal suspend fun runBackgroundSyncIfReady(): BackgroundSyncRunOutcome {
    if (!MovitData.isInstalled) return BackgroundSyncRunOutcome.NoOp
    val platform = runCatching { MovitData.requirePlatform() }.getOrNull()
        ?: return BackgroundSyncRunOutcome.NoOp
    if (platform.authHeader().isNullOrBlank()) return BackgroundSyncRunOutcome.NoOp

    return runCatching {
        when (MovitData.sync.syncIfNeeded(forceCheck = true)) {
            is MovitSyncOrchestrator.SyncOutcome.Error -> BackgroundSyncRunOutcome.Failed
            else -> BackgroundSyncRunOutcome.Completed
        }
    }.getOrElse { BackgroundSyncRunOutcome.Failed }
}
