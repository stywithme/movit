package com.movit.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing

enum class MovitAccentVariant {
    Lime,
    Blue,
    Coral,
}

@Composable
fun MovitAccentBlock(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    variant: MovitAccentVariant = MovitAccentVariant.Lime,
    glyphIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val (container, content) = accentColors(variant)

    val shape = RoundedCornerShape(MovitRadius.xl)
    val inner: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(MovitSpacing.lg)) {
            Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.W800)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                trailing?.invoke()
            }
            if (glyphIcon != null) {
                Icon(
                    imageVector = glyphIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(120.dp),
                    tint = content.copy(alpha = 0.18f),
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
        ) {
            inner()
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            contentColor = content,
        ) {
            inner()
        }
    }
}

@Composable
private fun accentColors(variant: MovitAccentVariant): Pair<Color, Color> = when (variant) {
    MovitAccentVariant.Lime -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
    MovitAccentVariant.Blue -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    MovitAccentVariant.Coral -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
}
