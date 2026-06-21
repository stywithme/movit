package com.movit.feature.trainingdebug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TrainingDebugRoute(
    onBack: () -> Unit,
    onCopy: (String) -> Unit = {},
    exerciseSlug: String? = null,
    modifier: Modifier = Modifier,
    viewModel: TrainingDebugViewModel = viewModel {
        TrainingDebugViewModel(
            exerciseSlug = exerciseSlug,
            deviceTiltPort = resolveTrainingDebugDeviceTiltPort(),
        )
    },
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TrainingDebugEffect.NavigateBack -> onBack()
                is TrainingDebugEffect.CopyToClipboard -> onCopy(effect.text)
            }
        }
    }

    PlatformTrainingDebugScreen(
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
expect fun PlatformTrainingDebugScreen(
    viewModel: TrainingDebugViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
