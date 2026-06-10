package com.movit.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movit.designsystem.movitColors

object MovitElevation {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 10.dp
    val lg: Dp = 12.dp
    val xl: Dp = 14.dp
    val xxl: Dp = 16.dp
}

@Composable
fun Modifier.movitShadow(
    elevation: Dp,
    shape: Shape,
    small: Boolean = false,
): Modifier {
    val movit = MaterialTheme.movitColors
    val shadowColor = if (small) movit.shadowSm else movit.shadow
    return shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
}
