package com.movit.designsystem.components

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
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import androidx.compose.ui.unit.dp
import com.movit.designsystem.movitColors

@Composable
fun MovitDashboardHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    progressPercent: Int,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    inkStyle: Boolean = true,
) {
    val movit = MaterialTheme.movitColors
    val shape = RoundedCornerShape(MovitRadius.xl)
    val container = if (inkStyle) movit.ink else MaterialTheme.colorScheme.surface
    val content = if (inkStyle) movit.onInk else MaterialTheme.colorScheme.onSurface
    val muted = if (inkStyle) movit.onInkVeil70 else movit.textSecondary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = container,
        contentColor = content,
        border = if (inkStyle) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(MovitSpacing.lg)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                fontWeight = FontWeight.W700,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.W800,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
            MovitProgressBar(
                progressPercent = progressPercent,
                modifier = Modifier.padding(top = MovitSpacing.lg),
                trackColor = if (inkStyle) movit.onInkVeil18 else movit.surface2,
            )
            if (actionLabel != null && onActionClick != null) {
                MovitButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    variant = MovitButtonVariant.Tonal,
                    size = MovitButtonSize.Small,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            }
        }
    }
}
