package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitRemoteImage
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.resources.movitText

@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    onStartExercise: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                title = movitText("exercise_detail_title"),
                backLabel = movitText("library_a11y_back"),
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
        },
        bottomBar = {
            val exercise = state.exercise
            if (exercise != null && !state.isLoading && state.errorMessage == null) {
                MovitButton(
                    text = movitText("exercise_detail_start"),
                    onClick = onStartExercise,
                    variant = MovitButtonVariant.Filled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MovitSpacing.lg),
                )
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
            state.exercise != null -> {
                val exercise = state.exercise
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    MovitRemoteImage(
                        imageUrl = exercise.heroImageUrl,
                        placeholderLabel = exercise.name,
                        contentDescription = movitText("prepare_a11y_hero_image"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.W800,
                        )
                        Text(
                            text = exercise.category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ExerciseStatChip(label = movitText("prepare_stat_sets"), value = exercise.sets)
                        ExerciseStatChip(label = movitText("prepare_stat_reps"), value = exercise.reps)
                        ExerciseStatChip(label = movitText("prepare_stat_rest"), value = exercise.rest)
                    }
                    if (exercise.targetMuscles.isNotEmpty()) {
                        MovitSectionHeader(title = movitText("prepare_target_muscles"))
                        Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                            exercise.targetMuscles.forEach { muscle ->
                                MovitTag(text = muscle, variant = MovitTagVariant.Blue)
                            }
                        }
                    }
                    if (exercise.instructions.isNotEmpty()) {
                        MovitSectionHeader(title = movitText("prepare_instructions"))
                        exercise.instructions.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = MovitSpacing.xs),
                            )
                        }
                    }
                    MovitSectionHeader(title = movitText("exercise_detail_equipment"))
                    Text(
                        text = exercise.equipment,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseStatChip(label: String, value: String) {
    Column(modifier = Modifier.padding(end = MovitSpacing.md)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
        )
    }
}
