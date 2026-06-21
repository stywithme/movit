package com.movit.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movit.designsystem.MovitSpacing
import com.movit.resources.movitText

@Composable
fun TrainingDebugFpsOverlay(
    fps: Int?,
    modifier: Modifier = Modifier,
) {
    if (!isTrainingDebugBuild() || fps == null) return
    Text(
        text = movitText("training_session_debug_fps", fps),
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = MovitSpacing.sm, vertical = MovitSpacing.xs),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        fontFamily = FontFamily.Monospace,
    )
}
