package com.movit.feature.training

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
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
    angleTrackingEpoch: Int,
) {
    var previewReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val debugFpsEnabled = onDebugFps != null && isTrainingDebugBuild()
    val debugFrameCount = remember(debugFpsEnabled) {
        if (debugFpsEnabled) DebugFpsFrameCounter() else null
    }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnDebugFps by rememberUpdatedState(onDebugFps)

    LaunchedEffect(debugFpsEnabled) {
        if (!debugFpsEnabled) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            val count = debugFrameCount?.drain() ?: 0
            currentOnDebugFps?.invoke(count)
        }
    }

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
            debugFrameCount?.increment()
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

    // E-08: reset elbow + sticky between flow exercises (epoch bumped in reloadForNextFlowItem).
    LaunchedEffect(angleTrackingEpoch, cameraSource) {
        if (angleTrackingEpoch <= 0) return@LaunchedEffect
        cameraSource.resetAngleTracking()
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

private class DebugFpsFrameCounter {
    @Volatile
    private var count = 0

    fun increment() {
        count++
    }

    fun drain(): Int {
        val snapshot = count
        count = 0
        return snapshot
    }
}
