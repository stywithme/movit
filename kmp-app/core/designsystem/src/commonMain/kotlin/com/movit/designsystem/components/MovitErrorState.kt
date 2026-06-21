package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MovitErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    MovitEmptyState(
        title = title,
        message = message,
        actionLabel = if (onRetry != null) actionLabel else null,
        onActionClick = onRetry,
        modifier = modifier,
        enabled = enabled,
    )
}
