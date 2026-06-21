package com.movit.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitElevation
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors
import com.movit.designsystem.movitShadow

enum class MovitSegmentedStyle {
    Default,
    OnAccent,
}

@Composable
fun MovitSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: MovitSegmentedStyle = MovitSegmentedStyle.Default,
) {
    val movit = MaterialTheme.movitColors
    val trackColor = when (style) {
        MovitSegmentedStyle.Default -> movit.surface2
        MovitSegmentedStyle.OnAccent -> movit.onInkVeil18
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MovitRadius.full),
        color = trackColor,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val segmentModifier = if (selected && style == MovitSegmentedStyle.Default) {
                    Modifier.movitShadow(MovitElevation.xs, RoundedCornerShape(MovitRadius.full), small = true)
                } else {
                    Modifier
                }
                Surface(
                    modifier = segmentModifier,
                    onClick = { onOptionSelected(index) },
                    shape = RoundedCornerShape(MovitRadius.full),
                    color = when {
                        selected && style == MovitSegmentedStyle.OnAccent -> MaterialTheme.colorScheme.surface
                        selected -> MaterialTheme.colorScheme.surface
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    },
                    contentColor = when {
                        selected && style == MovitSegmentedStyle.OnAccent -> movit.ink
                        selected -> MaterialTheme.colorScheme.onSurface
                        style == MovitSegmentedStyle.OnAccent -> movit.onInkVeil70
                        else -> movit.textTertiary
                    },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = MovitSpacing.sm),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.W700,
                    )
                }
            }
        }
    }
}
