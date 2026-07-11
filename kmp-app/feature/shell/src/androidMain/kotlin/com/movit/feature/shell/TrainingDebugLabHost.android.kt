package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
actual fun TrainingDebugLabHost(
    exerciseSlug: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { onBack() }
}
