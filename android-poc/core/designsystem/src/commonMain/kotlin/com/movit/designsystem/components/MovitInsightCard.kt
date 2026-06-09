package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

enum class MovitInsightVariant {
    Success,
    Warning,
    Danger,
}

@Composable
fun MovitInsightCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    variant: MovitInsightVariant = MovitInsightVariant.Success,
) {
    val movit = MaterialTheme.movitColors
    val (background, border, iconBg, iconFg) = insightColors(variant, movit)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MovitRadius.lg),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(MovitSpacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconBg,
                contentColor = iconFg,
            ) {
                Row(
                    modifier = Modifier.size(38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.padding(start = MovitSpacing.md)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.W700)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.textSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun insightColors(
    variant: MovitInsightVariant,
    movit: com.movit.designsystem.MovitExtendedColors,
): InsightColors = when (variant) {
    MovitInsightVariant.Success -> InsightColors(
        background = movit.successTint,
        border = movit.success.copy(alpha = 0.30f),
        iconBg = movit.success,
        iconFg = MaterialTheme.colorScheme.onSecondary,
    )
    MovitInsightVariant.Warning -> InsightColors(
        background = movit.warningTint,
        border = movit.warning.copy(alpha = 0.30f),
        iconBg = movit.warning,
        iconFg = MaterialTheme.colorScheme.onTertiary,
    )
    MovitInsightVariant.Danger -> InsightColors(
        background = movit.coralTint,
        border = MaterialTheme.colorScheme.error.copy(alpha = 0.30f),
        iconBg = MaterialTheme.colorScheme.error,
        iconFg = MaterialTheme.colorScheme.onError,
    )
}

private data class InsightColors(
    val background: Color,
    val border: Color,
    val iconBg: Color,
    val iconFg: Color,
)
