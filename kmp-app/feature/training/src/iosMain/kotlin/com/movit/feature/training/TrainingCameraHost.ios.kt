package com.movit.feature.training

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.movit.core.posecapture.MovitPoseCaptureIosBindings
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.model.PoseFrame
import com.movit.designsystem.movitColors
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TrainingCameraHost(
    onPoseFrame: (PoseFrame) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    var previewReady by remember { mutableStateOf(false) }
    var cameraStarted by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val cameraSource = remember {
        MovitPoseCaptureIosBindings.createCameraFrameSource()
    }

    DisposableEffect(cameraSource, onPoseFrame) {
        cameraSource.setFrameListener { frame -> frame?.let(onPoseFrame) }
        onDispose {
            cameraSource.setFrameListener(null)
            cameraSource.stop()
        }
    }

    LaunchedEffect(previewReady) {
        if (previewReady && !cameraStarted) {
            runCatching {
                cameraSource.start(CameraSourceConfiguration(useFrontCamera = true))
            }.onSuccess {
                cameraStarted = true
                onCameraReady()
            }.onFailure { error ->
                cameraError = error.message ?: "Camera failed to start."
                onError(cameraError!!)
            }
        }
    }

    when (val message = cameraError) {
        null -> {
            UIKitView(
                modifier = modifier.fillMaxSize(),
                factory = {
                    UIView().apply {
                        backgroundColor = platform.UIKit.UIColor.blackColor
                    }
                },
                update = { view ->
                    cameraSource.attachPreview(view)
                    cameraSource.updatePreviewLayout()
                    if (!previewReady) previewReady = true
                },
                onRelease = {
                    previewReady = false
                    cameraStarted = false
                },
            )
        }
        else -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.movitColors.onInkVeil70,
                )
            }
        }
    }
}
