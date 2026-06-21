package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitStatMini(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    MovitCard(
        modifier = modifier,
        variant = MovitCardVariant.Elevated,
        contentPadding = 18.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = valueColor,
                fontWeight = FontWeight.W800,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.movitColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun MovitStatBig(
    name: String,
    description: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconVariant: MovitIconBoxVariant = MovitIconBoxVariant.Primary,
    delta: String? = null,
    deltaUp: Boolean = true,
) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Elevated) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                MovitIconBox(icon = icon, variant = iconVariant)
            }
            if (delta != null) {
                MovitDeltaBadge(text = delta, positive = deltaUp)
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.movitColors.textTertiary,
        )
        Row(
            modifier = Modifier.padding(top = MovitSpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.movitColors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
fun MovitDeltaBadge(
    text: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    MovitTag(
        text = text,
        modifier = modifier,
        variant = if (positive) MovitTagVariant.Lime else MovitTagVariant.Coral,
    )
}
