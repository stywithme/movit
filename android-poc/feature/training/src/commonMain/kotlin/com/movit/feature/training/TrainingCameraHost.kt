package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.core.training.model.PoseFrame

@Composable
expect fun TrainingCameraHost(
    onPoseFrame: (PoseFrame) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
)
