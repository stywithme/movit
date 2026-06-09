package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun ProgramWeekPlanScreen(
    state: ProgramWeekPlanUiState,
    onBack: () -> Unit,
    onDayClick: (ProgramDayUi) -> Unit,
    onOpenTodaySession: () -> Unit,
    onViewWeeklyReport: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekPlan = state.weekPlan
    Column(modifier = modifier.fillMaxSize()) {
        MovitInnerPageHeader(
            onBack = onBack,
            title = weekPlan?.weekTitle ?: movitText("program_flow_week_title", 1),
            modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
        )
        when {
            state.isLoading -> MovitLoadingState(message = movitText("program_flow_loading"))
            state.errorMessage != null -> MovitErrorState(message = state.errorMessage, onRetry = onRetry)
            weekPlan != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                ) {
                    Text(
                        text = weekPlan.programName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.movitColors.textSecondary,
                    )
                    Text(
                        text = weekPlan.weekSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.movitColors.textTertiary,
                        modifier = Modifier.padding(bottom = MovitSpacing.sm),
                    )
                    weekPlan.days.forEach { day ->
                        ProgramDayPill(
                            day = day,
                            onClick = {
                                if (!day.isRestDay && day.plannedWorkoutId != null) {
                                    onDayClick(day)
                                }
                            },
                        )
                    }
                    if (weekPlan.todayDayNumber != null) {
                        MovitButton(
                            text = movitText("program_flow_open_today"),
                            onClick = onOpenTodaySession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MovitSpacing.md),
                        )
                    }
                    MovitButton(
                        text = movitText("program_flow_view_week_report"),
                        onClick = onViewWeeklyReport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MovitSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramDayPill(
    day: ProgramDayUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MovitRadius.md)
    val movit = MaterialTheme.movitColors
    val dayNumberColors = when (day.status) {
        ProgramDayStatus.Done -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        ProgramDayStatus.Today -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        else -> movit.surface2 to MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (day.status == ProgramDayStatus.Today) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !day.isRestDay, onClick = onClick)
            .padding(MovitSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(dayNumberColors.first),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W800,
                color = dayNumberColors.second,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
            )
            Text(
                text = day.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
        }
        if (!day.isRestDay) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.movitColors.textTertiary,
            )
        }
    }
}
