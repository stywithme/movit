package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBarChart
import com.movit.designsystem.components.MovitBarChartItem
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun WeeklyReportScreen(
    state: WeeklyReportUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onWeekSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    Column(modifier = modifier.fillMaxSize()) {
        MovitInnerPageHeader(
            onBack = onBack,
            title = report?.let { movitText("program_flow_week_report_title", it.weekNumber) }
                ?: movitText("program_flow_week_report_fallback"),
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
        )
        when {
            state.isLoading -> MovitLoadingState(message = movitText("program_flow_loading"))
            state.errorMessage != null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = state.errorMessage,
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            report != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    if (state.weekSummaries.size > 1) {
                        MovitSectionHeader(
                            title = movitText("program_flow_all_weeks_title"),
                            subtitle = report.programName,
                        )
                        state.weekSummaries.forEach { summary ->
                            WeeklyReportWeekSummaryCard(
                                summary = summary,
                                selected = summary.weekNumber == state.selectedWeekNumber,
                                onClick = { onWeekSelected(summary.weekNumber) },
                            )
                        }
                    }
                    WeeklyReportHero(report = report)
                    MovitStatTileRow(
                        stats = listOf(
                            MovitStatTileData(
                                value = report.sessionsCompleted.toString(),
                                label = movitText("program_flow_metric_sessions"),
                            ),
                            MovitStatTileData(
                                value = "${report.avgFormPercent}%",
                                label = movitText("program_flow_metric_form"),
                            ),
                            MovitStatTileData(
                                value = formatReps(report.totalReps),
                                label = movitText("program_flow_metric_reps"),
                            ),
                        ),
                    )
                    val chartA11y = movitText("program_flow_a11y_chart")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        modifier = Modifier.semantics { contentDescription = chartA11y },
                    ) {
                        Text(
                            text = movitText("program_flow_daily_form"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.W700,
                        )
                        MovitBarChart(
                            items = report.dailyScores.map { day ->
                                MovitBarChartItem(
                                    value = day.scorePercent.toFloat().coerceAtLeast(0f),
                                    label = day.label,
                                    highlighted = day.scorePercent >= 80,
                                )
                            },
                            highlightColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    MovitButton(
                        text = movitText("program_flow_share_report"),
                        onClick = onShare,
                        variant = MovitButtonVariant.Outlined,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MovitSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyReportHero(report: WeeklyReportUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MovitRadius.lg))
            .background(MaterialTheme.colorScheme.secondary)
            .padding(MovitSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
    ) {
        Text(
            text = report.heroEyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondary,
        )
        Text(
            text = report.heroTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onSecondary,
        )
        Text(
            text = report.heroSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

@Composable
private fun WeeklyReportWeekSummaryCard(
    summary: WeeklyReportWeekSummaryUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val summaryA11y = movitText(
        "program_flow_a11y_week_summary",
        summary.weekNumber,
        summary.title,
        summary.progressPercent,
        summary.message,
    )
    MovitCard(
        variant = if (selected) MovitCardVariant.Outlined else MovitCardVariant.Filled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = summaryA11y },
    ) {
        Column(
            modifier = Modifier.padding(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = movitText("program_flow_week_title", summary.weekNumber),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = movitText(
                        "program_flow_week_progress",
                        summary.sessionsCompleted,
                        summary.sessionsPlanned,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textTertiary,
                )
            }
            Text(
                text = summary.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
            MovitProgressBar(progressPercent = summary.progressPercent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = movitText("program_flow_week_form", summary.avgFormPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textTertiary,
                )
                Text(
                    text = summary.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textTertiary,
                )
            }
        }
    }
}

private fun formatReps(reps: Int): String {
    return when {
        reps >= 1000 -> "${reps / 1000}.${(reps % 1000) / 100}k"
        else -> reps.toString()
    }
}
