package com.movit.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.movitColors
import com.movit.feature.home.HomeReportPreviewUi
import com.movit.resources.movitText

@Composable
fun HomeReportPreview(
    reportPreview: HomeReportPreviewUi,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = movitText("home_last_session"),
            subtitle = movitText("home_insights"),
            actionLabel = movitText("home_all_reports"),
            onActionClick = onOpenReports,
        )
        MovitCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MovitCardVariant.Outlined,
            onClick = onOpenReports,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                Text(
                    text = reportPreview.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.movitColors.textSecondary,
                )
                Text(
                    text = reportPreview.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                )
                Text(
                    text = "${reportPreview.scoreLabel} · ${reportPreview.trendLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
