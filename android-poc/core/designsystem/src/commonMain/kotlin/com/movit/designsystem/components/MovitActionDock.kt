package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitElevation
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Sticky bottom control used during prepare / rest (prototype `.action-dock`).
 * Single horizontal row: timer + info on the left, neutral mini-buttons on the right.
 * Position it yourself (e.g. align bottom of a Box) — this only renders the bar.
 */
@Composable
fun MovitActionDock(
    timerText: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    onSkipClick: (() -> Unit)? = null,
    onAdjustClick: (() -> Unit)? = null,
    adjustLabel: String = "−15",
) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = MovitElevation.lg,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = MovitSpacing.md)
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.W800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = movit.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onAdjustClick != null) {
                    DockMiniButton(text = adjustLabel, onClick = onAdjustClick)
                }
                if (onSkipClick != null) {
                    DockMiniButton(text = "Skip", onClick = onSkipClick)
                }
                DockMiniButton(icon = Icons.Default.PlayArrow, onClick = onPlayClick)
            }
        }
    }
}

@Composable
private fun DockMiniButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 36.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = movit.textSecondary,
        border = BorderStroke(1.dp, movit.stroke),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = if (text != null) 10.dp else 0.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            } else if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W800,
                    maxLines = 1,
                )
            }
        }
    }
}
