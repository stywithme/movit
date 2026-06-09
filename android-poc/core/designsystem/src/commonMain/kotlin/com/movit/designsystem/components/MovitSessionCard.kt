package com.movit.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

data class MovitSessionItem(
    val typeLabel: String,
    val title: String,
    val subtitle: String,
    val isRest: Boolean = false,
)

@Composable
fun MovitSessionCard(
    title: String,
    subtitle: String,
    items: List<MovitSessionItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
    actionLabel: String = "Start session",
    onActionClick: (() -> Unit)? = null,
    footerNote: String? = null,
    thumbnailUrl: String? = null,
    thumbnailLabel: String? = null,
    itemTrailing: (@Composable (index: Int, item: MovitSessionItem) -> Unit)? = null,
) {
    val movit = MaterialTheme.movitColors

    MovitCard(modifier = modifier, variant = MovitCardVariant.Elevated, contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithoutRipple(onToggle)
                .padding(MovitSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnailUrl != null || thumbnailLabel != null) {
                SessionThumbnail(
                    label = thumbnailLabel ?: title.take(1).uppercase(),
                    imageUrl = thumbnailUrl,
                )
            } else {
                Surface(
                    modifier = Modifier.size(9.dp),
                    shape = CircleShape,
                    color = if (isCompleted) movit.success else movit.textQuaternary,
                ) {}
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MovitSpacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W700,
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
            if (isCompleted) {
                MovitTag(text = "Done", variant = MovitTagVariant.Lime)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = movit.textQuaternary,
                modifier = Modifier
                    .rotate(if (expanded) 90f else 0f)
                    .padding(start = MovitSpacing.sm),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = MovitSpacing.lg).padding(bottom = MovitSpacing.lg)) {
                HorizontalDivider(color = movit.divider, modifier = Modifier.padding(bottom = MovitSpacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                    items.forEachIndexed { index, item ->
                        MovitSessionItemRow(
                            item = item,
                            trailing = itemTrailing?.let { trailing -> { trailing(index, item) } },
                        )
                    }
                }
                if (footerNote != null) {
                    Text(
                        text = footerNote,
                        style = MaterialTheme.typography.labelMedium,
                        color = movit.textTertiary,
                        modifier = Modifier.padding(top = MovitSpacing.md),
                    )
                } else if (onActionClick != null) {
                    MovitButton(
                        text = actionLabel,
                        onClick = onActionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MovitSpacing.md),
                        leadingIcon = Icons.Default.PlayArrow,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionThumbnail(
    label: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.Center),
        )
        if (!imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            )
        }
    }
}

@Composable
fun MovitSessionItemRow(
    item: MovitSessionItem,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MovitRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitTypePill(label = item.typeLabel, isRest = item.isRest)
            Column(
                modifier = Modifier
                    .padding(start = MovitSpacing.md)
                    .weight(1f),
            ) {
                Text(text = item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.W700)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = movit.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (trailing != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(MovitSpacing.sm))
                trailing()
            }
        }
    }
}

@Composable
fun MovitTypePill(
    label: String,
    modifier: Modifier = Modifier,
    isRest: Boolean = false,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isRest) movit.warningTint else MaterialTheme.colorScheme.primary,
        contentColor = if (isRest) movit.warning else MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W800,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}
