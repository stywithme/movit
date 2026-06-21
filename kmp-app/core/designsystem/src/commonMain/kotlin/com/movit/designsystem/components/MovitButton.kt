package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

enum class MovitButtonVariant {
    Filled,
    Tonal,
    Outlined,
    Text,
    Dark,
}

enum class MovitButtonSize {
    Default,
    Small,
}

@Composable
fun MovitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MovitButtonVariant = MovitButtonVariant.Filled,
    size: MovitButtonSize = MovitButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val movit = MaterialTheme.movitColors
    val pillShape = RoundedCornerShape(MovitRadius.full)
    val height = when (size) {
        MovitButtonSize.Default -> 52.dp
        MovitButtonSize.Small -> MovitSpacing.minTouchTarget
    }
    val contentPadding = PaddingValues(
        horizontal = if (size == MovitButtonSize.Small) 18.dp else 22.dp,
        vertical = MovitSpacing.md,
    )
    val minHeight = Modifier.heightIn(min = height)

    val label: @Composable RowScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MovitSpacing.sm))
            }
            Text(
                text = text,
                style = if (size == MovitButtonSize.Small) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W700)
                },
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(MovitSpacing.sm))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }

    when (variant) {
        MovitButtonVariant.Filled -> Button(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = pillShape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            content = label,
        )

        MovitButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = pillShape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            content = label,
        )

        MovitButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = pillShape,
            contentPadding = contentPadding,
            border = BorderStroke(1.5.dp, movit.stroke),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = label,
        )

        MovitButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = MovitSpacing.sm, vertical = MovitSpacing.xs),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            content = label,
        )

        MovitButtonVariant.Dark -> Button(
            onClick = onClick,
            modifier = modifier.then(minHeight),
            enabled = enabled,
            shape = pillShape,
            contentPadding = PaddingValues(start = 24.dp, end = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = movit.ink,
                contentColor = movit.onInk,
            ),
            content = {
                Row(
                    modifier = Modifier.height(height),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (trailingIcon != null) {
                        Surface(
                            shape = RoundedCornerShape(MovitRadius.full),
                            color = movit.onInkVeil16,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Row(
                                modifier = Modifier.height(34.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
        )
    }
}
