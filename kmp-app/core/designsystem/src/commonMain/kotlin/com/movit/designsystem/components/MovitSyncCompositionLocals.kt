package com.movit.designsystem.components

import androidx.compose.runtime.staticCompositionLocalOf

val LocalMovitSyncAvatarState = staticCompositionLocalOf<MovitSyncAvatarState?> { null }
val LocalMovitOnSyncStatusClick = staticCompositionLocalOf<(() -> Unit)?> { null }
