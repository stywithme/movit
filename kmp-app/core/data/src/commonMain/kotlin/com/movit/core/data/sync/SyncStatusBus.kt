package com.movit.core.data.sync

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.outbox.parseHttpStatusFromError
import com.movit.core.data.platform.MovitPlatformBindings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for header sync ring (R5).
 * Fed by orchestrator, outbox replay, audio prefetch, and connectivity.
 */
class SyncStatusBus(
    private val platform: () -> MovitPlatformBindings,
    private val localStore: MovitLocalStore,
    private val telemetry: MovitSyncTelemetry = MovitSyncTelemetry(localStore),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recomputeMutex = Mutex()

    private var syncInProgress = false
    private var outboxReplayInProgress = false
    private var prefetchInProgress = false
    private var degradedMode = false
    private var lastOutcomeLabel: String? = null
    private var lastHttpStatus: Int? = null
    private var pendingOutbox: Long = 0
    private var failedOutbox: Long = 0

    private val _status = MutableStateFlow(SyncUiStatus())
    val status: StateFlow<SyncUiStatus> = _status.asStateFlow()

    init {
        scope.launch {
            lastOutcomeLabel = telemetry.readLastSyncCycle()?.outcome
            refreshOutboxCounts()
            recompute()
        }
    }

    fun onSyncStarted() {
        syncInProgress = true
        scope.launch { recompute() }
    }

    fun onSyncFinished(outcome: MovitSyncOrchestrator.SyncOutcome) {
        syncInProgress = false
        lastOutcomeLabel = outcome.telemetryLabel()
        lastHttpStatus = when (outcome) {
            is MovitSyncOrchestrator.SyncOutcome.Error -> parseHttpStatusFromError(outcome.message)
            else -> null
        }
        scope.launch {
            refreshOutboxCounts()
            recompute()
        }
    }

    fun onOutboxReplayStarted() {
        outboxReplayInProgress = true
        scope.launch { recompute() }
    }

    fun onOutboxReplayFinished() {
        outboxReplayInProgress = false
        scope.launch {
            refreshOutboxCounts()
            recompute()
        }
    }

    fun onPrefetchStarted() {
        prefetchInProgress = true
        scope.launch { recompute() }
    }

    fun onPrefetchFinished() {
        prefetchInProgress = false
        scope.launch { recompute() }
    }

    fun setDegraded(enabled: Boolean) {
        degradedMode = enabled
        scope.launch { recompute() }
    }

    fun refreshFromTelemetry() {
        scope.launch {
            lastOutcomeLabel = telemetry.readLastSyncCycle()?.outcome ?: lastOutcomeLabel
            refreshOutboxCounts()
            recompute()
        }
    }

    private suspend fun refreshOutboxCounts() {
        pendingOutbox = localStore.countOutboxByStatus(OutboxStatus.PENDING)
        failedOutbox = localStore.countOutboxByStatus(OutboxStatus.FAILED_PERMANENT)
    }

    private suspend fun recompute() {
        recomputeMutex.withLock {
            val networkUp = platform().isNetworkAvailable()
            val ring = deriveRing(networkUp)
            val problem = if (ring == SyncRingState.Problem) deriveProblemKind(networkUp) else null
            _status.value = SyncUiStatus(
                ring = ring,
                problemKind = problem,
                pendingOutbox = pendingOutbox,
                failedOutbox = failedOutbox,
                lastSyncOutcomeLabel = lastOutcomeLabel,
                lastHttpStatus = lastHttpStatus,
                isPrefetching = prefetchInProgress,
                isDegraded = degradedMode,
            )
        }
    }

    private fun deriveRing(networkUp: Boolean): SyncRingState = when {
        syncInProgress || outboxReplayInProgress || prefetchInProgress -> SyncRingState.Syncing
        !networkUp -> SyncRingState.OfflineQuiet
        failedOutbox > 0 || degradedMode -> SyncRingState.Problem
        lastOutcomeLabel == "success" || lastOutcomeLabel == "skipped" -> SyncRingState.Synced
        lastOutcomeLabel == null -> SyncRingState.Synced
        isProblemOutcome(lastOutcomeLabel, networkUp) -> SyncRingState.Problem
        else -> SyncRingState.Synced
    }

    private fun isProblemOutcome(label: String?, networkUp: Boolean): Boolean {
        if (label == null) return false
        return when (label) {
            "success", "skipped" -> false
            "offline_network" -> networkUp
            "error_network", "error_http", "error_decode", "error_unknown" -> true
            else -> true
        }
    }

    private fun deriveProblemKind(networkUp: Boolean): SyncProblemKind = when {
        degradedMode -> SyncProblemKind.Degraded
        failedOutbox > 0 -> SyncProblemKind.OutboxFailed
        lastHttpStatus != null && lastHttpStatus!! >= 500 -> SyncProblemKind.ServerError5xx
        lastOutcomeLabel == "offline_network" && networkUp -> SyncProblemKind.ServerUnreachable
        lastOutcomeLabel == "error_network" && networkUp -> SyncProblemKind.ServerUnreachable
        else -> SyncProblemKind.SyncFailed
    }

    private fun MovitSyncOrchestrator.SyncOutcome.telemetryLabel(): String = when (this) {
        is MovitSyncOrchestrator.SyncOutcome.Success -> "success"
        is MovitSyncOrchestrator.SyncOutcome.Offline -> "offline_network"
        MovitSyncOrchestrator.SyncOutcome.Skipped -> "skipped"
        is MovitSyncOrchestrator.SyncOutcome.Error -> when (kind) {
            MovitSyncOrchestrator.SyncOutcome.ErrorKind.Network -> "error_network"
            MovitSyncOrchestrator.SyncOutcome.ErrorKind.Decode -> "error_decode"
            MovitSyncOrchestrator.SyncOutcome.ErrorKind.Http -> "error_http"
            MovitSyncOrchestrator.SyncOutcome.ErrorKind.Unknown -> "error_unknown"
        }
    }
}
