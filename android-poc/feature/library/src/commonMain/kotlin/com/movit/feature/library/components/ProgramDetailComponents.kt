package com.movit.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitRemoteImage
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStepper
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.library.ProgramDayStatus
import com.movit.feature.library.ProgramDayUi
import com.movit.feature.library.ProgramDetailCardUi
import com.movit.feature.library.ProgramEditExerciseUi
import com.movit.feature.library.ProgramEditReason
import com.movit.feature.library.ProgramEditScope
import com.movit.feature.library.ProgramEditSessionUi
import com.movit.feature.library.ProgramEditUiState
import com.movit.feature.library.ProgramEnrollmentUi
import com.movit.feature.library.ProgramStatUi
import com.movit.feature.library.ProgramWeekUi
import com.movit.resources.movitText

@Composable
fun ProgramHeroSection(
    title: String,
    description: String,
    kickers: List<String>,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val shape = RoundedCornerShape(MovitRadius.xl)
    val heroA11y = movitText("program_a11y_hero_image")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .semantics { contentDescription = heroA11y },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ),
                ),
        )
        if (!imageUrl.isNullOrBlank()) {
            MovitRemoteImage(
                imageUrl = imageUrl,
                contentDescription = null,
                placeholderLabel = title.take(1).uppercase(),
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(movit.inkVeil05, movit.inkVeil78),
                    ),
                ),
        )
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.W800,
                color = movit.onInkVeil22,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(MovitSpacing.lg),
        ) {
            if (kickers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
                    kickers.forEachIndexed { index, kicker ->
                        if (index == 0 && kicker.contains("featured", ignoreCase = true)) {
                            MovitTag(text = kicker, variant = MovitTagVariant.Lime)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(MovitRadius.full),
                                color = movit.onInkVeil22,
                            ) {
                                Text(
                                    text = kicker,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = movit.onInk,
                                    fontWeight = FontWeight.W700,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.W800,
                color = movit.onInk,
                modifier = Modifier.padding(top = MovitSpacing.sm),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = movit.onInkVeil70,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}

@Composable
fun ProgramStatGrid(
    stats: List<ProgramStatUi>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        stats.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                row.forEach { stat ->
                    ProgramStatCard(
                        stat = stat,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProgramStatCard(
    stat: ProgramStatUi,
    modifier: Modifier = Modifier,
) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Filled) {
        Column(modifier = Modifier.padding(MovitSpacing.md)) {
            Text(
                text = stat.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.movitColors.textTertiary,
            )
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stat.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.movitColors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun ProgramCopyCard(
    enrollment: ProgramEnrollmentUi,
    onEditCopy: () -> Unit,
    onResumeWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MovitRadius.xl)
    val primaryTintSolid = movit.primaryTint.compositeOver(scheme.surface)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(primaryTintSolid, scheme.surface)))
            .border(androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = 0.36f)), shape),
    ) {
        Column(modifier = Modifier.padding(MovitSpacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                        )
                    }
                }
                Column {
                    Text(
                        text = movitText("program_copy_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                    )
                    Text(
                        text = movitText("program_copy_body"),
                        style = MaterialTheme.typography.bodySmall,
                        color = movit.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = MovitSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                MovitTag(text = movitText("program_active_plan"), variant = MovitTagVariant.Lime)
                enrollment.startedLabel?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = movit.textTertiary)
                }
                if (enrollment.customEditsCount > 0) {
                    Text(
                        text = movitText("program_custom_edits", enrollment.customEditsCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = movit.textTertiary,
                    )
                }
                enrollment.syncLabel?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = movit.textTertiary)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MovitSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                MovitButton(
                    text = movitText("program_edit_copy"),
                    onClick = onEditCopy,
                    variant = MovitButtonVariant.Outlined,
                    modifier = Modifier.weight(1f),
                )
                MovitButton(
                    text = movitText("program_resume_week"),
                    onClick = onResumeWeek,
                    variant = MovitButtonVariant.Filled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ProgramWeekStrip(
    weeks: List<ProgramWeekUi>,
    selectedWeekNumber: Int,
    onWeekSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripA11y = movitText("program_a11y_week_strip")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = stripA11y },
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        weeks.forEach { week ->
            val selected = week.weekNumber == selectedWeekNumber
            val weekA11y = movitText(
                "program_a11y_week_card",
                week.label,
                week.theme,
                week.progressPercent,
            )
            MovitCard(
                variant = if (selected) MovitCardVariant.Outlined else MovitCardVariant.Filled,
                onClick = { onWeekSelected(week.weekNumber) },
                modifier = Modifier.semantics { contentDescription = weekA11y },
            ) {
                Column(modifier = Modifier.padding(MovitSpacing.md)) {
                    Text(text = week.label, fontWeight = FontWeight.W800)
                    Text(
                        text = week.theme,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    MovitProgressBar(
                        progressPercent = week.progressPercent,
                        modifier = Modifier.padding(top = MovitSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
fun ProgramWeekCard(
    week: ProgramWeekUi,
    modifier: Modifier = Modifier,
) {
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = if (week.isCurrent) MovitCardVariant.Outlined else MovitCardVariant.Filled,
    ) {
        Column(modifier = Modifier.padding(MovitSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${week.label} · ${week.theme}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                    )
                    Text(
                        text = week.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.movitColors.textTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                when {
                    week.isCurrent -> MovitTag(text = movitText("program_current"), variant = MovitTagVariant.Blue)
                    week.isLocked -> Text(
                        text = movitText("program_preview"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textTertiary,
                    )
                }
            }
            if (week.progressPercent > 0) {
                MovitProgressBar(
                    progressPercent = week.progressPercent,
                    modifier = Modifier.padding(top = MovitSpacing.sm),
                )
            }
            Column(
                modifier = Modifier.padding(top = MovitSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                week.days.forEach { day -> ProgramDayRow(day = day) }
            }
        }
    }
}

@Composable
private fun ProgramDayRow(day: ProgramDayUi) {
    val movit = MaterialTheme.movitColors
    val dotColor = when (day.status) {
        ProgramDayStatus.Done -> movit.success
        ProgramDayStatus.Next -> MaterialTheme.colorScheme.primary
        ProgramDayStatus.Rest -> movit.stroke
        ProgramDayStatus.Upcoming -> MaterialTheme.colorScheme.primary
    }
    val dotContent = when (day.status) {
        ProgramDayStatus.Rest -> "R"
        else -> day.dayNumber.toString()
    }
    Surface(
        shape = RoundedCornerShape(MovitRadius.lg),
        color = movit.surface2,
        border = androidx.compose.foundation.BorderStroke(1.dp, movit.divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(13.dp),
                color = dotColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = dotContent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.W800,
                        color = when (day.status) {
                            ProgramDayStatus.Done -> movit.ink
                            ProgramDayStatus.Rest -> movit.textSecondary
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = day.title, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = day.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = movit.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            when (day.status) {
                ProgramDayStatus.Done -> MovitTag(text = movitText("program_done"), variant = MovitTagVariant.Lime)
                ProgramDayStatus.Next -> MovitTag(text = movitText("program_next"), variant = MovitTagVariant.Blue)
                else -> Unit
            }
        }
    }
}

@Composable
fun ProgramDetailCardsSection(
    cards: List<ProgramDetailCardUi>,
    modifier: Modifier = Modifier,
) {
    val icons = listOf(Icons.Default.TrackChanges, Icons.Default.Layers, Icons.Default.CalendarMonth)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        cards.forEachIndexed { index, card ->
            ProgramDetailInfoCard(
                title = card.title,
                description = card.description,
                icon = icons.getOrElse(index) { Icons.Default.TrackChanges },
            )
        }
    }
}

@Composable
private fun ProgramDetailInfoCard(
    title: String,
    description: String,
    icon: ImageVector,
) {
    MovitCard(variant = MovitCardVariant.Filled) {
        Row(
            modifier = Modifier.padding(MovitSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column {
                Text(text = title, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textTertiary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
fun ProgramEditPanel(
    edit: ProgramEditUiState,
    onReasonSelected: (ProgramEditReason) -> Unit,
    onScopeSelected: (ProgramEditScope) -> Unit,
    onWeeklyTargetChange: (Int) -> Unit,
    onPauseToggle: () -> Unit,
    onSessionMove: (sessionId: String, direction: Int) -> Unit,
    onExerciseParamChange: (
        sessionId: String,
        exerciseId: String,
        sets: Int?,
        reps: Int?,
        weightKg: Double?,
        restSeconds: Int?,
    ) -> Unit,
    onRemoveSession: (sessionId: String) -> Unit,
    onRemoveExercise: (sessionId: String, exerciseId: String) -> Unit,
    onResetDay: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
    ) {
        MovitSectionHeader(
            title = movitText("program_edit_why_title"),
            subtitle = movitText("program_edit_customize"),
            actionLabel = movitText("program_save"),
            onActionClick = onSave,
        )
        Surface(
            shape = RoundedCornerShape(MovitRadius.lg),
            color = MaterialTheme.movitColors.limeTint,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.movitColors.success.copy(alpha = 0.35f),
            ),
        ) {
            Text(
                text = movitText("program_edit_note"),
                modifier = Modifier.padding(MovitSpacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
        }
        if (edit.showSaveToast) {
            Surface(
                shape = RoundedCornerShape(MovitRadius.lg),
                color = MaterialTheme.movitColors.successTint,
            ) {
                Row(
                    modifier = Modifier.padding(MovitSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text(text = movitText("program_save_toast"), fontWeight = FontWeight.W700)
                }
            }
        }
        ProgramReasonGrid(selected = edit.selectedReason, onSelected = onReasonSelected)
        MovitSectionHeader(title = movitText("program_scope_title"), subtitle = movitText("program_scope_sub"))
        ProgramScopeList(selected = edit.selectedScope, onSelected = onScopeSelected)
        ProgramImpactCard(edit = edit)
        ProgramSettingsStack(
            edit = edit,
            onWeeklyTargetChange = onWeeklyTargetChange,
            onPauseToggle = onPauseToggle,
        )
        ProgramEditDaySection(
            edit = edit,
            onSessionMove = onSessionMove,
            onExerciseParamChange = onExerciseParamChange,
            onRemoveSession = onRemoveSession,
            onRemoveExercise = onRemoveExercise,
            onResetDay = onResetDay,
        )
        edit.saveError?.let { message ->
            Surface(
                shape = RoundedCornerShape(MovitRadius.lg),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(MovitSpacing.md),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (edit.isSaving) {
            Text(
                text = movitText("program_edit_saving"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.movitColors.textTertiary,
            )
        }
    }
}

@Composable
private fun ProgramReasonGrid(
    selected: ProgramEditReason,
    onSelected: (ProgramEditReason) -> Unit,
) {
    val reasons = listOf(
        ProgramEditReason.ScheduleChanged to movitText("program_reason_schedule"),
        ProgramEditReason.EquipmentMissing to movitText("program_reason_equipment"),
        ProgramEditReason.TooEasyHard to movitText("program_reason_intensity"),
        ProgramEditReason.InjuryDiscomfort to movitText("program_reason_injury"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        reasons.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                row.forEach { (reason, title) ->
                    val active = reason == selected
                    MovitCard(
                        modifier = Modifier.weight(1f),
                        variant = if (active) MovitCardVariant.Outlined else MovitCardVariant.Filled,
                        onClick = { onSelected(reason) },
                    ) {
                        Text(text = title, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramScopeList(
    selected: ProgramEditScope,
    onSelected: (ProgramEditScope) -> Unit,
) {
    val scopes = listOf(
        ProgramEditScope.PlanSettings to movitText("program_scope_plan"),
        ProgramEditScope.WeekCalendar to movitText("program_scope_week"),
        ProgramEditScope.DaySessions to movitText("program_scope_day"),
        ProgramEditScope.ExerciseTargets to movitText("program_scope_exercise"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        scopes.forEachIndexed { index, (scope, title) ->
            val active = scope == selected
            MovitCard(
                variant = if (active) MovitCardVariant.Outlined else MovitCardVariant.Filled,
                onClick = { onSelected(scope) },
            ) {
                Row(
                    modifier = Modifier.padding(MovitSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.movitColors.surface2
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                fontWeight = FontWeight.W800,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.movitColors.textSecondary
                                },
                            )
                        }
                    }
                    Text(text = title, fontWeight = FontWeight.W800, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProgramImpactCard(edit: ProgramEditUiState) {
    MovitCard(variant = MovitCardVariant.Filled) {
        Column(modifier = Modifier.padding(MovitSpacing.md)) {
            ImpactRow(
                label = movitText("program_impact_editing"),
                value = edit.editingDayTitle.ifBlank {
                    "Week ${edit.editingWeekNumber} · Day ${edit.editingDayNumber}"
                },
            )
            ImpactRow(label = movitText("program_impact_plan"), value = movitText("program_impact_user_copy"))
            ImpactRow(label = movitText("program_impact_template"), value = movitText("program_impact_unchanged"))
            ImpactRow(
                label = movitText("program_impact_sync"),
                value = movitText("program_impact_sync_value", edit.daySessions.size),
            )
        }
    }
}

@Composable
private fun ProgramEditDaySection(
    edit: ProgramEditUiState,
    onSessionMove: (sessionId: String, direction: Int) -> Unit,
    onExerciseParamChange: (
        sessionId: String,
        exerciseId: String,
        sets: Int?,
        reps: Int?,
        weightKg: Double?,
        restSeconds: Int?,
    ) -> Unit,
    onRemoveSession: (sessionId: String) -> Unit,
    onRemoveExercise: (sessionId: String, exerciseId: String) -> Unit,
    onResetDay: () -> Unit,
) {
    MovitSectionHeader(
        title = movitText("program_edit_day_title"),
        subtitle = edit.editingDayTitle.ifBlank {
            movitText("program_edit_day_sub", edit.editingWeekNumber, edit.editingDayNumber)
        },
        actionLabel = movitText("program_edit_reset_day"),
        onActionClick = onResetDay,
    )
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        edit.daySessions.forEachIndexed { index, session ->
            ProgramEditSessionCard(
                session = session,
                canMoveUp = index > 0,
                canMoveDown = index < edit.daySessions.lastIndex,
                onMoveUp = { onSessionMove(session.id, -1) },
                onMoveDown = { onSessionMove(session.id, 1) },
                onRemoveSession = { onRemoveSession(session.id) },
                onExerciseParamChange = { exerciseId, sets, reps, weightKg, restSeconds ->
                    onExerciseParamChange(session.id, exerciseId, sets, reps, weightKg, restSeconds)
                },
                onRemoveExercise = { exerciseId -> onRemoveExercise(session.id, exerciseId) },
            )
        }
    }
}

@Composable
private fun ProgramEditSessionCard(
    session: ProgramEditSessionUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemoveSession: () -> Unit,
    onExerciseParamChange: (
        exerciseId: String,
        sets: Int?,
        reps: Int?,
        weightKg: Double?,
        restSeconds: Int?,
    ) -> Unit,
    onRemoveExercise: (exerciseId: String) -> Unit,
) {
    MovitCard(variant = MovitCardVariant.Outlined) {
        Column(
            modifier = Modifier.padding(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                ProgramEditDragHandle(
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    enabled = canMoveUp || canMoveDown,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = session.title, fontWeight = FontWeight.W800)
                    Text(
                        text = movitText("program_edit_session_meta", session.exercises.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textTertiary,
                    )
                }
                if (session.isEdited) {
                    MovitTag(text = movitText("program_edit_session_edited"), variant = MovitTagVariant.Lime)
                }
                IconButton(onClick = onRemoveSession) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = movitText("program_edit_remove_session"),
                    )
                }
            }
            session.exercises.forEach { exercise ->
                ProgramEditExerciseParamRow(
                    exercise = exercise,
                    onParamChange = onExerciseParamChange,
                    onRemove = { onRemoveExercise(exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun ProgramEditExerciseParamRow(
    exercise: ProgramEditExerciseUi,
    onParamChange: (
        exerciseId: String,
        sets: Int?,
        reps: Int?,
        weightKg: Double?,
        restSeconds: Int?,
    ) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(MovitRadius.lg),
        color = MaterialTheme.movitColors.surface2,
    ) {
        Column(
            modifier = Modifier.padding(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = exercise.name, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyMedium)
                    if (exercise.isEdited) {
                        MovitTag(text = movitText("program_edit_session_edited"), variant = MovitTagVariant.Blue)
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = movitText("program_edit_remove_exercise"),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                ProgramEditParamCell(
                    label = movitText("program_edit_exercise_sets"),
                    modifier = Modifier.weight(1f),
                ) {
                    MovitStepper(
                        value = exercise.sets,
                        onDecrement = { onParamChange(exercise.id, exercise.sets - 1, null, null, null) },
                        onIncrement = { onParamChange(exercise.id, exercise.sets + 1, null, null, null) },
                        minValue = 1,
                        maxValue = 12,
                    )
                }
                ProgramEditParamCell(
                    label = movitText("program_edit_exercise_reps"),
                    modifier = Modifier.weight(1f),
                ) {
                    MovitStepper(
                        value = exercise.reps ?: 0,
                        onDecrement = {
                            val next = (exercise.reps ?: 0) - 1
                            onParamChange(exercise.id, null, next.coerceAtLeast(0), null, null)
                        },
                        onIncrement = {
                            onParamChange(exercise.id, null, (exercise.reps ?: 0) + 1, null, null)
                        },
                        minValue = 0,
                        maxValue = 30,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
            ) {
                ProgramEditParamCell(
                    label = movitText("program_edit_exercise_weight"),
                    modifier = Modifier.weight(1f),
                ) {
                    MovitStepper(
                        value = (exercise.weightKg ?: 0.0).toInt(),
                        onDecrement = {
                            val next = (exercise.weightKg ?: 0.0) - 2.5
                            onParamChange(exercise.id, null, null, next.coerceAtLeast(0.0), null)
                        },
                        onIncrement = {
                            onParamChange(exercise.id, null, null, (exercise.weightKg ?: 0.0) + 2.5, null)
                        },
                        minValue = 0,
                        maxValue = 200,
                    )
                }
                ProgramEditParamCell(
                    label = movitText("program_edit_exercise_rest"),
                    modifier = Modifier.weight(1f),
                ) {
                    MovitStepper(
                        value = exercise.restSeconds,
                        onDecrement = {
                            onParamChange(exercise.id, null, null, null, (exercise.restSeconds - 5).coerceAtLeast(0))
                        },
                        onIncrement = {
                            onParamChange(exercise.id, null, null, null, exercise.restSeconds + 5)
                        },
                        minValue = 0,
                        maxValue = 180,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramEditParamCell(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.movitColors.textTertiary,
            fontWeight = FontWeight.W700,
        )
        content()
    }
}

@Composable
private fun ProgramEditDragHandle(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var totalDrag by remember { mutableFloatStateOf(0f) }
    IconButton(
        onClick = {},
        enabled = enabled,
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
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
            tint = if (enabled) {
                MaterialTheme.movitColors.textSecondary
            } else {
                MaterialTheme.movitColors.textTertiary
            },
        )
    }
}

@Composable
private fun ImpactRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.movitColors.textSecondary)
        Text(text = value, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProgramSettingsStack(
    edit: ProgramEditUiState,
    onWeeklyTargetChange: (Int) -> Unit,
    onPauseToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        SettingRow(
            label = movitText("program_setting_start_date"),
            desc = movitText("program_setting_start_date_sub"),
            value = edit.startDateLabel,
        )
        MovitCard(variant = MovitCardVariant.Filled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MovitSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = movitText("program_setting_weekly"), fontWeight = FontWeight.W800)
                    Text(
                        text = movitText("program_setting_weekly_sub"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textTertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MovitButton(text = "−", onClick = { onWeeklyTargetChange(-1) }, variant = MovitButtonVariant.Outlined)
                    Text(text = edit.weeklyTarget.toString(), fontWeight = FontWeight.W800)
                    MovitButton(text = "+", onClick = { onWeeklyTargetChange(1) }, variant = MovitButtonVariant.Outlined)
                }
            }
        }
        MovitCard(variant = MovitCardVariant.Filled, onClick = onPauseToggle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MovitSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = movitText("program_setting_pause"), fontWeight = FontWeight.W800)
                    Text(
                        text = movitText("program_setting_pause_sub"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.movitColors.textTertiary,
                    )
                }
                MovitTag(
                    text = if (edit.pauseCalendar) movitText("program_paused") else movitText("program_active"),
                    variant = if (edit.pauseCalendar) MovitTagVariant.Blue else MovitTagVariant.Lime,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, desc: String, value: String) {
    MovitCard(variant = MovitCardVariant.Filled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontWeight = FontWeight.W800)
                Text(text = desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.movitColors.textTertiary)
            }
            Surface(
                shape = RoundedCornerShape(MovitRadius.full),
                color = MaterialTheme.movitColors.surface2,
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontWeight = FontWeight.W800,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun ProgramStartDock(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockA11y = movitText("program_a11y_start_dock", title, subtitle)
    MovitCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = dockA11y },
        variant = MovitCardVariant.Elevated,
        contentPadding = MovitSpacing.md,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.W800, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.movitColors.textTertiary,
                )
            }
            MovitButton(text = ctaLabel, onClick = onStart, variant = MovitButtonVariant.Filled)
        }
    }
}
