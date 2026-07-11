package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.core.training.model.PoseFrame

@Composable
expect fun TrainingSessionCameraHost(
    onFrame: (PoseFrame?) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = true,
    modelType: String = "full",
    onDebugFps: ((Int) -> Unit)? = null,
    /** Bumped in [TrainingSessionViewModel.reloadForNextFlowItem] to reset elbow + sticky (E-08). */
    angleTrackingEpoch: Int = 0,
)
