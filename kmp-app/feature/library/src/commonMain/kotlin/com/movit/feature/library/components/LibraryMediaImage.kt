package com.movit.feature.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitRemoteImage

@Composable
fun LibraryMediaImage(
    imageUrl: String?,
    contentDescription: String?,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
) {
    MovitRemoteImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        placeholderLabel = fallbackLabel,
        modifier = modifier,
    )
}
