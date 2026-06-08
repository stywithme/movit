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
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitProgressBar
import com.movit.feature.home.HomeProgressUi
import com.movit.feature.home.HomeSummaryCalculator

@Composable
fun HomeHeroSummary(
    greetingTitle: String,
    greetingSubtitle: String,
    progress: HomeProgressUi?,
    hasTodayPlan: Boolean,
    onStartTraining: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
            Text(
                text = greetingTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasTodayPlan) "Ready to train" else "Get started",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = greetingSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        progress?.let {
            MovitCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MovitCardVariant.Filled,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = MovitSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                ) {
                    Text(
                        text = "Weekly completion",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MovitProgressBar(
                        progressPercent = it.weeklyCompletionPercent,
                        label = HomeSummaryCalculator.weeklyCompletionLabel(
                            it.weeklyCompletionPercent,
                        ),
                    )
                }
            }
        }

        if (hasTodayPlan) {
            MovitButton(
                text = "Start training",
                onClick = onStartTraining,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MovitButton(
                text = "Explore programs",
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth(),
                variant = MovitButtonVariant.Outlined,
            )
        }
    }
}
