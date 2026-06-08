package com.movit.feature.home.components

import androidx.compose.foundation.clickable
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
import com.movit.feature.home.HomeReportPreviewUi

@Composable
fun HomeReportPreview(
    reportPreview: HomeReportPreviewUi?,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reportPreview == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = "Latest session",
            subtitle = "Reports",
        )
        MovitCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenReports),
            variant = MovitCardVariant.Outlined,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                Text(
                    text = reportPreview.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = reportPreview.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
