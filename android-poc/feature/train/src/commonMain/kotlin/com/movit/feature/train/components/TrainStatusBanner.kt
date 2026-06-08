package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitProgressBar
import com.movit.feature.train.TrainDashboardStatus
import com.movit.feature.train.TrainDashboardUi

@Composable
fun TrainStatusBanner(
    dashboard: TrainDashboardUi,
    modifier: Modifier = Modifier,
) {
    val program = dashboard.program
    MovitCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                ) {
                    Text(
                        text = program?.name ?: statusTitle(dashboard.status),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = program?.positionLabel ?: statusSubtitle(dashboard.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(status = dashboard.status, label = program?.levelLabel ?: statusPill(dashboard.status))
            }

            if (program != null) {
                MovitProgressBar(
                    progressPercent = program.progressPercent,
                    label = "${program.progressPercent}% complete",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = program.daysTrainedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = program.streakLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = program.gradeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    status: TrainDashboardStatus,
    label: String,
) {
    val container = when (status) {
        TrainDashboardStatus.ActivePlan -> MaterialTheme.colorScheme.primaryContainer
        TrainDashboardStatus.NoPlan -> MaterialTheme.colorScheme.secondaryContainer
        TrainDashboardStatus.RestDay -> MaterialTheme.colorScheme.surfaceVariant
        TrainDashboardStatus.CompletedToday,
        TrainDashboardStatus.ProgramComplete,
        -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (status) {
        TrainDashboardStatus.ActivePlan -> MaterialTheme.colorScheme.onPrimaryContainer
        TrainDashboardStatus.NoPlan -> MaterialTheme.colorScheme.onSecondaryContainer
        TrainDashboardStatus.RestDay -> MaterialTheme.colorScheme.onSurfaceVariant
        TrainDashboardStatus.CompletedToday,
        TrainDashboardStatus.ProgramComplete,
        -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = MovitSpacing.sm,
                vertical = MovitSpacing.xs,
            ),
        )
    }
}

private fun statusTitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> "Active program"
    TrainDashboardStatus.NoPlan -> "No active program"
    TrainDashboardStatus.RestDay -> "Rest day"
    TrainDashboardStatus.CompletedToday -> "Day complete"
    TrainDashboardStatus.ProgramComplete -> "Program complete"
}

private fun statusSubtitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> "Your plan is ready."
    TrainDashboardStatus.NoPlan -> "Choose a guided program."
    TrainDashboardStatus.RestDay -> "Recovery is scheduled today."
    TrainDashboardStatus.CompletedToday -> "Training is complete for today."
    TrainDashboardStatus.ProgramComplete -> "Review your journey and choose next steps."
}

private fun statusPill(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> "Ready"
    TrainDashboardStatus.NoPlan -> "Start"
    TrainDashboardStatus.RestDay -> "Recover"
    TrainDashboardStatus.CompletedToday -> "Done"
    TrainDashboardStatus.ProgramComplete -> "Complete"
}
