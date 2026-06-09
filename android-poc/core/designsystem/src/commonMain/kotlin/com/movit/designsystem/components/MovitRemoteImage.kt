package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MovitRemoteImage(
    imageUrl: String?,
    contentDescription: String?,
    placeholderLabel: String,
    modifier: Modifier = Modifier,
)
