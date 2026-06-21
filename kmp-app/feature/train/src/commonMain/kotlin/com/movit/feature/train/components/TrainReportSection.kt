package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitDeltaBadge
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitKpiGrid
import com.movit.designsystem.components.MovitKpiItem
import com.movit.designsystem.components.MovitLineChart
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.movitColors
import com.movit.feature.train.TrainReportSummaryUi
import com.movit.resources.movitText

@Composable
fun TrainReportSection(
    report: TrainReportSummaryUi,
    onViewReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chartPoints = report.trendChartPoints.ifEmpty {
        listOf(0.72f, 0.62f, 0.66f, 0.44f, 0.36f, 0.22f, 0.18f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitSectionHeader(
                title = movitText("train_form_trend"),
                subtitle = movitText("train_last_7_sessions"),
            )
            MovitCard(variant = MovitCardVariant.Elevated) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = movitText("train_form_trend_scope"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.movitColors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    report.trendDeltaPercent?.let { delta ->
                        MovitDeltaBadge(
                            text = movitText("train_form_trend_delta", delta),
                            positive = delta >= 0,
                        )
                    }
                }
                val chartDescription = buildString {
                    append(movitText("train_a11y_form_trend_chart"))
                    append(": ")
                    append(
                        chartPoints.mapIndexed { index, value ->
                            "${index + 1} ${(value * 100f).roundToInt()}%"
                        }.joinToString(", "),
                    )
                }
                MovitLineChart(
                    points = chartPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MovitSpacing.sm)
                        .semantics { contentDescription = chartDescription },
                    lineColor = MaterialTheme.movitColors.success,
                    fillColor = MaterialTheme.movitColors.success.copy(alpha = 0.25f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitSectionHeader(
                title = report.title,
                subtitle = report.insight,
            )
            MovitCard(variant = MovitCardVariant.Elevated) {
                MovitKpiGrid(
                    items = report.metrics.map { metric ->
                        MovitKpiItem(
                            value = metric.value,
                            label = metric.label,
                            highlighted = metric.label.equals("Accuracy", ignoreCase = true),
                        )
                    },
                )
                MovitInsightCard(
                    title = movitText("train_form_improving_card"),
                    message = report.insight,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    variant = MovitInsightVariant.Success,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
                MovitButton(
                    text = movitText("train_view_full_reports"),
                    onClick = onViewReport,
                    variant = MovitButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MovitSpacing.md),
                )
            }
        }
    }
}
