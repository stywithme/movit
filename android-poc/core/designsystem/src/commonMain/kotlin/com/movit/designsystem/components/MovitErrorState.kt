package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MovitErrorState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    MovitEmptyState(
        title = "Something went wrong",
        message = message,
        actionLabel = if (onRetry != null) actionLabel else null,
        onActionClick = onRetry,
        modifier = modifier,
        enabled = enabled,
    )
}
