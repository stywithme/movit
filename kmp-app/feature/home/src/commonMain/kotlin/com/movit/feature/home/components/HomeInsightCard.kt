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

@Composable
fun HomeInsightCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
            Text(
                text = "Insight",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
