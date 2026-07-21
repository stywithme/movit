package com.movit.core.data.sync

/**
 * Visual sync ring state for [SyncStatusBus] / header avatar (R5).
 */
enum class SyncRingState {
    /** Last cycle succeeded and outbox is clean. */
    Synced,
    /** Sync, outbox replay, or media prefetch in flight. */
    Syncing,
    /** Action needed — server/outbox/degraded (network may be present). */
    Problem,
    /** No network — expected gym mode, not an error. */
    OfflineQuiet,
}

enum class SyncProblemKind {
    ServerUnreachable,
    ServerError5xx,
    OutboxFailed,
    SyncFailed,
    Degraded,
}

data class SyncUiStatus(
    val ring: SyncRingState = SyncRingState.Synced,
    val problemKind: SyncProblemKind? = null,
    val pendingOutbox: Long = 0,
    val failedOutbox: Long = 0,
    val lastSyncOutcomeLabel: String? = null,
    val lastHttpStatus: Int? = null,
    val isPrefetching: Boolean = false,
    val isDegraded: Boolean = false,
)
