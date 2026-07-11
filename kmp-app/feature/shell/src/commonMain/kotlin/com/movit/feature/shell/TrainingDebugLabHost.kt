package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun TrainingDebugLabHost(
    exerciseSlug: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
)
