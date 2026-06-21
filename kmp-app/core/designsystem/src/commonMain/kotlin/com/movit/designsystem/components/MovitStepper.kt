package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

/**
 * Numeric stepper matching the prototype `.stepper` (− value +).
 * Buttons are 34dp rounded squares on surface-2 with a subtle stroke.
 */
@Composable
fun MovitStepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minValue: Int = Int.MIN_VALUE,
    maxValue: Int = Int.MAX_VALUE,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        StepperButton(symbol = "−", enabled = enabled && value > minValue, onClick = onDecrement)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 16.dp),
        )
        StepperButton(symbol = "+", enabled = enabled && value < maxValue, onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(11.dp),
        color = movit.surface2,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, movit.stroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
            )
        }
    }
}
