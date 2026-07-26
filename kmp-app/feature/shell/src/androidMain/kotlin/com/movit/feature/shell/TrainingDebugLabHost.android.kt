package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.movit.core.data.access.TrainingDebugAccess
import com.movit.feature.trainingdebug.TrainingDebugRoute
import com.movit.feature.trainingdebug.isTrainingDebugLabEnabled

/**
 * Android host for the Training Debug Lab.
 *
 * The lab used to be `debugImplementation`-only, so this was a stub that bounced straight back
 * and the only way in was the debug-build launcher Activity. It now ships in release too, gated
 * by [TrainingDebugAccess] — the primary admin account can open it on a production build, while
 * everyone else never sees the entry point (and is bounced here if they reach the route anyway).
 */
@Composable
actual fun TrainingDebugLabHost(
    exerciseSlug: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier,
) {
    if (TrainingDebugAccess.isLabAvailable(isTrainingDebugLabEnabled())) {
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
