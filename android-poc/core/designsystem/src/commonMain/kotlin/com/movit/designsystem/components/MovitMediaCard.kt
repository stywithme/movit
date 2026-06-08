package com.movit.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing

@Composable
fun MovitMediaCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    metadata: List<String> = emptyList(),
    badge: String? = null,
    imageLabel: String? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .alpha(if (enabled && !isLoading) 1f else 0.6f)
        .then(
            if (onClick != null && enabled && !isLoading) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )

    MovitCard(
        modifier = cardModifier,
        variant = MovitCardVariant.Filled,
        enabled = enabled,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            MovitMediaPlaceholder(
                label = imageLabel ?: title.take(1).uppercase(),
                modifier = Modifier
                    .width(96.dp)
                    .height(96.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                badge?.let {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(
                                horizontal = MovitSpacing.sm,
                                vertical = MovitSpacing.xs,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                        metadata.take(3).forEach { meta ->
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MovitMediaPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
