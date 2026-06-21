package com.movit.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object MovitRadius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 26.dp
    val full = 999.dp
}

val MovitShapes = Shapes(
    extraSmall = RoundedCornerShape(MovitRadius.sm),
    small = RoundedCornerShape(MovitRadius.sm),
    medium = RoundedCornerShape(MovitRadius.md),
    large = RoundedCornerShape(MovitRadius.lg),
    extraLarge = RoundedCornerShape(MovitRadius.xl),
)
