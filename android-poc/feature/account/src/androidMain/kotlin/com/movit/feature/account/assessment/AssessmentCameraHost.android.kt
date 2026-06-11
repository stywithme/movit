package com.movit.feature.account.assessment

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.movit.core.data.MovitData
import com.movit.core.posecapture.android.CameraXFrameSource
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.model.PoseFrame
import org.koin.core.component.get

@Composable
actual fun AssessmentCameraHost(
    onPoseFrame: (PoseFrame?) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) onError("Camera permission is required for body scan.")
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraSource = remember(permissionGranted) {
        if (!permissionGranted || !MovitData.isInstalled) return@remember null
        runCatching { MovitData.koin().get<CameraFrameSource>() }.getOrNull()
    }

    DisposableEffect(cameraSource, onPoseFrame) {
        val source = cameraSource ?: return@DisposableEffect onDispose {}
        source.setFrameListener { frame -> onPoseFrame(frame) }
        onDispose {
            source.setFrameListener(null)
            source.stop()
        }
    }

    if (cameraSource == null) {
        LaunchedEffect(permissionGranted) {
            if (permissionGranted) {
                onError("Pose capture is not available. Restart the app.")
            }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { preview ->
                val androidSource = cameraSource as? CameraXFrameSource ?: return@also
                androidSource.bindPreview(preview, lifecycleOwner)
                androidSource.start(CameraSourceConfiguration(useFrontCamera = true))
                onCameraReady()
            }
        },
    )
}
