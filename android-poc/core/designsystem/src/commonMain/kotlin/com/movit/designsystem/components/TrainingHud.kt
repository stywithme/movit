package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    repsLabel: String = "Reps",
    timeLabel: String = "Time",
    progressLabel: String = "Progress",
    coachMessage: String? = null,
    coachSeverity: GlassMessageSeverity = GlassMessageSeverity.INFO,
    repsContentDescription: String? = null,
) {
    val movit = MaterialTheme.movitColors
    val hasRepTarget = targetReps > 0
    val primaryLabel = if (hasRepTarget) repsLabel else timeLabel
    val primaryValue = if (hasRepTarget) repCount.toString() else elapsedLabel
    val primarySuffix = if (hasRepTarget) "/ $targetReps" else null
    val formColor = when {
        formPercent >= 85 -> movit.success
        formPercent >= 65 -> movit.gold
        else -> MaterialTheme.colorScheme.tertiary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MovitSpacing.minTouchTarget)
            .clip(RoundedCornerShape(28.dp))
            .background(movit.inkVeil78)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        val announcement = repsContentDescription ?: if (hasRepTarget) {
                            "$repCount of $targetReps reps"
                        } else {
                            "$elapsedLabel elapsed"
                        }
                        contentDescription = announcement
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                Text(
                    text = primaryLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W900,
                    color = movit.onInkVeil70,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = primaryValue,
                        fontSize = 56.sp,
                        lineHeight = 58.sp,
                        fontWeight = FontWeight.W900,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    if (primarySuffix != null) {
                        Text(
                            text = primarySuffix,
                            modifier = Modifier.padding(start = MovitSpacing.xs, bottom = 7.dp),
                            fontSize = 26.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.W800,
                            color = movit.onInkVeil70,
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                if (hasRepTarget) {
                    TrainingHudMetric(label = timeLabel, value = elapsedLabel)
                }
                TrainingHudMetric(
                    label = formLabel,
                    value = "$formPercent%",
                    valueColor = formColor,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W800,
                    color = movit.onInkVeil70,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${progressPercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W900,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            LinearProgressIndicator(
                progress = { progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = formColor,
                trackColor = movit.onInkVeil16,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitTag(text = phaseLabel, variant = MovitTagVariant.Blue)
            Spacer(Modifier.width(MovitSpacing.sm))
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .weight(1f)
                    .background(movit.onInkVeil18),
            )
        }

        if (!coachMessage.isNullOrBlank()) {
            TrainingCoachCue(
                text = coachMessage,
                severity = coachSeverity,
            )
        }
    }
}

@Composable
private fun TrainingHudMetric(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.W900,
            color = MaterialTheme.movitColors.onInkVeil55,
            maxLines = 1,
        )
        Text(
            text = value,
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.W900,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrainingCoachCue(
    text: String,
    severity: GlassMessageSeverity,
) {
    val movit = MaterialTheme.movitColors
    val (container, content, border) = when (severity) {
        GlassMessageSeverity.SUCCESS -> Triple(movit.success.copy(alpha = 0.28f), Color.White, movit.success)
        GlassMessageSeverity.WARNING -> Triple(movit.warning.copy(alpha = 0.32f), Color.White, movit.warning)
        GlassMessageSeverity.ERROR -> Triple(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.36f), Color.White, MaterialTheme.colorScheme.tertiary)
        GlassMessageSeverity.INFO -> Triple(movit.onInkVeil16, Color.White, movit.onInkVeil22)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border.copy(alpha = 0.65f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.W900,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
