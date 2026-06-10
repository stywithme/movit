package com.movit.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.movit.designsystem.movitColors

fun Modifier.movitClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val movit = MaterialTheme.movitColors
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = movit.primaryPress),
        enabled = enabled,
        onClick = onClick,
    )
}
