package com.movit.feature.training

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.movit.designsystem.components.MovitChromeButtonStyle
import com.movit.designsystem.components.MovitChromeIconButton
import com.movit.resources.movitText

@Composable
fun TrainingCameraFlipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = movitText("training_session_flip_camera_a11y")
    MovitChromeIconButton(
        onClick = { if (enabled) onClick() },
        icon = Icons.Default.Cameraswitch,
        contentDescription = label,
        modifier = modifier.alpha(if (enabled) 1f else 0.46f),
        style = MovitChromeButtonStyle.OnMedia,
    )
}
