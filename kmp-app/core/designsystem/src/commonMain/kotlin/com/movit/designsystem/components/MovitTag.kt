package com.movit.designsystem.components

import androidx.compose.foundation.layout.Row
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
import com.movit.designsystem.movitColors

@Composable
fun MovitTag(
    text: String,
    modifier: Modifier = Modifier,
    variant: MovitTagVariant = MovitTagVariant.Blue,
    icon: ImageVector? = null,
) {
    val movit = MaterialTheme.movitColors
    val (container, content) = tagColors(variant, movit)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MovitRadius.full),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(MovitSpacing.xs))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.W700)
        }
    }
}

@Composable
private fun tagColors(
    variant: MovitTagVariant,
    movit: com.movit.designsystem.MovitExtendedColors,
): Pair<Color, Color> = when (variant) {
    MovitTagVariant.Lime -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
    MovitTagVariant.Blue -> movit.primaryTint to MaterialTheme.colorScheme.primary
    MovitTagVariant.Coral -> movit.coralTint to MaterialTheme.colorScheme.tertiary
    MovitTagVariant.Gold -> movit.limeTint to movit.gold
}
