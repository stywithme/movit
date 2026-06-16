package com.movit.feature.training

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
import com.movit.core.training.model.PoseFrame
import com.movit.designsystem.components.MovitErrorState
import com.movit.resources.movitText
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TrainingSessionCameraHost(
    onFrame: (PoseFrame?) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
    useFrontCamera: Boolean,
    modelType: String,
    onDebugFps: ((Int) -> Unit)?,
) {
    var previewReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var frameCounter by remember { mutableIntStateOf(0) }
    var fpsWindowStart by remember { mutableStateOf(0L) }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnDebugFps by rememberUpdatedState(onDebugFps)

    val cameraSource = remember {
        MovitPoseCaptureIosBindings.createCameraFrameSource()
    }
    val cameraUnavailableMessage = movitText("training_session_camera_unavailable")

    DisposableEffect(cameraSource) {
        cameraSource.setErrorListener { message ->
            cameraError = message
            currentOnError(message)
        }
        cameraSource.setOnCameraBoundListener {
            cameraError = null
            currentOnCameraReady()
        }
        cameraSource.setFrameListener { frame ->
            val debugFpsCallback = currentOnDebugFps
            if (debugFpsCallback != null && isTrainingDebugBuild()) {
                frameCounter += 1
                val now = iosNowMillis()
                if (fpsWindowStart == 0L) fpsWindowStart = now
                if (now - fpsWindowStart >= 1_000L) {
                    debugFpsCallback(frameCounter)
                    frameCounter = 0
                    fpsWindowStart = now
                }
            }
            currentOnFrame(frame)
        }
        onDispose {
            cameraSource.setFrameListener(null)
            cameraSource.setErrorListener(null)
            cameraSource.setOnCameraBoundListener(null)
            cameraSource.stop()
        }
    }

    LaunchedEffect(previewReady, useFrontCamera, modelType, cameraSource) {
        if (!previewReady) return@LaunchedEffect
        cameraError = null
        runCatching {
            cameraSource.start(resolveTrainingCameraConfiguration(useFrontCamera))
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

private fun iosNowMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
