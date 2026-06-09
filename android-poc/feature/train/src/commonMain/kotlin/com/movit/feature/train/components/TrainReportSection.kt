package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitKpiGrid
import com.movit.designsystem.components.MovitKpiItem
import com.movit.designsystem.components.MovitLineChart
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.train.TrainReportSummaryUi
import com.movit.resources.movitText

@Composable
fun TrainReportSection(
    report: TrainReportSummaryUi,
    onViewReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                MovitLineChart(
                    points = listOf(0.72f, 0.62f, 0.66f, 0.44f, 0.36f, 0.22f, 0.18f),
                    modifier = Modifier.fillMaxWidth(),
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
                    icon = Icons.Default.TrendingUp,
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
