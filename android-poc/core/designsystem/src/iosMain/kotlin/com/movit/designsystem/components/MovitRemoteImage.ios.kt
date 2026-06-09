package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
actual fun MovitRemoteImage(
    imageUrl: String?,
    contentDescription: String?,
    placeholderLabel: String,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (imageUrl.isNullOrBlank()) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                },
            ),
    ) {
        MovitMediaPlaceholder(label = placeholderLabel, modifier = Modifier.fillMaxSize())
    }
}
