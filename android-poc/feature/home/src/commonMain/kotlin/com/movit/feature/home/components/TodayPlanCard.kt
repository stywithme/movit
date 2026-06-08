package com.movit.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.home.HomeTrainingPlanUi

@Composable
fun TodayPlanCard(
    todayPlan: HomeTrainingPlanUi?,
    onStartPlan: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = "Today's plan",
            subtitle = "Today",
        )

        if (todayPlan == null) {
            MovitEmptyState(
                title = "No workout scheduled",
                message = "Browse programs and workouts to build your plan.",
                actionLabel = "Explore",
                onActionClick = onExplore,
            )
        } else {
            MovitCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MovitCardVariant.Filled,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                ) {
                    Text(
                        text = todayPlan.subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = todayPlan.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${todayPlan.exerciseCountLabel} · ${todayPlan.durationLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = todayPlan.statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MovitSpacing.xs),
                    )
                    MovitButton(
                        text = "Start workout",
                        onClick = onStartPlan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
