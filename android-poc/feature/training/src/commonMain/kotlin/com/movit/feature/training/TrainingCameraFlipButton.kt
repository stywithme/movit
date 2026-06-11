package com.movit.feature.training

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.movit.designsystem.MovitSpacing
import androidx.compose.foundation.layout.size
import com.movit.resources.movitText

@Composable
fun TrainingCameraFlipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = movitText("training_session_flip_camera_a11y")
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(MovitSpacing.minTouchTarget)
            .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = null,
        )
    }
}
