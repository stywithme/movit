package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

enum class MovitBannerVariant {
    Default,
    Success,
    Complete,
}

@Composable
fun MovitBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    variant: MovitBannerVariant = MovitBannerVariant.Default,
) {
    val movit = MaterialTheme.movitColors
    val (container, border, titleColor) = when (variant) {
        MovitBannerVariant.Default -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
        MovitBannerVariant.Success -> Triple(movit.successTint, movit.success.copy(alpha = 0.35f), movit.success)
        MovitBannerVariant.Complete -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MovitRadius.lg),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier.padding(MovitSpacing.lg),
            horizontalAlignment = if (variant == MovitBannerVariant.Complete) {
                androidx.compose.ui.Alignment.CenterHorizontally
            } else {
                androidx.compose.ui.Alignment.Start
            },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.W800,
                textAlign = if (variant == MovitBannerVariant.Complete) TextAlign.Center else TextAlign.Start,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = movit.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = if (variant == MovitBannerVariant.Complete) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}
