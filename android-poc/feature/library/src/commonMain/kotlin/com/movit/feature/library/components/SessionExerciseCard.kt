package com.movit.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitRemoteImage
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.library.WorkoutSessionBlockUi
import com.movit.feature.library.sessionRestLabel
import com.movit.feature.library.sessionSetsLabel
import com.movit.feature.library.sessionWeightLabel
import com.movit.resources.movitText

@Composable
fun SessionExerciseCard(
    exercise: WorkoutSessionBlockUi.Exercise,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onSwap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val setsLabel = sessionSetsLabel(exercise.sets, exercise.reps, exercise.durationSeconds)
    val weightLabel = sessionWeightLabel(exercise.weightKg)
    val restLabel = sessionRestLabel(exercise.restSeconds)

    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
        onClick = if (isEditMode) onEdit else onClick,
        contentPadding = MovitSpacing.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                SessionExerciseThumbnail(
                    index = exercise.index,
                    imageUrl = exercise.imageUrl,
                    name = exercise.name,
                    modifier = Modifier.size(96.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                    )
                    Text(
                        text = exercise.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.movitColors.textSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                        modifier = Modifier.padding(top = MovitSpacing.xs),
                    ) {
                        val setsIcon = if (exercise.durationSeconds != null && exercise.reps == null) {
                            Icons.Default.Schedule
                        } else {
                            Icons.Default.Layers
                        }
                        MovitTag(
                            text = setsLabel,
                            variant = MovitTagVariant.Lime,
                            icon = setsIcon,
                        )
                        weightLabel?.let {
                            MovitTag(
                                text = it,
                                variant = MovitTagVariant.Blue,
                                icon = Icons.Default.FitnessCenter,
                            )
                        }
                        if (restLabel.isNotBlank()) {
                            MovitTag(
                                text = restLabel,
                                variant = MovitTagVariant.Coral,
                                icon = Icons.Default.Schedule,
                            )
                        }
                    }
                }
            }
            if (isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onSwap) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = movitText("session_a11y_swap"),
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = movitText("session_a11y_edit"),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = movitText("session_a11y_delete"),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    SessionDragHandle(
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDragHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalDrag by remember { mutableFloatStateOf(0f) }
    IconButton(
        onClick = {},
        modifier = modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragEnd = { totalDrag = 0f },
                onDrag = { _, dragAmount ->
                    totalDrag += dragAmount.y
                    if (totalDrag < -72f) {
                        onMoveUp()
                        totalDrag = 0f
                    } else if (totalDrag > 72f) {
                        onMoveDown()
                        totalDrag = 0f
                    }
                },
            )
        },
    ) {
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = movitText("session_a11y_reorder"),
        )
    }
}

@Composable
fun SessionExerciseThumbnail(
    index: Int,
    imageUrl: String?,
    name: String,
    showIndex: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        MovitRemoteImage(
            imageUrl = imageUrl,
            contentDescription = movitText("session_a11y_exercise_thumb", name),
            placeholderLabel = name.firstOrNull()?.uppercase() ?: "?",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp)),
        )
        if (showIndex) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W800,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
fun SessionRestBlock(
    rest: WorkoutSessionBlockUi.Rest,
    isEditMode: Boolean = false,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val label = com.movit.feature.library.sessionRestBlockLabel(rest.durationLabel)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(movit.coralTint)
                .then(if (isEditMode) Modifier else Modifier)
                .padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            if (isEditMode) {
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = movitText("session_a11y_edit"),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = movitText("session_a11y_delete"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                SessionDragHandle(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
            }
        }
    }
}
