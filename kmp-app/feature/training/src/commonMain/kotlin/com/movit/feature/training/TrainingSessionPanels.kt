package com.movit.feature.training

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.scale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movit.designsystem.MovitMotion
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitGlassMessage
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
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(MovitSpacing.lg),
        shape = RoundedCornerShape(28.dp),
        color = movit.inkVeil78,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, movit.onInkVeil18),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Text(
                text = movitText("training_session_setup_title"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.W900,
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
                        .size(112.dp)
                        .padding(vertical = MovitSpacing.xs),
                )
            }

            val headline = actionMessage?.takeIf { it.isNotBlank() }
                ?: movitText("training_session_setup_phase", phaseLabel)
            SetupPrimaryCue(text = headline, progressPercent = progressPercent)

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
                    jointRows.take(2).forEach { row ->
                        SetupJointRow(row)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (progressPercent >= 90) movit.success else MaterialTheme.colorScheme.primary,
                trackColor = movit.onInkVeil16,
            )
            Text(
                text = movitText("training_session_setup_progress", progressPercent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W800,
                color = movit.onInkVeil88,
            )
        }
    }
}

@Composable
private fun SetupPrimaryCue(
    text: String,
    progressPercent: Int,
) {
    val movit = MaterialTheme.movitColors
    val ready = progressPercent >= 90
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (ready) movit.success.copy(alpha = 0.3f) else movit.onInkVeil16,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(
            width = 1.dp,
            color = if (ready) movit.success.copy(alpha = 0.75f) else movit.onInkVeil22,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.W900,
            textAlign = TextAlign.Center,
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
    val displayText = value?.toString() ?: if (display.isBlank()) display else "..."
    val movit = MaterialTheme.movitColors
    Column(
        modifier = modifier.padding(MovitSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = movit.inkVeil78,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(1.dp, movit.onInkVeil18),
        ) {
            Text(
                text = displayText,
                modifier = Modifier.padding(horizontal = 46.dp, vertical = 10.dp),
                fontSize = 104.sp,
                lineHeight = 110.sp,
                fontWeight = FontWeight.W900,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
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
    restContext: String,
    nextExerciseName: String,
    currentSetNumber: Int,
    totalSets: Int,
    nextTargetReps: Int,
    nextTargetDurationSeconds: Int? = null,
    nextTargetWeightKg: Float? = null,
    previewImageUrl: String? = null,
    tip: String?,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val betweenSets = restContext == "BETWEEN_SETS"
    val titleKey = if (betweenSets) {
        "training_session_rest_between_sets"
    } else {
        "training_session_rest_between_exercises"
    }
    val upNextText = if (betweenSets) {
        movitText("training_session_rest_set_progress", currentSetNumber, totalSets)
    } else {
        movitText("training_session_rest_next", nextExerciseName)
    }
    val targetLines = buildList {
        if (nextTargetReps > 0) {
            add(movitText("training_session_rest_target_reps", nextTargetReps))
        }
        nextTargetDurationSeconds?.let { seconds ->
            add(movitText("training_session_rest_target_duration", seconds))
        }
        nextTargetWeightKg?.let { kg ->
            val label = if (kg == kg.toLong().toFloat()) {
                kg.toLong().toString()
            } else {
                kg.toString()
            }
            add(movitText("session_weight_kg", label))
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(MovitSpacing.lg),
        shape = RoundedCornerShape(28.dp),
        color = movit.inkVeil78,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, movit.onInkVeil18),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Text(
                text = movitText(titleKey),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.W900,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = movitText("training_session_rest_timer", secondsRemaining),
                fontSize = 72.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.W900,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            if (!previewImageUrl.isNullOrBlank()) {
                MovitRemoteImage(
                    imageUrl = previewImageUrl,
                    contentDescription = nextExerciseName,
                    placeholderLabel = nextExerciseName.take(1).uppercase(),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            Text(
                text = upNextText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                color = movit.onInkVeil88,
                textAlign = TextAlign.Center,
            )
            if (targetLines.isNotEmpty()) {
                targetLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W700,
                        color = movit.onInkVeil70,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (!tip.isNullOrBlank()) {
                MovitGlassMessage(text = tip, severity = GlassMessageSeverity.INFO)
            }
        }
    }
}

@Composable
fun RestControlsDock(
    secondsRemaining: Int,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onAddTime: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = movit.inkVeil78,
            contentColor = movit.onInk,
            border = BorderStroke(1.dp, movit.onInkVeil18),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                Text(
                    text = movitText("training_session_rest_timer", secondsRemaining),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                RestControlMiniButton(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    onClick = onTogglePause,
                    contentDescription = movitText(
                        if (isPaused) "training_session_resume" else "training_session_pause",
                    ),
                )
                RestControlMiniButton(
                    text = movitText("prepare_add_rest"),
                    onClick = onAddTime,
                    contentDescription = movitText("training_session_rest_add_time_a11y"),
                )
            }
        }
        val skipRestA11y = movitText("training_session_rest_skip_a11y")
        MovitButton(
            text = movitText("prepare_skip_rest"),
            onClick = onSkip,
            variant = MovitButtonVariant.Filled,
            modifier = Modifier.semantics {
                contentDescription = skipRestA11y
            },
        )
    }
}

@Composable
private fun RestControlMiniButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String? = null,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .then(if (text != null) Modifier else Modifier.size(36.dp))
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        contentColor = movit.onInk,
        border = BorderStroke(1.dp, movit.onInkVeil18),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = if (text != null) 10.dp else 0.dp),
        ) {
            when {
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(17.dp),
                )
                text != null -> Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W800,
                )
            }
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
