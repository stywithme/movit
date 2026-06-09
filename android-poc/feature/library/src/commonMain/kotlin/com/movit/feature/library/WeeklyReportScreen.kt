package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun WeeklyReportScreen(
    state: WeeklyReportUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
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
            state.errorMessage != null -> MovitErrorState(message = state.errorMessage, onRetry = onRetry)
            report != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    WeeklyReportHero(report = report)
                    MovitStatTileRow(
                        tiles = listOf(
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
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                        Text(
                            text = movitText("program_flow_daily_form"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.W700,
                        )
                        WeeklyFormChart(scores = report.dailyScores)
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
private fun WeeklyFormChart(scores: List<WeeklyReportDayScoreUi>) {
    val maxBarHeight = 120.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MovitRadius.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(MovitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            scores.forEach { day ->
                val fraction = day.scorePercent.coerceIn(0, 100) / 100f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (day.scorePercent >= 80) {
                                MaterialTheme.colorScheme.primary
                            } else if (day.scorePercent > 0) {
                                MaterialTheme.movitColors.textTertiary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            scores.forEach { day ->
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.weight(1f),
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
