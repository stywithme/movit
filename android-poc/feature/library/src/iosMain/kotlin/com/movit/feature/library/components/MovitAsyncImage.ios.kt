package com.movit.feature.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.movit.designsystem.components.MovitRemoteImage

@Composable
actual fun MovitAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    if (!url.isNullOrBlank()) {
        MovitRemoteImage(
            imageUrl = url,
            contentDescription = contentDescription,
            placeholderLabel = "",
            modifier = modifier,
        )
    }
}
