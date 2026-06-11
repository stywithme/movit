package com.movit.feature.library.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.core.training.session.LiveExerciseRunner

@Composable
expect fun TrainingCameraHost(
    runner: LiveExerciseRunner,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
)
