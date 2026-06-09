package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitAccentBlock
import com.movit.designsystem.components.MovitAccentVariant
import com.movit.designsystem.components.MovitBanner
import com.movit.designsystem.components.MovitBannerVariant
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitMetricItem
import com.movit.designsystem.components.MovitMetricRow
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.train.TrainDashboardStatus
import com.movit.feature.train.TrainDashboardUi
import com.movit.resources.movitText

@Composable
fun TrainStatusBanner(
    dashboard: TrainDashboardUi,
    modifier: Modifier = Modifier,
    onExplorePrograms: (() -> Unit)? = null,
) {
    val program = dashboard.program
    when {
        program != null -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
                MovitDashboardHero(
                    eyebrow = heroEyebrow(dashboard.status),
                    title = program.name,
                    subtitle = program.positionLabel,
                    progressPercent = program.progressPercent,
                    inkStyle = true,
                )
                MovitMetricRow(
                    items = listOf(
                        MovitMetricItem(program.daysTrainedLabel, movitText("train_metric_days"), MaterialTheme.colorScheme.primary),
                        MovitMetricItem(program.streakLabel, movitText("train_metric_streak"), MaterialTheme.colorScheme.tertiary),
                        MovitMetricItem(program.gradeLabel, movitText("train_metric_grade"), MaterialTheme.movitColors.limeDeep),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MovitTag(
                        text = program.levelLabel,
                        variant = MovitTagVariant.Blue,
                        icon = Icons.Default.Stars,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MovitTag(
                            text = program.streakLabel,
                            variant = MovitTagVariant.Coral,
                            icon = Icons.Default.LocalFireDepartment,
                        )
                        Text(
                            text = "${program.progressPercent}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.W800,
                            modifier = Modifier.padding(start = MovitSpacing.md),
                        )
                    }
                }
            }
        }
        dashboard.status == TrainDashboardStatus.NoPlan -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                Text(
                    text = movitText("train_get_started"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.movitColors.limeDeep,
                    fontWeight = FontWeight.W700,
                )
                MovitAccentBlock(
                    title = movitText("train_browse_programs"),
                    subtitle = movitText("train_browse_pick_sub"),
                    variant = MovitAccentVariant.Lime,
                    glyphIcon = Icons.Default.Explore,
                    onClick = onExplorePrograms,
                )
            }
        }
        else -> {
            MovitBanner(
                modifier = modifier,
                title = statusTitle(dashboard.status),
                message = statusSubtitle(dashboard.status),
                variant = when (dashboard.status) {
                    TrainDashboardStatus.CompletedToday,
                    TrainDashboardStatus.ProgramComplete,
                    -> MovitBannerVariant.Complete
                    TrainDashboardStatus.RestDay -> MovitBannerVariant.Success
                    else -> MovitBannerVariant.Default
                },
            )
        }
    }
}

@Composable
private fun heroEyebrow(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> movitText("train_active_program")
    TrainDashboardStatus.NoPlan -> movitText("train_get_started")
    TrainDashboardStatus.RestDay -> movitText("train_recovery_day")
    TrainDashboardStatus.CompletedToday -> movitText("train_today_complete")
    TrainDashboardStatus.ProgramComplete -> movitText("train_program_complete")
}

@Composable
private fun statusTitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> movitText("train_active_program")
    TrainDashboardStatus.NoPlan -> movitText("train_browse_programs")
    TrainDashboardStatus.RestDay -> movitText("train_rest_day_label")
    TrainDashboardStatus.CompletedToday -> movitText("train_day_complete_short")
    TrainDashboardStatus.ProgramComplete -> movitText("train_program_complete")
}

@Composable
private fun statusSubtitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> movitText("train_status_plan_ready")
    TrainDashboardStatus.NoPlan -> movitText("train_status_pick_plan")
    TrainDashboardStatus.RestDay -> movitText("train_status_recovery_part")
    TrainDashboardStatus.CompletedToday -> movitText("train_status_training_complete")
    TrainDashboardStatus.ProgramComplete -> movitText("train_status_program_review")
}
