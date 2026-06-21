package com.movit.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.movit.designsystem.movitColors
import com.movit.core.model.ExploreItemUi
import com.movit.feature.library.resolveLibraryBadge

@Composable
fun ExerciseGridCard(
    item: ExploreItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageContentDescription: String? = null,
) {
    val badge = item.resolveLibraryBadge()
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
        onClick = onClick,
        contentPadding = 0.dp,
    ) {
        LibraryMediaImage(
            imageUrl = item.imageUrl,
            contentDescription = imageContentDescription ?: item.title,
            fallbackLabel = item.title.take(1).uppercase(),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
        Column(
            modifier = Modifier.padding(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
        ) {
            badge?.let {
                MovitTag(text = it.text, variant = it.variant)
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
