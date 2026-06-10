package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun ExercisePrepareScreen(
    state: ExercisePrepareUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onSkipRest: () -> Unit,
    onToggleRestPause: () -> Unit,
    onAddRestTime: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.displayExercise
    val headerTitleKey = when (state.mode) {
        ExercisePrepareMode.Prepare -> "prepare_title"
        ExercisePrepareMode.Rest -> "prepare_rest_title"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                MovitInnerPageHeader(
                    onBack = onBack,
                    backLabel = movitText("prepare_back"),
                    title = movitText(headerTitleKey),
                    modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
                )
                if (exercise != null) {
                    MovitProgressBar(
                        progressPercent = state.headerProgressPercent,
                        modifier = Modifier
                            .padding(horizontal = MovitSpacing.lg)
                            .padding(bottom = MovitSpacing.sm),
                    )
                }
            }
        },
        bottomBar = {
            when {
                state.mode == ExercisePrepareMode.Prepare && state.exercise != null -> {
                    PrepareStartDock(
                        sessionSummary = state.exercise.sessionSummary,
                        onStart = onStart,
                        modifier = Modifier.padding(MovitSpacing.lg),
                    )
                }
                state.mode == ExercisePrepareMode.Rest -> {
                    RestTimerDock(
                        timerText = formatRestTimer(state.restSeconds),
                        isPaused = state.isRestPaused,
                        lowTime = state.restSeconds <= 5,
                        onTogglePause = onToggleRestPause,
                        onAddTime = onAddRestTime,
                        onSkip = onSkipRest,
                        modifier = Modifier.padding(MovitSpacing.lg),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> MovitLoadingState(message = movitText("prepare_loading"))
            state.errorMessage != null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = movitText(state.errorMessage),
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            exercise != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg)
                        .padding(bottom = MovitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    if (state.mode == ExercisePrepareMode.Rest) {
                        MovitTag(
                            text = movitText("prepare_up_next"),
                            variant = MovitTagVariant.Coral,
                        )
                    }
                    ExerciseHeroPreview(name = exercise.name)
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.W800,
                        )
                        Text(
                            text = exercise.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                    MovitStatTileRow(
                        stats = listOf(
                            MovitStatTileData(exercise.sets, movitText("prepare_stat_sets")),
                            MovitStatTileData(
                                exercise.reps,
                                movitText(exercise.repsLabelKey),
                            ),
                            MovitStatTileData(exercise.rest, movitText("prepare_stat_rest")),
                            MovitStatTileData("🏋", exercise.equipment),
                        ),
                    )
                    SetupGuideCard(
                        axesLabel = exercise.axesLabel,
                        distanceTip = exercise.distanceTip,
                    )
                    if (exercise.instructions.isNotEmpty()) {
                        MovitSectionHeader(title = movitText("prepare_instructions"))
                        InstructionsCard(steps = exercise.instructions)
                    }
                    if (exercise.targetMuscles.isNotEmpty()) {
                        MovitSectionHeader(title = movitText("prepare_target_muscles"))
                        TargetMusclesRow(muscles = exercise.targetMuscles)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHeroPreview(
    name: String,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(MovitSpacing.sm),
            shape = RoundedCornerShape(999.dp),
            color = movit.ink.copy(alpha = 0.72f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = movit.onInk,
                )
                Text(
                    text = movitText("prepare_looping_preview"),
                    style = MaterialTheme.typography.labelSmall,
                    color = movit.onInk,
                )
            }
        }
    }
}

@Composable
private fun SetupGuideCard(
    axesLabel: String,
    distanceTip: String,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Outlined,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(movit.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text("📷", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = movitText("prepare_camera_setup"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.W800,
                    )
                }
                Text(
                    text = axesLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.W700,
                    color = movit.primaryPress,
                )
                Text(
                    text = distanceTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun InstructionsCard(
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Outlined,
        contentPadding = MovitSpacing.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            steps.forEachIndexed { index, step ->
                InstructionStep(number = index + 1, text = step)
            }
        }
    }
}

@Composable
private fun InstructionStep(
    number: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onSecondary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetMusclesRow(
    muscles: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        muscles.forEach { muscle ->
            MovitTag(text = muscle, variant = MovitTagVariant.Blue)
        }
    }
}

@Composable
private fun PrepareStartDock(
    sessionSummary: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Elevated,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movitText("session_ready_to_train"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = sessionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            MovitButton(
                text = movitText("session_start"),
                onClick = onStart,
                variant = MovitButtonVariant.Filled,
                leadingIcon = Icons.Default.PlayArrow,
            )
        }
    }
}

@Composable
private fun RestTimerDock(
    timerText: String,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onAddTime: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    lowTime: Boolean = false,
) {
    val timerColor = if (lowTime) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovitCard(
            modifier = Modifier.weight(1f),
            variant = MovitCardVariant.Elevated,
            contentPadding = MovitSpacing.md,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            ) {
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                    color = timerColor,
                )
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RestMiniButton(
                        icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        onClick = onTogglePause,
                    )
                    RestMiniButton(
                        text = movitText("prepare_add_rest"),
                        onClick = onAddTime,
                    )
                }
            }
        }
        MovitButton(
            text = movitText("prepare_skip_rest"),
            onClick = onSkip,
            variant = MovitButtonVariant.Filled,
            modifier = Modifier,
        )
    }
}

@Composable
private fun RestMiniButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val movit = MaterialTheme.movitColors
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .then(if (text != null) Modifier.padding(horizontal = 0.dp) else Modifier.size(36.dp)),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = movit.textSecondary,
        border = androidx.compose.foundation.BorderStroke(1.dp, movit.stroke),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = if (text != null) 10.dp else 0.dp),
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                text != null -> Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W800,
                )
            }
        }
    }
}
