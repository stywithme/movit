package com.movit.feature.trainingdebug

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.movit.core.posecapture.MovitPoseCaptureIosBindings
import com.movit.feature.training.resolveTrainingCameraConfiguration
import com.movit.designsystem.components.MovitErrorState
import com.movit.resources.movitText
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TrainingDebugCameraHost(
    isFrontCamera: Boolean,
    onFrame: (TrainingDebugFrameInput?) -> Unit,
    onError: (String) -> Unit,
    onSourceFps: (Int) -> Unit,
    modifier: Modifier,
) {
    var previewReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var frameCounter by remember { mutableIntStateOf(0) }
    var fpsWindowStart by remember { mutableStateOf(0L) }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnSourceFps by rememberUpdatedState(onSourceFps)
    val cameraSource = remember { MovitPoseCaptureIosBindings.createCameraFrameSource() }
    val cameraUnavailableMessage = movitText("training_session_camera_unavailable")

    DisposableEffect(cameraSource) {
        cameraSource.setErrorListener { message ->
            cameraError = message
            currentOnError(message)
        }
        cameraSource.setFrameListener { frame ->
            frameCounter += 1
            val now = iosNowMillis()
            if (fpsWindowStart == 0L) fpsWindowStart = now
            if (now - fpsWindowStart >= 1_000L) {
                currentOnSourceFps(frameCounter)
                frameCounter = 0
                fpsWindowStart = now
            }
            if (frame == null) {
                currentOnFrame(null)
                return@setFrameListener
            }
            currentOnFrame(
                poseFrameToDebugInput(poseFrame = frame),
            )
        }
        onDispose {
            cameraSource.setFrameListener(null)
            cameraSource.setErrorListener(null)
            cameraSource.stop()
        }
    }

    LaunchedEffect(previewReady, isFrontCamera, cameraSource) {
        if (!previewReady) return@LaunchedEffect
        cameraError = null
        runCatching {
            cameraSource.start(resolveTrainingCameraConfiguration(isFrontCamera))
        }.onFailure { error ->
            val message = error.message ?: cameraUnavailableMessage
            cameraError = message
            currentOnError(message)
        }
    }

    val message = cameraError
    if (message != null) {
        MovitErrorState(
            title = movitText("common_error_title"),
            message = message,
            modifier = modifier,
        )
        return
    }

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
        },
    )
}

private fun iosNowMillis(): Long = trainingDebugWallClockMs()
