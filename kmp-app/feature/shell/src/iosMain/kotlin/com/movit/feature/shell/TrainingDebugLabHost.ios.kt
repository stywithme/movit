package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.movit.feature.trainingdebug.TrainingDebugRoute
import com.movit.feature.trainingdebug.isTrainingDebugLabEnabled

@Composable
actual fun TrainingDebugLabHost(
    exerciseSlug: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier,
) {
    if (isTrainingDebugLabEnabled()) {
        TrainingDebugRoute(
            exerciseSlug = exerciseSlug,
            onBack = onBack,
            onCopy = onCopy,
            modifier = modifier,
        )
    } else {
        LaunchedEffect(Unit) { onBack() }
    }
}
