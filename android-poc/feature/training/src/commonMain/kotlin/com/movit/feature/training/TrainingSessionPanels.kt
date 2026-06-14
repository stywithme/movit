package com.movit.feature.training

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.scale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitMotion
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitGlassMessage
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitRemoteImage
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun SetupPosePanel(
    progressPercent: Int,
    phaseLabel: String,
    guidance: String?,
    modifier: Modifier = Modifier,
    actionMessage: String? = guidance,
    cameraTip: String? = null,
    regionStatus: SetupAxisStatusUi = SetupAxisStatusUi.PENDING,
    postureStatus: SetupAxisStatusUi = SetupAxisStatusUi.PENDING,
    directionStatus: SetupAxisStatusUi = SetupAxisStatusUi.PENDING,
    jointRows: List<SetupJointGuidanceUi> = emptyList(),
    referenceImageUrl: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MovitSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Text(
            text = movitText("training_session_setup_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )

        SetupAxisChipsRow(
            regionStatus = regionStatus,
            postureStatus = postureStatus,
            directionStatus = directionStatus,
        )

        if (!referenceImageUrl.isNullOrBlank()) {
            MovitRemoteImage(
                imageUrl = referenceImageUrl,
                contentDescription = phaseLabel,
                placeholderLabel = phaseLabel.take(1).uppercase(),
                modifier = Modifier
                    .size(120.dp)
                    .padding(vertical = MovitSpacing.xs),
            )
        }

        val headline = actionMessage?.takeIf { it.isNotBlank() }
            ?: movitText("training_session_setup_phase", phaseLabel)
        MovitGlassMessage(
            text = headline,
            severity = GlassMessageSeverity.INFO,
        )

        if (!cameraTip.isNullOrBlank() && cameraTip != actionMessage) {
            MovitGlassMessage(
                text = cameraTip,
                severity = GlassMessageSeverity.WARNING,
            )
        }

        if (jointRows.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                jointRows.forEach { row ->
                    SetupJointRow(row)
                }
            }
        }

        MovitProgressBar(progressPercent = progressPercent.coerceIn(0, 100))
        Text(
            text = movitText("training_session_setup_progress", progressPercent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.movitColors.onInkVeil70,
        )
    }
}

@Composable
private fun SetupAxisChipsRow(
    regionStatus: SetupAxisStatusUi,
    postureStatus: SetupAxisStatusUi,
    directionStatus: SetupAxisStatusUi,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs, Alignment.CenterHorizontally),
    ) {
        SetupAxisChip(label = movitText("training_setup_phase_region"), status = regionStatus)
        SetupAxisChip(label = movitText("training_setup_phase_posture"), status = postureStatus)
        SetupAxisChip(label = movitText("training_setup_phase_direction"), status = directionStatus)
    }
}

@Composable
private fun SetupAxisChip(
    label: String,
    status: SetupAxisStatusUi,
) {
    val variant = when (status) {
        SetupAxisStatusUi.PASSED -> MovitTagVariant.Lime
        SetupAxisStatusUi.FAILED -> MovitTagVariant.Coral
        SetupAxisStatusUi.PENDING -> MovitTagVariant.Blue
    }
    MovitTag(text = label, variant = variant)
}

@Composable
private fun SetupJointRow(row: SetupJointGuidanceUi) {
    val variant = when (row.level) {
        "RED" -> MovitTagVariant.Coral
        "YELLOW" -> MovitTagVariant.Gold
        else -> MovitTagVariant.Blue
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovitTag(
            text = row.jointCode.removePrefix("left_").removePrefix("right_").replace('_', ' '),
            variant = variant,
        )
        Text(
            text = row.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.movitColors.onInkVeil88,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun CountdownOverlay(
    value: Int?,
    frozen: Boolean,
    freezeReason: String? = null,
    modifier: Modifier = Modifier,
) {
    val display = value?.toString() ?: "…"
    Column(
        modifier = modifier.padding(MovitSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Text(
            text = display,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.W900,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        if (frozen) {
            MovitTag(
                text = movitText("training_session_countdown_frozen_chip"),
                variant = MovitTagVariant.Gold,
            )
            MovitGlassMessage(
                text = movitText("training_session_countdown_frozen"),
                severity = GlassMessageSeverity.WARNING,
            )
            val reason = freezeReason?.takeIf { it.isNotBlank() }
                ?: movitText("training_session_countdown_frozen_reason_pose")
            MovitTag(
                text = reason,
                variant = MovitTagVariant.Coral,
            )
        }
    }
}

@Composable
fun RestPanel(
    secondsRemaining: Int,
    nextExerciseName: String,
    tip: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MovitSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Text(
            text = movitText("training_session_rest_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = movitText("training_session_rest_timer", secondsRemaining),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.W900,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = movitText("training_session_rest_next", nextExerciseName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.onInkVeil88,
        )
        if (!tip.isNullOrBlank()) {
            MovitGlassMessage(text = tip, severity = GlassMessageSeverity.INFO)
        }
    }
}

@Composable
fun WorkoutCompletePanel(
    exerciseName: String,
    repCount: Int,
    formPercent: Int,
    showViewReport: Boolean = false,
    uploadNotice: String? = null,
    onViewReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberPrefersReducedMotion()
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.88f) }
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(1f, animationSpec = MovitMotion.tweenMedium())
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale.value)
            .alpha(alpha.value)
            .padding(MovitSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitGlassMessage(
            text = movitText("training_session_complete_title"),
            severity = GlassMessageSeverity.SUCCESS,
        )
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = movitText("training_session_complete_summary", repCount, formPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.onInkVeil88,
            textAlign = TextAlign.Center,
        )
        if (!uploadNotice.isNullOrBlank()) {
            MovitGlassMessage(
                text = uploadNotice,
                severity = GlassMessageSeverity.INFO,
            )
        }
        if (showViewReport) {
            MovitButton(
                text = movitText("training_session_view_report"),
                onClick = onViewReport,
                variant = MovitButtonVariant.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
