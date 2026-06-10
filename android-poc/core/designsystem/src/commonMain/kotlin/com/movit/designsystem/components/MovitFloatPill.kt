package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitElevation
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors
import com.movit.designsystem.movitShadow

enum class MovitFloatPillVariant {
    /** Solid ink chip — default floating header control. */
    Ink,

    /** Primary CTA chip (e.g. Start). */
    Action,

    /** Glassy translucent chip for use on top of hero images. */
    Outline,
}

/**
 * Floating pill control for inner pages (prototype `.float-pill`).
 * Provide a [label] for a pill, or only an [icon] for a 46dp circular icon button.
 */
@Composable
fun MovitFloatPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    variant: MovitFloatPillVariant = MovitFloatPillVariant.Ink,
    contentDescription: String? = null,
) {
    val movit = MaterialTheme.movitColors
    val container = when (variant) {
        MovitFloatPillVariant.Ink -> movit.ink
        MovitFloatPillVariant.Action -> MaterialTheme.colorScheme.primary
        MovitFloatPillVariant.Outline -> movit.inkVeil72
    }
    val content = when (variant) {
        MovitFloatPillVariant.Action -> MaterialTheme.colorScheme.onPrimary
        else -> movit.onInk
    }
    val iconOnly = label == null
    val shape: Shape = if (iconOnly) CircleShape else RoundedCornerShape(MovitRadius.full)
    val border = if (variant == MovitFloatPillVariant.Outline) {
        BorderStroke(1.dp, movit.onInkVeil18)
    } else {
        null
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .movitShadow(elevation = MovitElevation.xl, shape = shape)
            .then(if (iconOnly) Modifier.size(46.dp) else Modifier.height(46.dp).widthIn(min = 46.dp)),
        shape = shape,
        color = container,
        contentColor = content,
        border = border,
    ) {
        if (iconOnly) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Inner-page floating header: a back control on the leading edge, an optional centered
 * title, and an optional trailing action (prototype "Inner page" patterns in §7b).
 */
@Composable
fun MovitInnerPageHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    backLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    actionVariant: MovitFloatPillVariant = MovitFloatPillVariant.Action,
    onImage: Boolean = false,
) {
    val backVariant = if (onImage) MovitFloatPillVariant.Outline else MovitFloatPillVariant.Ink
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitFloatPill(
            onClick = onBack,
            label = backLabel,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            variant = backVariant,
            contentDescription = backLabel ?: "Back",
        )
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MovitSpacing.sm),
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        if (onAction != null) {
            MovitFloatPill(
                onClick = onAction,
                label = actionLabel,
                icon = actionIcon,
                variant = if (onImage) MovitFloatPillVariant.Outline else actionVariant,
                contentDescription = actionLabel,
            )
        }
    }
}
