package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

enum class GlassMessageSeverity {
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
}

@Composable
fun MovitGlassMessage(
    text: String,
    modifier: Modifier = Modifier,
    severity: GlassMessageSeverity = GlassMessageSeverity.INFO,
) {
    val movit = MaterialTheme.movitColors
    val (background, border) = when (severity) {
        GlassMessageSeverity.INFO -> movit.onInkVeil16 to movit.onInkVeil22
        GlassMessageSeverity.WARNING -> movit.warningTint.copy(alpha = 0.85f) to movit.warning.copy(alpha = 0.4f)
        GlassMessageSeverity.ERROR -> movit.coralTint.copy(alpha = 0.9f) to movit.warning.copy(alpha = 0.55f)
        GlassMessageSeverity.SUCCESS -> movit.successTint.copy(alpha = 0.9f) to movit.success.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.W700,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
