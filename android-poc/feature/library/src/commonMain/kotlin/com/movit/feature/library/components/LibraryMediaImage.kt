package com.movit.feature.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun LibraryMediaImage(
    imageUrl: String?,
    contentDescription: String?,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
)
