package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Image-top program card matching the prototype `.prog-card` (04-explore · 01-train):
 * media (150dp) with overlaid `gpill`s (badge top-right · weeks bottom-left · level bottom-right)
 * over a bottom scrim, then a body (title · description · meta with dumbbell icon).
 *
 * Replaces the horizontal `MovitMediaCard` for programs so the orientation matches the prototype.
 */
@Composable
fun MovitProgramCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    metadata: List<String> = emptyList(),
    imageUrl: String? = null,
    badge: String? = null,
    durationLabel: String? = null,
    levelLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val movit = MaterialTheme.movitColors
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
        onClick = onClick,
        contentPadding = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            MovitRemoteImage(
                imageUrl = imageUrl,
                contentDescription = title,
                placeholderLabel = title.take(1).uppercase(),
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, movit.inkVeil72))),
            )
            badge?.let {
                ProgramGPill(
                    text = it,
                    accent = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(MovitSpacing.sm),
                )
            }
            durationLabel?.let {
                ProgramGPill(
                    text = it,
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.align(Alignment.BottomStart).padding(MovitSpacing.sm),
                )
            }
            levelLabel?.let {
                ProgramGPill(
                    text = it,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(MovitSpacing.sm),
                )
            }
        }
        Column(
            modifier = Modifier.padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = movit.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadata.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = movit.textTertiary,
                    )
                    Text(
                        text = metadata.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = movit.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramGPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MovitRadius.full),
        color = if (accent) MaterialTheme.colorScheme.tertiary else movit.onInkVeil88,
        contentColor = if (accent) MaterialTheme.colorScheme.onTertiary else movit.ink,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W700,
            )
        }
    }
}
