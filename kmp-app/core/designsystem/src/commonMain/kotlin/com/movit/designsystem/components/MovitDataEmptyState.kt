package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.resources.movitText

enum class MovitDataEmptyReason {
    Offline,
    LoadFailed,
}

@Composable
fun MovitDataEmptyState(
    reason: MovitDataEmptyReason,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val (titleKey, messageKey) = when (reason) {
        MovitDataEmptyReason.Offline -> "empty_state_offline_title" to "empty_state_offline_message"
        MovitDataEmptyReason.LoadFailed -> "empty_state_load_failed_title" to "empty_state_load_failed_message"
    }
    MovitErrorState(
        title = movitText(titleKey),
        message = movitText(messageKey),
        actionLabel = if (onRetry != null) movitText("common_retry") else null,
        onRetry = onRetry,
        modifier = modifier,
    )
}
