package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Dashboard hero matching the prototype `.dashboard-hero` family ([app.css]).
 *
 * - [Default] → `linear-gradient(135°, primary-tint, surface)` — light (Home greeting, Train active).
 * - [Lime]    → `linear-gradient(135°, lime-tint, surface)` — Level / Assessment score.
 * - [Level]   → `linear-gradient(135°, lime-tint, surface, primary-tint)` — Home level card.
 * - [Ink]     → solid ink (catalog / dark hero).
 *
 * Light variants render dark-on-tint with a lime progress fill, exactly like `.dh-progress`.
 */
enum class MovitDashboardHeroVariant { Default, Lime, Level, Ink }

@Composable
fun MovitDashboardHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    progressPercent: Int,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    variant: MovitDashboardHeroVariant = MovitDashboardHeroVariant.Ink,
    showProgress: Boolean = true,
    inkStyle: Boolean? = null,
) {
    val movit = MaterialTheme.movitColors
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MovitRadius.xl)
    // back-compat: legacy callers pass inkStyle (true → Ink, false → light Default)
    val effectiveVariant = when (inkStyle) {
        true -> MovitDashboardHeroVariant.Ink
        false -> MovitDashboardHeroVariant.Default
        null -> variant
    }
    val ink = effectiveVariant == MovitDashboardHeroVariant.Ink

    val surface = scheme.surface
    // tints are translucent tokens — composite over surface to get the opaque gradient stop
    val primaryTintSolid = movit.primaryTint.compositeOver(surface)
    val limeTintSolid = movit.limeTint.compositeOver(surface)

    val background = when (effectiveVariant) {
        MovitDashboardHeroVariant.Ink -> Brush.linearGradient(listOf(movit.ink, movit.ink))
        MovitDashboardHeroVariant.Default -> Brush.linearGradient(listOf(primaryTintSolid, surface))
        MovitDashboardHeroVariant.Lime -> Brush.linearGradient(listOf(limeTintSolid, surface))
        MovitDashboardHeroVariant.Level ->
            Brush.linearGradient(listOf(limeTintSolid, surface, primaryTintSolid))
    }
    val content = if (ink) movit.onInk else scheme.onSurface
    val muted = if (ink) movit.onInkVeil70 else movit.textSecondary
    val progressTrack = if (ink) movit.onInkVeil18 else movit.surface2
    val progressFill = if (ink) scheme.primary else movit.limeDeep
    val border = when {
        ink -> null
        effectiveVariant == MovitDashboardHeroVariant.Level -> BorderStroke(1.dp, movit.limeDeep.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, scheme.outline)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .padding(MovitSpacing.lg),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            fontWeight = FontWeight.W700,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = content,
            fontWeight = FontWeight.W800,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MovitSpacing.xs),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MovitSpacing.xs),
        )
        if (showProgress) {
            MovitProgressBar(
                progressPercent = progressPercent,
                modifier = Modifier.padding(top = MovitSpacing.lg),
                progressColor = progressFill,
                trackColor = progressTrack,
            )
        }
        if (actionLabel != null && onActionClick != null) {
            MovitButton(
                text = actionLabel,
                onClick = onActionClick,
                variant = MovitButtonVariant.Tonal,
                size = MovitButtonSize.Small,
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        }
    }
}
