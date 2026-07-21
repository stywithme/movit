package com.movit.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovitSyncStatusAvatar(
    userName: String,
    state: MovitSyncAvatarState?,
    onClick: (() -> Unit)?,
    onStatusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val avatarSize = 44.dp
    val ringVisual = state?.ring
    val ringColor = ringColorFor(ringVisual)
    val showDot = state?.showAlertDot == true
    val interactive = onClick != null || onStatusClick != null

    Box(
        modifier = modifier
            .size(avatarSize + 6.dp)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onStatusClick?.invoke() ?: onClick?.invoke() },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ringVisual != null) {
            SyncRing(
                visual = ringVisual,
                color = ringColor,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        if (showDot && onStatusClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

@Composable
private fun SyncRing(
    visual: MovitSyncRingVisual,
    color: Color,
    modifier: Modifier = Modifier,
) {
    when (visual) {
        MovitSyncRingVisual.Syncing -> {
            val transition = rememberInfiniteTransition(label = "syncRing")
            val sweep by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "syncSweep",
            )
            Canvas(modifier = modifier) {
                drawArc(
                    color = color,
                    startAngle = sweep,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        MovitSyncRingVisual.Synced -> {
            Canvas(modifier = modifier) {
                drawCircle(color = color, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        else -> {
            Canvas(modifier = modifier) {
                drawCircle(color = color, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun ringColorFor(visual: MovitSyncRingVisual?): Color = when (visual) {
    MovitSyncRingVisual.Synced, null -> Color(0xFF2E7D32)
    MovitSyncRingVisual.Syncing -> Color(0xFFF9A825)
    MovitSyncRingVisual.Problem -> MaterialTheme.colorScheme.error
    MovitSyncRingVisual.Offline -> Color(0xFF9E9E9E)
}
