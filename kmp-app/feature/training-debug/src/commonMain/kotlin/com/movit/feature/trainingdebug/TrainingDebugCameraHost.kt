package com.movit.feature.trainingdebug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.core.training.model.Landmark

@Composable
expect fun TrainingDebugCameraHost(
    isFrontCamera: Boolean,
    onFrame: (TrainingDebugFrameInput?) -> Unit,
    onError: (String) -> Unit,
    onSourceFps: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

internal fun poseFrameToDebugInput(
    poseFrame: com.movit.core.training.model.PoseFrame,
    rawLandmarks: List<Landmark> = poseFrame.landmarks.orEmpty(),
    smoothedLandmarks: List<Landmark> = poseFrame.landmarks.orEmpty(),
    rawWorldLandmarks: List<Landmark>? = poseFrame.worldLandmarks,
    smoothedWorldLandmarks: List<Landmark>? = poseFrame.worldLandmarks,
    inferenceTimeMs: Long = 0L,
): TrainingDebugFrameInput = TrainingDebugFrameFactory.fromAssembled(
    poseFrame = poseFrame,
    rawLandmarks = rawLandmarks,
    smoothedLandmarks = smoothedLandmarks,
    rawWorldLandmarks = rawWorldLandmarks,
    smoothedWorldLandmarks = smoothedWorldLandmarks,
    inferenceTimeMs = inferenceTimeMs,
)
