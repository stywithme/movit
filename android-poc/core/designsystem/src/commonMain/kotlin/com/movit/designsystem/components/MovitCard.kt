package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

enum class MovitCardVariant {
    Elevated,
    Filled,
    Outlined,
    Flat,
}

private val cardShape: Shape
    @Composable get() = RoundedCornerShape(MovitRadius.xl)

@Composable
fun MovitCard(
    modifier: Modifier = Modifier,
    variant: MovitCardVariant = MovitCardVariant.Elevated,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = MovitSpacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    MovitCardSurface(
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        onClick = onClick,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun MovitCardSurface(
    modifier: Modifier = Modifier,
    variant: MovitCardVariant = MovitCardVariant.Elevated,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = MovitSpacing.lg,
    shape: Shape = cardShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val movit = MaterialTheme.movitColors
    val alpha = if (enabled) 1f else 0.5f
    val clickableModifier = if (onClick != null && enabled) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        modifier
    }

    when (variant) {
        MovitCardVariant.Outlined -> OutlinedCard(
            modifier = clickableModifier.alpha(alpha),
            shape = shape,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }

        MovitCardVariant.Flat -> Surface(
            modifier = clickableModifier.alpha(alpha),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }

        MovitCardVariant.Filled -> Card(
            modifier = clickableModifier.alpha(alpha),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }

        MovitCardVariant.Elevated -> Surface(
            modifier = clickableModifier
                .alpha(alpha)
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = movit.shadow,
                    spotColor = movit.shadow,
                ),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}
