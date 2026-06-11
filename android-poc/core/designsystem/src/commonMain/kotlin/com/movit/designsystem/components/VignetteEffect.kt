package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun VignetteEffect(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color(0x66FF3B30),
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, tint),
                    radius = 900f,
                ),
            ),
    )
}
