package com.movit.feature.library.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.core.training.session.LiveExerciseRunner
import com.movit.resources.movitText

@Composable
actual fun TrainingCameraHost(
    runner: LiveExerciseRunner,
    onCameraReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val errorMsg = movitText("workout_live_bridge_unavailable")
    LaunchedEffect(Unit) {
        onError(errorMsg)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = movitText("workout_live_ios_placeholder"))
    }
}
