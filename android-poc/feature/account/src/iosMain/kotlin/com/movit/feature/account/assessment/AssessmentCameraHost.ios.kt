package com.movit.feature.account.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.movit.core.training.model.PoseFrame
import com.movit.designsystem.movitColors

@Composable
actual fun AssessmentCameraHost(
    onPoseFrame: (PoseFrame?) -> Unit,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) {
        onError("Body scan camera is not available on iOS yet.")
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.movitColors.ink),
    )
}

