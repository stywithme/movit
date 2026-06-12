package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitWorkoutScrollCard(
    title: String,
    metadata: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    badge: String? = null,
    durationLabel: String? = null,
    levelLabel: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    MovitCard(
        modifier = modifier.width(188.dp),
        variant = MovitCardVariant.Filled,
        onClick = onClick,
        contentPadding = 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            ) {
                MovitRemoteImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    placeholderLabel = title.take(1).uppercase(),
                    modifier = Modifier.matchParentSize(),
                )
                badge?.let {
                    MovitTag(
                        text = it,
                        variant = MovitTagVariant.Gold,
                        modifier = Modifier
                            .padding(MovitSpacing.sm)
                            .align(Alignment.TopStart),
                    )
                }
                durationLabel?.let {
                    SurfacePill(
                        text = it,
                        icon = Icons.Default.Schedule,
                        modifier = Modifier
                            .padding(MovitSpacing.sm)
                            .align(Alignment.BottomStart),
                    )
                }
                levelLabel?.let {
                    SurfacePill(
                        text = it,
                        modifier = Modifier
                            .padding(MovitSpacing.sm)
                            .align(Alignment.BottomEnd),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(MovitSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (actionLabel != null && onAction != null) {
                    MovitButton(
                        text = actionLabel,
                        onClick = onAction,
                        size = MovitButtonSize.Small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MovitSpacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfacePill(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = movit.onInkVeil88,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.height(12.dp),
                    tint = movit.ink,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W700,
            )
        }
    }
}
