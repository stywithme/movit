package com.movit.feature.training

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TrainingCameraSurface(
    onPreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                // Explicit FILL_CENTER — must match [DisplayLandmarkTransform] FILL_CENTER math.
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also(onPreviewReady)
        },
    )
}
