package com.movit.feature.shell

import com.movit.core.data.sync.SyncProblemKind
import com.movit.core.data.sync.SyncRingState
import com.movit.core.data.sync.SyncUiStatus
import com.movit.designsystem.components.MovitSyncAvatarState
import com.movit.designsystem.components.MovitSyncRingVisual

fun SyncUiStatus.toAvatarState(): MovitSyncAvatarState = MovitSyncAvatarState(
    ring = when (ring) {
        SyncRingState.Synced -> MovitSyncRingVisual.Synced
        SyncRingState.Syncing -> MovitSyncRingVisual.Syncing
        SyncRingState.Problem -> MovitSyncRingVisual.Problem
        SyncRingState.OfflineQuiet -> MovitSyncRingVisual.Offline
    },
    showAlertDot = ring == SyncRingState.Problem,
)

fun SyncUiStatus.statusMessageKey(): String = when (problemKind) {
    SyncProblemKind.ServerError5xx -> "sync_status_server_error"
    SyncProblemKind.ServerUnreachable -> "sync_status_server_unreachable"
    SyncProblemKind.OutboxFailed -> "sync_status_outbox_failed"
    SyncProblemKind.Degraded -> "sync_status_degraded"
    SyncProblemKind.SyncFailed, null -> when (ring) {
        SyncRingState.OfflineQuiet -> "sync_status_offline"
        SyncRingState.Syncing -> "sync_status_syncing"
        SyncRingState.Synced -> "sync_status_synced"
        SyncRingState.Problem -> "sync_status_problem"
    }
}
