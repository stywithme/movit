package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitSectionHeader
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.movit.designsystem.movitColors
import com.movit.feature.train.TrainReadinessUi
import com.movit.resources.movitText

@Composable
fun TrainReadinessCard(
    readiness: TrainReadinessUi,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        MovitSectionHeader(
            title = movitText("train_readiness"),
            subtitle = movitText("train_readiness_sub"),
        )
        MovitCard(modifier = Modifier.fillMaxWidth(), variant = MovitCardVariant.Elevated) {
            MovitInsightCard(
                title = readiness.title,
                message = readiness.message,
                icon = Icons.Default.Favorite,
                variant = MovitInsightVariant.Success,
            )
            MovitProgressBar(
                progressPercent = readiness.progressPercent,
                label = readiness.scoreLabel,
                modifier = Modifier.padding(top = MovitSpacing.md),
                showPercent = true,
                progressColor = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = readiness.guidanceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.movitColors.textTertiary,
                modifier = Modifier.padding(top = MovitSpacing.sm),
            )
        }
    }
}
