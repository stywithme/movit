package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

data class MovitMacroNutrientUi(
    val value: String,
    val label: String,
)

@Composable
fun MovitMacroCaloriesCard(
    title: String,
    subtitle: String,
    currentCalories: String,
    targetCalories: String,
    barHeights: List<Float>,
    nutrients: List<MovitMacroNutrientUi>,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            MovitIconBox(Icons.Default.LocalFireDepartment, variant = MovitIconBoxVariant.Coral)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.W700)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = movit.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentCalories,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = "/$targetCalories",
                    style = MaterialTheme.typography.labelSmall,
                    color = movit.textTertiary,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md)
                .height(42.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            barHeights.forEach { heightFraction ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height((42.dp * heightFraction.coerceIn(0.1f, 1f)))
                        .clip(RoundedCornerShape(topStart = MovitRadius.sm, topEnd = MovitRadius.sm))
                        .background(
                            if (heightFraction >= 0.85f) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            },
                        ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
        ) {
            nutrients.forEach { nutrient ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = nutrient.value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.W800,
                    )
                    Text(
                        text = nutrient.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = movit.textSecondary,
                    )
                }
            }
        }
    }
}
