package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.train.TrainDashboardStatus
import com.movit.feature.train.TrainDashboardUi
import com.movit.feature.train.TrainTodayWorkoutUi

@Composable
fun TrainTodayCard(
    dashboard: TrainDashboardUi,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = dashboard.today ?: noPlanWorkout()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = sectionTitle(dashboard.status),
            subtitle = sectionSubtitle(dashboard.status),
        )
        MovitCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                    Text(
                        text = today.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = today.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    TodayMetric(label = "Time", value = today.durationLabel, modifier = Modifier.weight(1f))
                    TodayMetric(label = "Work", value = today.exerciseCountLabel, modifier = Modifier.weight(1f))
                    TodayMetric(label = "Focus", value = today.focusLabel, modifier = Modifier.weight(1f))
                }
                MovitButton(
                    text = today.primaryActionLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TodayMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun sectionTitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> "Today"
    TrainDashboardStatus.NoPlan -> "Start training"
    TrainDashboardStatus.RestDay -> "Today"
    TrainDashboardStatus.CompletedToday -> "Summary"
    TrainDashboardStatus.ProgramComplete -> "Next step"
}

private fun sectionSubtitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> "Review the session before starting."
    TrainDashboardStatus.NoPlan -> "Pick a program and Movit will schedule it."
    TrainDashboardStatus.RestDay -> "Optional light work only."
    TrainDashboardStatus.CompletedToday -> "Use the report before changing tomorrow."
    TrainDashboardStatus.ProgramComplete -> "Your journey is ready to review."
}

private fun noPlanWorkout(): TrainTodayWorkoutUi = TrainTodayWorkoutUi(
    title = "No active plan",
    subtitle = "Programs turn goals into a weekly training rhythm.",
    durationLabel = "Flexible",
    exerciseCountLabel = "Guided plan",
    focusLabel = "Choose goal",
    primaryActionLabel = "Explore programs",
)
