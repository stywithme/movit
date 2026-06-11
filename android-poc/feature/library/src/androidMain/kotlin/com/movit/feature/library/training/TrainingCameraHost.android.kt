package com.movit.feature.library.training

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
import com.movit.core.training.session.LiveExerciseRunner

@Composable
actual fun TrainingCameraHost(
    runner: LiveExerciseRunner,
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
        if (!granted) {
            onError("Camera permission is required for live training.")
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val session = remember(runner, permissionGranted) {
        if (!permissionGranted) return@remember null
        KmpTrainingSessionBridge.factory?.create(
            hostContext = context,
            lifecycleOwner = lifecycleOwner,
            runner = runner,
            onPoseFrame = { frame -> frame?.let(runner::processFrame) },
            onError = onError,
        )
    }

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    if (session == null) {
        LaunchedEffect(permissionGranted) {
            if (permissionGranted) {
                onError("KMP training bridge is not installed.")
            }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { preview ->
                session.bindPreview(preview)
                session.start()
                onCameraReady()
            }
        },
    )
}
