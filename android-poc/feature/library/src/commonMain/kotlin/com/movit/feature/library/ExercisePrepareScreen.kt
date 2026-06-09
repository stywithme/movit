package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitActionDock
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.movitColors

@Composable
fun ExercisePrepareScreen(
    state: ExercisePrepareUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.exercise
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                MovitInnerPageHeader(
                    onBack = onBack,
                    backLabel = "Back",
                    title = "Prepare Workout",
                    modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
                )
                if (exercise != null) {
                    MovitProgressBar(
                        progressPercent = exercise.sessionProgressPercent,
                        modifier = Modifier
                            .padding(horizontal = MovitSpacing.lg)
                            .padding(bottom = MovitSpacing.sm),
                    )
                }
            }
        },
        bottomBar = {
            if (exercise != null) {
                MovitActionDock(
                    timerText = "0:00",
                    title = exercise.name,
                    subtitle = "Set 1 of ${exercise.sets}",
                    onPlayClick = onStart,
                    modifier = Modifier.padding(MovitSpacing.lg),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> MovitLoadingState(message = "Loading exercise…")
            state.errorMessage != null -> MovitErrorState(message = state.errorMessage, onRetry = onRetry)
            exercise != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = exercise.name.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.W800,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
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
                            MovitStatTileData(exercise.sets, "Sets"),
                            MovitStatTileData(exercise.reps, "Reps"),
                            MovitStatTileData(exercise.rest, "Rest"),
                            MovitStatTileData("🏋", exercise.equipment),
                        ),
                    )
                    SetupGuideCard(
                        axesLabel = exercise.axesLabel,
                        distanceTip = exercise.distanceTip,
                    )
                }
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
                    androidx.compose.material3.Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Camera setup",
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
