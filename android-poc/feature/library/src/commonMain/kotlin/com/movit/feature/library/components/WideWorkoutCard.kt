package com.movit.feature.library.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.movitColors
import com.movit.feature.explore.ExploreItemUi
import com.movit.feature.library.resolveLibraryBadge

@Composable
fun WideWorkoutCard(
    item: ExploreItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    imageContentDescription: String? = null,
    featuredLabel: String? = null,
) {
    val focusBadge = item.resolveLibraryBadge(featured = featured)
    val levelTag = item.metadata.lastOrNull()
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
            LibraryMediaImage(
                imageUrl = item.imageUrl,
                contentDescription = imageContentDescription ?: item.title,
                fallbackLabel = item.title.take(1).uppercase(),
                modifier = Modifier
                    .width(96.dp)
                    .height(96.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                focusBadge?.let {
                    MovitTag(
                        text = when {
                            featured && featuredLabel != null -> featuredLabel
                            item.focusLabel != null -> item.focusLabel
                            else -> null
                        } ?: it.text,
                        variant = it.variant,
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    levelTag?.let {
                        MovitTag(text = it, variant = com.movit.designsystem.components.MovitTagVariant.Blue)
                    }
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.metadata.isNotEmpty()) {
                    Text(
                        text = item.metadata.dropLast(1).joinToString(" · ").ifBlank {
                            item.metadata.joinToString(" · ")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
