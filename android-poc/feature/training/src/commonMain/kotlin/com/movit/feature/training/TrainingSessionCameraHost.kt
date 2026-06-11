package com.movit.feature.training



import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import com.movit.core.training.model.PoseFrame



@Composable

expect fun TrainingSessionCameraHost(

    onFrame: (PoseFrame) -> Unit,

    onCameraReady: () -> Unit,

    onError: (String) -> Unit,

    modifier: Modifier = Modifier,

    useFrontCamera: Boolean = true,

    onDebugFps: ((Int) -> Unit)? = null,

)


