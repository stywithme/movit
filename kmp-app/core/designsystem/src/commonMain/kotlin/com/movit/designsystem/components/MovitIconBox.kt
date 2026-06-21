package com.movit.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.movit.designsystem.movitColors

enum class MovitIconBoxVariant {
    Primary,
    Lime,
    Coral,
    Success,
    Warning,
}

@Composable
fun MovitIconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    variant: MovitIconBoxVariant = MovitIconBoxVariant.Primary,
    contentDescription: String? = null,
) {
    val movit = MaterialTheme.movitColors
    val (container, content) = iconBoxColors(variant, movit)

    Surface(
        modifier = modifier.size(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun iconBoxColors(
    variant: MovitIconBoxVariant,
    movit: com.movit.designsystem.MovitExtendedColors,
): Pair<Color, Color> = when (variant) {
    MovitIconBoxVariant.Primary -> movit.primaryTint to MaterialTheme.colorScheme.primary
    MovitIconBoxVariant.Lime -> movit.limeTint to movit.limeDeep
    MovitIconBoxVariant.Coral -> movit.coralTint to MaterialTheme.colorScheme.tertiary
    MovitIconBoxVariant.Success -> movit.successTint to movit.success
    MovitIconBoxVariant.Warning -> movit.warningTint to movit.warning
}
