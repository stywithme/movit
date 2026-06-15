package com.movit.feature.trainingdebug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.feature.trainingdebug.ui.TrainingDebugScreen

@Composable
actual fun PlatformTrainingDebugScreen(
    viewModel: TrainingDebugViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    TrainingDebugScreen(
        viewModel = viewModel,
        onBack = onBack,
    )
}
