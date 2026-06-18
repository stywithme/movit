package com.movit.feature.trainingdebug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.ParameterName

@Composable
expect fun TrainingDebugImageHost(
    modelType: DebugPoseModelType,
    onFrame: (TrainingDebugFrameInput) -> Unit,
    onPickRequested: () -> Unit,
    onReanalyze: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
expect fun TrainingDebugVideoHost(
    modelType: DebugPoseModelType,
    isFrontCamera: Boolean,
    onFrame: (TrainingDebugFrameInput) -> Unit,
    onPickRequested: () -> Unit,
    onProgress: (
        @ParameterName("currentMs") Long,
        @ParameterName("durationMs") Long,
        @ParameterName("playing") Boolean,
    ) -> Unit,
    onSeekReset: () -> Unit,
    modifier: Modifier = Modifier,
)
