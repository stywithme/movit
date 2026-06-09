package com.movit.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.movitColors
import com.movit.feature.home.HomeTrainingPlanUi
import com.movit.resources.movitText

@Composable
fun TodayPlanCard(
    todayPlan: HomeTrainingPlanUi,
    onStartPlan: () -> Unit,
    modifier: Modifier = Modifier,
    showPrimaryAction: Boolean = true,
) {
    val startA11y = movitText("home_a11y_start_workout")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = movitText("home_todays_plan"),
            subtitle = movitText("home_today"),
        )
        MovitCard(variant = MovitCardVariant.Outlined) {
            Column(
                modifier = Modifier.padding(MovitSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            ) {
                Text(
                    text = todayPlan.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.movitColors.textSecondary,
                    fontWeight = FontWeight.W700,
                )
                Text(
                    text = todayPlan.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = todayPlan.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
                if (showPrimaryAction) {
                    MovitButton(
                        text = todayPlan.primaryActionLabel,
                        onClick = onStartPlan,
                        variant = MovitButtonVariant.Filled,
                        leadingIcon = Icons.Default.PlayArrow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = startA11y },
                    )
                }
                Text(
                    text = todayPlan.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
