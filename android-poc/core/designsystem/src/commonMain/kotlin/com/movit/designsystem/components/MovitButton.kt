package com.movit.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing

enum class MovitButtonVariant {
    Filled,
    Tonal,
    Outlined,
}

@Composable
fun MovitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MovitButtonVariant = MovitButtonVariant.Filled,
    enabled: Boolean = true,
) {
    val contentPadding = PaddingValues(
        horizontal = MovitSpacing.lg,
        vertical = MovitSpacing.md,
    )
    val minHeight = Modifier.heightIn(min = 48.dp)

    when (variant) {
        MovitButtonVariant.Filled -> Button(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }

        MovitButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            contentPadding = contentPadding,
            colors = ButtonDefaults.filledTonalButtonColors(),
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }

        MovitButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            contentPadding = contentPadding,
            colors = ButtonDefaults.outlinedButtonColors(),
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
