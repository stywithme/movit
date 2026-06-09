package com.movit.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.explore.ExploreItemUi

@Composable
fun ExerciseGridCard(
    item: ExploreItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
        onClick = onClick,
        contentPadding = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier = Modifier.padding(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
        ) {
            item.badge?.let {
                MovitTag(text = it, variant = MovitTagVariant.Lime)
            } ?: item.metadata.firstOrNull()?.let {
                MovitTag(text = it, variant = MovitTagVariant.Blue)
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W800,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.metadata.joinToString(" · ").ifBlank { item.subtitle },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.movitColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
