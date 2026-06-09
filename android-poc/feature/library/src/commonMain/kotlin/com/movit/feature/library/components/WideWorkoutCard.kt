package com.movit.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.explore.ExploreItemUi

@Composable
fun WideWorkoutCard(
    item: ExploreItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
        onClick = onClick,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                if (featured || item.badge != null) {
                    MovitTag(
                        text = item.badge ?: "Featured",
                        variant = if (featured) MovitTagVariant.Lime else MovitTagVariant.Blue,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                        modifier = Modifier.weight(1f),
                    )
                    item.metadata.lastOrNull()?.let {
                        MovitTag(text = it, variant = MovitTagVariant.Blue)
                    }
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
                Text(
                    text = item.metadata.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
        }
    }
}
