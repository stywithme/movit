package com.movit.feature.trainingdebug

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.movit.feature.trainingdebug.android.AndroidDebugCameraPoseSource
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun TrainingDebugCameraHost(
    isFrontCamera: Boolean,
    onFrame: (TrainingDebugFrameInput?) -> Unit,
    onError: (String) -> Unit,
    onSourceFps: (Int) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraSource = remember { AndroidDebugCameraPoseSource() }
    val debugFrameCount = remember { AtomicInteger(0) }
    var bound by remember { mutableStateOf(false) }

    LaunchedEffect(bound) {
        if (!bound) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            onSourceFps(debugFrameCount.getAndSet(0))
        }
    }

    LaunchedEffect(isFrontCamera, bound) {
        if (!bound || !permissionGranted) return@LaunchedEffect
        cameraSource.start(
            TrainingDebugSourceConfig(
                isFrontCamera = isFrontCamera,
            ),
        )
    }

    DisposableEffect(cameraSource) {
        val job = scope.launch {
            cameraSource.frames.collect { frame ->
                debugFrameCount.incrementAndGet()
                onFrame(frame)
            }
        }
        onDispose {
            job.cancel()
            scope.launch { cameraSource.stop() }
        }
    }

    if (!permissionGranted) {
        onError("Camera permission required")
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { preview ->
                cameraSource.bindPreview(preview, lifecycleOwner)
                bound = true
            }
        },
    )
}
