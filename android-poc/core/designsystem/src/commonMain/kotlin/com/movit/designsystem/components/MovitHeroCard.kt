package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitHeroCard(
    eyebrow: String,
    title: String,
    membersLabel: String?,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    showPlayFab: Boolean = false,
) {
    val movit = MaterialTheme.movitColors
    val shape = RoundedCornerShape(MovitRadius.xl)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(256.dp)
            .clip(shape),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            MovitRemoteImage(
                imageUrl = imageUrl,
                contentDescription = title,
                placeholderLabel = title.take(1).uppercase(),
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(movit.inkVeil05, movit.inkVeil78),
                    ),
                )
                .padding(MovitSpacing.lg),
        ) {
        if (showPlayFab) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp),
                shape = CircleShape,
                color = movit.onInkVeil22,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, movit.onInkVeil55),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = movit.onInk)
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = movit.onInk,
                fontWeight = FontWeight.W700,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = movit.onInk,
                fontWeight = FontWeight.W800,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (membersLabel != null) {
                    Text(
                        text = membersLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = movit.onInk,
                        fontWeight = FontWeight.W700,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                Surface(
                    onClick = onCtaClick,
                    shape = RoundedCornerShape(MovitRadius.full),
                    color = movit.ink,
                    contentColor = movit.onInk,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = ctaLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.W700)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            contentColor = movit.ink,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(30.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
