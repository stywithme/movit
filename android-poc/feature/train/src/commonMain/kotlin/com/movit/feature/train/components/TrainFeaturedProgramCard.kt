package com.movit.feature.train.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
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
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.train.TrainFeaturedProgramUi
import com.movit.resources.movitText

@Composable
fun TrainFeaturedProgramCard(
    program: TrainFeaturedProgramUi,
    onStartProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Elevated,
        contentPadding = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = MovitRadius.lg, topEnd = MovitRadius.lg))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                text = program.title.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                modifier = Modifier.align(Alignment.Center),
            )
            if (!program.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MovitSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                program.badge?.let {
                    MovitTag(text = it, variant = MovitTagVariant.Coral)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                    program.durationWeeksLabel?.let {
                        MovitTag(text = it, variant = MovitTagVariant.Blue, icon = Icons.Default.CalendarMonth)
                    }
                    program.levelLabel?.let {
                        MovitTag(text = it, variant = MovitTagVariant.Lime)
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = program.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.movitColors.textSecondary,
            )
            if (program.metadata.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MovitTag(
                        text = program.metadata.joinToString(" · "),
                        variant = MovitTagVariant.Blue,
                        icon = Icons.Default.FitnessCenter,
                    )
                }
            }
            MovitButton(
                text = movitText("train_start_program"),
                onClick = onStartProgram,
                variant = MovitButtonVariant.Filled,
                size = MovitButtonSize.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.sm),
            )
        }
    }
}
