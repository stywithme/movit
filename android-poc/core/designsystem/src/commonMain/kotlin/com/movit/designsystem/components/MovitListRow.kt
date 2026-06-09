package com.movit.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * A single list row (icon · title/subtitle · trailing). It does NOT draw its own
 * card — wrap rows in [MovitListGroup] or [MovitListCard] for the container surface,
 * matching the prototype `.card.flush > .list-row` pattern.
 */
@Composable
fun MovitListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconVariant: MovitIconBoxVariant = MovitIconBoxVariant.Primary,
    trailingValue: String? = null,
    trailingUnit: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (showDivider) HorizontalDivider(color = MaterialTheme.movitColors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = MovitSpacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                MovitIconBox(icon = icon, variant = iconVariant)
                Spacer(Modifier.size(MovitSpacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.movitColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            when {
                trailing != null -> {
                    Spacer(Modifier.size(MovitSpacing.sm))
                    trailing()
                }
                trailingValue != null -> {
                    MovitTrailingValue(value = trailingValue, unit = trailingUnit)
                }
                showChevron && onClick != null -> {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.movitColors.textQuaternary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MovitTrailingValue(value: String, unit: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.W800,
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.movitColors.textTertiary,
                modifier = Modifier.padding(start = 1.dp, bottom = 1.dp),
            )
        }
    }
}

/**
 * Groups list rows inside an elevated card, auto-inserting dividers between rows
 * (matches the prototype `.list-row + .list-row { border-top }`). Prefer this over
 * manually toggling [MovitListRow.showDivider].
 */
@Composable
fun MovitListGroup(
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    MovitListCard(modifier = modifier) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.movitColors.divider)
                }
                row()
            }
        }
    }
}

@Composable
fun MovitListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Elevated, contentPadding = 0.dp) {
        content()
    }
}
