package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
actual fun MovitRemoteImage(
    imageUrl: String?,
    contentDescription: String?,
    placeholderLabel: String,
    modifier: Modifier,
) {
    if (imageUrl.isNullOrBlank()) {
        MovitMediaPlaceholder(label = placeholderLabel, modifier = modifier)
        return
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
