package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.movit.designsystem.movitColors

/** Visual treatment for minimal chrome icon buttons on inner pages. */
enum class MovitChromeButtonStyle {
    /** Subtle chip on standard page backgrounds. */
    Default,

    /** Semi-transparent chip over camera feeds, heroes, or dark imagery. */
    OnMedia,
}

/**
 * Minimal circular icon control for inner-page chrome (back, share, etc.).
 * Smaller than [MovitFloatPill] so it does not dominate or obscure content.
 */
@Composable
fun MovitChromeIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    style: MovitChromeButtonStyle = MovitChromeButtonStyle.Default,
) {
    val movit = MaterialTheme.movitColors
    val container = when (style) {
        MovitChromeButtonStyle.Default -> MaterialTheme.colorScheme.surface
        MovitChromeButtonStyle.OnMedia -> movit.inkVeil72
    }
    val content = when (style) {
        MovitChromeButtonStyle.Default -> MaterialTheme.colorScheme.onSurface
        MovitChromeButtonStyle.OnMedia -> movit.onInk
    }
    val border = when (style) {
        MovitChromeButtonStyle.Default -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        MovitChromeButtonStyle.OnMedia -> BorderStroke(1.dp, movit.onInkVeil18)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Unified back control for all inner / stack pages. Icon-only — label via [contentDescription] only. */
@Composable
fun MovitBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
    style: MovitChromeButtonStyle = MovitChromeButtonStyle.Default,
) {
    MovitChromeIconButton(
        onClick = onClick,
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = contentDescription,
        modifier = modifier,
        style = style,
    )
}
