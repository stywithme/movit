package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitSearchBar
import com.movit.designsystem.components.MovitStepper
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.library.components.SessionExerciseThumbnail
import com.movit.resources.movitText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionSheets(
    activeSheet: SessionSheet?,
    session: WorkoutSessionUi?,
    onDismiss: () -> Unit,
    onSwapQueryChange: (String) -> Unit,
    onSwapCandidateSelected: (String) -> Unit,
    onAddExerciseQueryChange: (String) -> Unit,
    onAddExerciseCandidateSelected: (String) -> Unit,
    onEditDraftChange: ((ExerciseEditDraft) -> ExerciseEditDraft) -> Unit,
    onSaveEditDetails: () -> Unit,
    onSwitchEditToSwap: () -> Unit,
    onRestDurationChange: (Int) -> Unit,
    onSaveRestEdit: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    when (activeSheet) {
        is SessionSheet.Swap -> ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            SwapExerciseSheetContent(
                sheet = activeSheet,
                onDismiss = onDismiss,
                onQueryChange = onSwapQueryChange,
                onCandidateSelected = onSwapCandidateSelected,
            )
        }
        is SessionSheet.EditDetails -> {
            val exercise = session?.sections
                ?.flatMap { it.items }
                ?.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
                ?.firstOrNull { it.id == activeSheet.exerciseId }
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
            ) {
                EditDetailsSheetContent(
                    exercise = exercise,
                    sheet = activeSheet,
                    onDismiss = onDismiss,
                    onDraftChange = onEditDraftChange,
                    onSave = onSaveEditDetails,
                    onChangeExercise = onSwitchEditToSwap,
                )
            }
        }
        is SessionSheet.AddExercise -> ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            AddExerciseSheetContent(
                sheet = activeSheet,
                onDismiss = onDismiss,
                onQueryChange = onAddExerciseQueryChange,
                onCandidateSelected = onAddExerciseCandidateSelected,
            )
        }
        is SessionSheet.EditRest -> ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            EditRestSheetContent(
                sheet = activeSheet,
                onDismiss = onDismiss,
                onDurationChange = onRestDurationChange,
                onSave = onSaveRestEdit,
            )
        }
        null -> Unit
    }
}

@Composable
private fun SwapExerciseSheetContent(
    sheet: SessionSheet.Swap,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCandidateSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MovitSpacing.lg)
            .padding(bottom = MovitSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movitText("session_swap_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = movitText("session_swap_subtitle", sheet.exerciseName, sheet.exerciseCategory),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = movitText("session_close"))
            }
        }
        MovitSearchBar(
            query = sheet.query,
            onQueryChange = onQueryChange,
            placeholder = movitText("session_search_placeholder"),
        )
        if (sheet.isLoadingCandidates) {
            MovitLoadingState(message = movitText("session_finding_alternatives"))
        } else {
            Text(
                text = movitText("session_recommended"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.movitColors.textSecondary,
            )
            sheet.candidates.forEach { candidate ->
                SwapCandidateRow(
                    candidate = candidate,
                    onClick = { onCandidateSelected(candidate.slug) },
                )
            }
        }
    }
}

@Composable
private fun SwapCandidateRow(
    candidate: SessionSwapCandidateUi,
    onClick: () -> Unit,
) {
    MovitCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
        onClick = onClick,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionExerciseThumbnail(
                index = 0,
                imageUrl = candidate.imageUrl,
                name = candidate.name,
                showIndex = false,
                modifier = Modifier.size(52.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = candidate.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            candidate.badge?.let {
                MovitTag(
                    text = it,
                    variant = MovitTagVariant.Lime,
                    icon = Icons.Default.Check,
                )
            }
        }
    }
}

