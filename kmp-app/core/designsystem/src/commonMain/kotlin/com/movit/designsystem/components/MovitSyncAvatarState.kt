package com.movit.designsystem.components

enum class MovitSyncRingVisual {
    Synced,
    Syncing,
    Problem,
    Offline,
}

data class MovitSyncAvatarState(
    val ring: MovitSyncRingVisual = MovitSyncRingVisual.Synced,
    val showAlertDot: Boolean = false,
)
