package com.movit.feature.trainingdebug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun TrainingDebugImageHost(
    modelType: DebugPoseModelType,
    onFrame: (TrainingDebugFrameInput) -> Unit,
    onPickRequested: () -> Unit,
    onReanalyze: () -> Unit,
    modifier: Modifier,
) = Unit

@Composable
actual fun TrainingDebugVideoHost(
    modelType: DebugPoseModelType,
    isFrontCamera: Boolean,
    onFrame: (TrainingDebugFrameInput) -> Unit,
    onPickRequested: () -> Unit,
    onProgress: (Long, Long, Boolean) -> Unit,
    onSeekReset: () -> Unit,
    modifier: Modifier,
) = Unit
