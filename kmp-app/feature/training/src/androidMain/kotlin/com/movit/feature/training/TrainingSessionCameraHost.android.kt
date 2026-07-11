package com.movit.feature.training

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.movit.core.data.MovitData
import com.movit.core.posecapture.android.CameraXFrameSource
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.model.PoseFrame
import com.movit.designsystem.components.MovitErrorState
import com.movit.resources.movitText
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import org.koin.core.component.get

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val openSettings = rememberOpenAppSettingsAction()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequestFinished by remember { mutableStateOf(permissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionRequestFinished = true
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!permissionGranted && permissionRequestFinished) {
        MovitErrorState(
            title = movitText("training_session_camera_denied_title"),
            message = movitText("training_session_camera_denied_message"),
            actionLabel = movitText("training_session_open_settings"),
            onRetry = openSettings,
            modifier = modifier,
        )
        return
    }

    if (!permissionGranted) {
        return
    }

    val cameraSource = remember {
        if (!MovitData.isInstalled) return@remember null
        runCatching { MovitData.koin().get<CameraFrameSource>() }.getOrNull()
    }

    val debugFpsEnabled = onDebugFps != null && isTrainingDebugBuild()
    val debugFrameCount = remember(debugFpsEnabled) {
        if (debugFpsEnabled) AtomicInteger(0) else null
    }

    LaunchedEffect(debugFpsEnabled, onDebugFps) {
        if (!debugFpsEnabled) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            val count = debugFrameCount?.getAndSet(0) ?: 0
            onDebugFps?.invoke(count)
        }
    }

    var previewBound by remember { mutableStateOf(false) }
    var appliedModelType by remember { mutableStateOf(modelType) }

    DisposableEffect(cameraSource) {
        val source = cameraSource ?: return@DisposableEffect onDispose {}
        source.setErrorListener(onError)
        source.setOnCameraBoundListener(onCameraReady)
        source.setFrameListener { frame ->
            debugFrameCount?.incrementAndGet()
            onFrame(frame)
        }
        onDispose {
            source.setFrameListener(null)
            source.setErrorListener(null)
            source.setOnCameraBoundListener(null)
            source.stop()
        }
    }

    val cameraUnavailableMessage = movitText("training_session_camera_unavailable")
    if (cameraSource == null) {
        LaunchedEffect(cameraUnavailableMessage) {
            onError(cameraUnavailableMessage)
        }
        return
    }

    LaunchedEffect(useFrontCamera, modelType, previewBound, cameraSource) {
        if (!previewBound) return@LaunchedEffect
        val androidSource = cameraSource as? CameraXFrameSource
        androidSource?.setDebugFpsEnabled(isTrainingDebugBuild())
        if (appliedModelType != modelType) {
            androidSource?.reinitializePoseDetector()
            appliedModelType = modelType
        }
        cameraSource.start(resolveTrainingCameraConfiguration(useFrontCamera))
    }

    // E-08: reset elbow + sticky between flow exercises (epoch bumped in reloadForNextFlowItem).
    LaunchedEffect(angleTrackingEpoch, cameraSource) {
        if (angleTrackingEpoch <= 0) return@LaunchedEffect
        cameraSource.resetAngleTracking()
    }

    TrainingCameraSurface(
        modifier = modifier,
        onPreviewReady = { preview ->
            val androidSource = cameraSource as? CameraXFrameSource ?: return@TrainingCameraSurface
            androidSource.bindPreview(preview, lifecycleOwner)
            previewBound = true
        },
    )
}