@Composable
private fun EditDetailsSheetContent(
    exercise: WorkoutSessionBlockUi.Exercise?,
    sheet: SessionSheet.EditDetails,
    onDismiss: () -> Unit,
    onDraftChange: ((ExerciseEditDraft) -> ExerciseEditDraft) -> Unit,
    onSave: () -> Unit,
    onChangeExercise: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MovitSpacing.lg)
            .padding(bottom = MovitSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionExerciseThumbnail(
                index = exercise?.index ?: 1,
                imageUrl = exercise?.imageUrl,
                name = exercise?.name ?: "",
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise?.name ?: movitText("session_exercise_fallback"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                TextButton(onClick = onChangeExercise) {
                    Text(movitText("session_change_exercise"))
                }
            }
        }
        EditStepperRow(
            title = movitText("session_sets_title"),
            subtitle = movitText("session_sets_sub"),
            value = sheet.draft.sets,
            minValue = 1,
            maxValue = 20,
            onDecrement = { onDraftChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } },
            onIncrement = { onDraftChange { it.copy(sets = (it.sets + 1).coerceAtMost(20)) } },
        )
        if (sheet.draft.durationSeconds != null || sheet.draft.reps == null) {
            EditStepperRow(
                title = movitText("session_hold_title"),
                subtitle = movitText("session_hold_sub"),
                value = sheet.draft.durationSeconds ?: 30,
                minValue = 5,
                maxValue = 300,
                onDecrement = {
                    onDraftChange {
                        it.copy(durationSeconds = ((it.durationSeconds ?: 30) - 5).coerceAtLeast(5))
                    }
                },
                onIncrement = {
                    onDraftChange {
                        it.copy(durationSeconds = ((it.durationSeconds ?: 30) + 5).coerceAtMost(300))
                    }
                },
            )
        } else {
            EditStepperRow(
                title = movitText("session_reps_title"),
                subtitle = movitText("session_reps_sub"),
                value = sheet.draft.reps ?: 12,
                minValue = 1,
                maxValue = 100,
                onDecrement = { onDraftChange { it.copy(reps = ((it.reps ?: 12) - 1).coerceAtLeast(1)) } },
                onIncrement = { onDraftChange { it.copy(reps = ((it.reps ?: 12) + 1).coerceAtMost(100)) } },
            )
        }
        EditStepperRow(
            title = movitText("session_weight_title"),
            subtitle = movitText("session_weight_sub"),
            value = (sheet.draft.weightKg ?: 0f).toInt(),
            minValue = 0,
            maxValue = 500,
            onDecrement = {
                onDraftChange {
                    val next = ((it.weightKg ?: 0f) - 2.5f).coerceAtLeast(0f)
                    it.copy(weightKg = if (next <= 0f) null else next)
                }
            },
            onIncrement = {
                onDraftChange {
                    val next = ((it.weightKg ?: 0f) + 2.5f).coerceAtMost(500f)
                    it.copy(weightKg = next)
                }
            },
        )
        EditStepperRow(
            title = movitText("session_rest_title"),
            subtitle = movitText("session_rest_sub"),
            value = sheet.draft.restSeconds,
            minValue = 0,
            maxValue = 300,
            onDecrement = { onDraftChange { it.copy(restSeconds = (it.restSeconds - 5).coerceAtLeast(0)) } },
            onIncrement = { onDraftChange { it.copy(restSeconds = (it.restSeconds + 5).coerceAtMost(300)) } },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            MovitButton(
                text = movitText("session_cancel"),
                onClick = onDismiss,
                variant = MovitButtonVariant.Outlined,
                modifier = Modifier.weight(1f),
            )
            MovitButton(
                text = movitText("session_save"),
                onClick = onSave,
                variant = MovitButtonVariant.Filled,
                leadingIcon = Icons.Default.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AddExerciseSheetContent(
    sheet: SessionSheet.AddExercise,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCandidateSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MovitSpacing.lg)
            .padding(bottom = MovitSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movitText("session_add_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
                Text(
                    text = movitText("session_add_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = movitText("session_close"))
            }
        }
        MovitSearchBar(
            query = sheet.query,
            onQueryChange = onQueryChange,
            placeholder = movitText("session_search_placeholder"),
        )
        if (sheet.isLoadingCandidates) {
            MovitLoadingState(message = movitText("session_finding_alternatives"))
        } else {
            sheet.candidates.forEach { candidate ->
                SwapCandidateRow(
                    candidate = candidate,
                    onClick = { onCandidateSelected(candidate.slug) },
                )
            }
        }
    }
}

@Composable
private fun EditRestSheetContent(
    sheet: SessionSheet.EditRest,
    onDismiss: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MovitSpacing.lg)
            .padding(bottom = MovitSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Text(
            text = movitText("session_edit_rest_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
        )
        EditStepperRow(
            title = movitText("session_rest_title"),
            subtitle = movitText("session_rest_sub"),
            value = sheet.durationSeconds,
            minValue = 5,
            maxValue = 600,
            onDecrement = { onDurationChange((sheet.durationSeconds - 5).coerceAtLeast(5)) },
            onIncrement = { onDurationChange((sheet.durationSeconds + 5).coerceAtMost(600)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            MovitButton(
                text = movitText("session_cancel"),
                onClick = onDismiss,
                variant = MovitButtonVariant.Outlined,
                modifier = Modifier.weight(1f),
            )
            MovitButton(
                text = movitText("session_save"),
                onClick = onSave,
                variant = MovitButtonVariant.Filled,
                leadingIcon = Icons.Default.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EditStepperRow(
    title: String,
    subtitle: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.W800)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
        }
        MovitStepper(
            value = value,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
            minValue = minValue,
            maxValue = maxValue,
        )
    }
}
