package com.movit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitSegmentedControl
import com.movit.feature.library.components.ProgramCopyCard
import com.movit.feature.library.components.ProgramDetailCardsSection
import com.movit.feature.library.components.ProgramEditPanel
import com.movit.feature.library.components.ProgramHeroSection
import com.movit.feature.library.components.ProgramStartDock
import com.movit.feature.library.components.ProgramStatGrid
import com.movit.feature.library.components.ProgramDaySessionsPanel
import com.movit.feature.library.components.ProgramWeekCard
import com.movit.feature.library.components.ProgramWeekOfflinePanel
import com.movit.feature.library.components.ProgramWeekStrip
import com.movit.resources.movitText

@Composable
fun ProgramDetailScreen(
    state: ProgramDetailUiState,
    onBack: () -> Unit,
    onTabSelected: (ProgramDetailTab) -> Unit,
    onWeekSelected: (Int) -> Unit,
    onDaySelected: (Int) -> Unit,
    onOpenDaySession: (String) -> Unit,
    onStartProgram: () -> Unit,
    onEditReasonSelected: (ProgramEditReason) -> Unit,
    onEditScopeSelected: (ProgramEditScope) -> Unit,
    onWeeklyTargetChange: (Int) -> Unit,
    onPauseCalendarToggle: () -> Unit,
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
    onResetEditDay: () -> Unit,
    onSaveEdit: () -> Unit,
    onViewWeeklyReport: () -> Unit,
    onDownloadWeekOffline: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOverview = state.selectedTab == ProgramDetailTab.Overview
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                backContentDescription = movitText("program_back"),
                actionLabel = when {
                    isOverview -> movitText("program_customize")
                    else -> movitText("program_save")
                },
                actionIcon = if (isOverview) Icons.Default.Edit else Icons.Default.Save,
                onAction = {
                    if (isOverview) {
                        onTabSelected(ProgramDetailTab.Edit)
                    } else {
                        onSaveEdit()
                    }
                },
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
        },
        bottomBar = {
            when {
                isOverview && state.nextSession != null -> {
                    ProgramStartDock(
                        title = state.nextSession.title,
                        subtitle = state.nextSession.subtitle,
                        ctaLabel = movitText(
                            if (state.enrollment.isEnrolled) "program_start_next" else "program_start",
                        ),
                        onStart = onStartProgram,
                        modifier = Modifier.padding(MovitSpacing.lg),
                    )
                }
                !isOverview -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MovitSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                    ) {
                        MovitButton(
                            text = movitText("program_cancel"),
                            onClick = { onTabSelected(ProgramDetailTab.Overview) },
                            variant = MovitButtonVariant.Outlined,
                            modifier = Modifier.weight(1f),
                        )
                        MovitButton(
                            text = movitText("program_save_copy"),
                            onClick = onSaveEdit,
                            variant = MovitButtonVariant.Filled,
                            leadingIcon = Icons.Default.Save,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                isOverview && !state.enrollment.isEnrolled -> {
                    MovitButton(
                        text = movitText("program_start"),
                        onClick = onStartProgram,
                        variant = MovitButtonVariant.Filled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MovitSpacing.lg),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> MovitLoadingState(message = movitText("program_loading"))
            state.errorMessage != null -> MovitErrorState(
                title = movitText("common_error_title"),
                message = when (val error = state.errorMessage) {
                    "program_not_found",
                    "program_no_upcoming_session",
                    "program_sign_in_to_enroll",
                    "program_sign_in_to_save_edits",
                    -> movitText(error)
                    else -> error
                },
                actionLabel = movitText("common_retry"),
                onRetry = onRetry,
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    if (state.isOffline) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = movitText("training_offline_banner"),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(MovitSpacing.sm),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ProgramHeroSection(
                        title = state.title,
                        description = state.description,
                        kickers = state.kickers,
                        imageUrl = state.imageUrl,
                    )
                    MovitSegmentedControl(
                        options = listOf(
                            movitText("program_tab_overview"),
                            movitText("program_tab_edit"),
                        ),
                        selectedIndex = if (isOverview) 0 else 1,
                        onOptionSelected = { index ->
                            onTabSelected(if (index == 0) ProgramDetailTab.Overview else ProgramDetailTab.Edit)
                        },
                    )
                    ProgramStatGrid(stats = state.stats)
                    if (state.enrollment.isEnrolled) {
                        ProgramCopyCard(
                            enrollment = state.enrollment,
                            onEditCopy = { onTabSelected(ProgramDetailTab.Edit) },
                            onResumeWeek = onStartProgram,
                        )
                    }
                    if (isOverview) {
                        ProgramOverviewContent(
                            state = state,
                            onWeekSelected = onWeekSelected,
                            onDaySelected = onDaySelected,
                            onOpenDaySession = onOpenDaySession,
                            onViewWeeklyReport = onViewWeeklyReport,
                            onDownloadWeekOffline = onDownloadWeekOffline,
                        )
                    } else {
                        ProgramEditPanel(
                            edit = state.edit,
                            onReasonSelected = onEditReasonSelected,
                            onScopeSelected = onEditScopeSelected,
                            onWeeklyTargetChange = onWeeklyTargetChange,
                            onPauseToggle = onPauseCalendarToggle,
                            onSessionMove = onSessionMove,
                            onExerciseParamChange = onExerciseParamChange,
                            onRemoveSession = onRemoveSession,
                            onRemoveExercise = onRemoveExercise,
                            onResetDay = onResetEditDay,
                            onSave = onSaveEdit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramOverviewContent(
    state: ProgramDetailUiState,
    onWeekSelected: (Int) -> Unit,
    onDaySelected: (Int) -> Unit,
    onOpenDaySession: (String) -> Unit,
    onViewWeeklyReport: () -> Unit,
    onDownloadWeekOffline: () -> Unit,
) {
    MovitSectionHeader(
        title = movitText("program_journey_title"),
        subtitle = movitText("program_journey_sub"),
        actionLabel = movitText("program_weekly_report"),
        onActionClick = onViewWeeklyReport,
    )
    ProgramWeekStrip(
        weeks = state.weeks,
        selectedWeekNumber = state.selectedWeekNumber,
        onWeekSelected = onWeekSelected,
    )
    if (state.enrollment.isEnrolled && state.weeks.isNotEmpty()) {
        ProgramWeekOfflinePanel(
            weekOffline = state.weekOffline,
            onDownload = onDownloadWeekOffline,
        )
    }
    val selectedWeek = state.weeks.firstOrNull { it.weekNumber == state.selectedWeekNumber }
        ?: state.weeks.firstOrNull()
    if (selectedWeek != null) {
        ProgramWeekCard(
            week = selectedWeek,
            selectedDayNumber = state.selectedDayNumber,
            onDaySelected = onDaySelected,
        )
        ProgramDaySessionsPanel(
            sessions = state.selectedDaySessions,
            onOpenSession = onOpenDaySession,
        )
    }
    state.weeks
        .filter { it.weekNumber != selectedWeek?.weekNumber && !it.isCurrent }
        .take(1)
        .forEach { week -> ProgramWeekCard(week = week) }
    MovitSectionHeader(
        title = movitText("program_details_title"),
        subtitle = movitText("program_details_sub"),
    )
    ProgramDetailCardsSection(cards = state.detailCards)
}
