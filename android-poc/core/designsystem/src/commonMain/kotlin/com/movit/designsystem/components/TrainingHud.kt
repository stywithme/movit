package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun TrainingHud(
    repCount: Int,
    targetReps: Int,
    formPercent: Int,
    phaseLabel: String,
    elapsedLabel: String,
    progressPercent: Int,
    modifier: Modifier = Modifier,
    formLabel: String = "Form",
    repsContentDescription: String? = null,
) {
    val movit = MaterialTheme.movitColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MovitSpacing.minTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(movit.inkVeil72)
            .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.semantics {
                    val announcement = repsContentDescription ?: "$repCount of $targetReps reps"
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(
                    text = "$repCount",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.W900,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "/ $targetReps",
                    style = MaterialTheme.typography.titleMedium,
                    color = movit.onInkVeil70,
                )
            }
            MovitScoreRing(
                percent = formPercent,
                label = formLabel,
                ringColor = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitTag(text = phaseLabel, variant = MovitTagVariant.Blue)
            Text(
                text = elapsedLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W700,
                color = movit.onInkVeil88,
            )
        }
        MovitProgressBar(progressPercent = progressPercent.coerceIn(0, 100))
    }
}
