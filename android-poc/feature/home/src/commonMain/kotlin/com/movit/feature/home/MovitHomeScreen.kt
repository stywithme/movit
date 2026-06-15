package com.movit.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitAccentBlock
import com.movit.designsystem.components.MovitAccentVariant
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitListGroup
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitHeaderVariant
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.movitFloatingNavScrollPadding
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.home.components.HomeHeroSummary
import com.movit.feature.home.components.HomeLevelCard
import com.movit.feature.home.components.HomeProgressSection
import com.movit.feature.home.components.HomeQuickActions
import com.movit.feature.home.components.HomeReportPreview
import com.movit.feature.home.components.TodayPlanCard
import com.movit.resources.movitText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovitHomeScreen(
    state: MovitHomeUiState,
    onEvent: (MovitHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRefresh = state.errorMessage == null && !state.isLoading
    val headerGreeting = state.greetingEyebrow.ifBlank {
        movitText(HomeTimeGreeting.stringKeyNow())
    }

    MovitScaffold(
        modifier = modifier,
        headerVariant = MovitHeaderVariant.Home,
        greeting = headerGreeting,
        userName = state.userName,
        hasUnreadNotifications = state.hasUnreadNotifications,
        onNotificationClick = { onEvent(MovitHomeEvent.NotificationClicked) },
        onProfileClick = { onEvent(MovitHomeEvent.ProfileClicked) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                if (canRefresh) {
                    onEvent(MovitHomeEvent.RefreshRequested)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(MovitSpacing.lg)
                    .movitFloatingNavScrollPadding(),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
            ) {
            when {
                state.isLoading && state.metricTiles.isEmpty() && state.todayPlan == null -> {
                    MovitLoadingState(message = movitText("home_loading"))
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        title = movitText("common_error_title"),
                        message = state.errorMessage,
                        actionLabel = movitText("common_retry"),
                        onRetry = { onEvent(MovitHomeEvent.RetryClicked) },
                    )
                }
                else -> {
                    HomeHeroSummary(
                        greetingEyebrow = state.greetingEyebrow,
                        greetingTitle = state.greetingTitle,
                        greetingSubtitle = state.greetingSubtitle,
                        progress = state.progress,
                    )

                    if (state.metricTiles.isNotEmpty()) {
                        MovitStatTileRow(
                            stats = state.metricTiles.map {
                                MovitStatTileData(value = it.value, label = it.label)
                            },
                            coloredValues = true,
                        )
                    }

                    state.levelCard?.let { level ->
                        HomeLevelCard(
                            level = level,
                            onClick = { onEvent(MovitHomeEvent.LevelCardClicked) },
                        )
                    }

                    state.catchUp?.let { catchUp ->
                        val catchUpA11y = movitText("home_a11y_catch_up", catchUp.message)
                        Column(
                            modifier = Modifier.semantics { contentDescription = catchUpA11y },
                            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        ) {
                            MovitInsightCard(
                                title = movitText("session_catch_up_title"),
                                message = catchUp.message,
                                icon = Icons.Default.EventAvailable,
                                variant = MovitInsightVariant.Warning,
                            )
                            MovitButton(
                                text = movitText("session_catch_up_open_missed"),
                                onClick = { onEvent(MovitHomeEvent.CatchUpOpenClicked) },
                                variant = MovitButtonVariant.Outlined,
                                size = MovitButtonSize.Small,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    state.alert?.let { alert ->
                        MovitInsightCard(
                            title = alert.title,
                            message = alert.message,
                            icon = Icons.Default.Warning,
                            variant = MovitInsightVariant.Warning,
                            modifier = Modifier.clickable {
                                onEvent(MovitHomeEvent.AlertClicked(alert.type))
                            },
                        )
                    } ?: state.insightMessage?.let { message ->
                        MovitInsightCard(
                            title = movitText("home_plan_adjusted"),
                            message = message,
                            icon = Icons.Default.Warning,
                            variant = MovitInsightVariant.Warning,
                            modifier = Modifier.clickable {
                                onEvent(MovitHomeEvent.AlertClicked("progression_applied"))
                            },
                        )
                    }

                    state.activeProgram?.let { program ->
                        HomeActiveProgramSection(
                            program = program,
                            onViewProgram = {
                                onEvent(MovitHomeEvent.ViewProgramClicked(program.programId))
                            },
                        )
                    }

                    state.todayPlan?.let { plan ->
                        TodayPlanCard(
                            todayPlan = plan,
                            onStartPlan = {
                                if (plan.opensAssessment) {
                                    onEvent(MovitHomeEvent.BodyScanClicked)
                                } else {
                                    onEvent(MovitHomeEvent.StartTodayPlanClicked)
                                }
                            },
                            showPrimaryAction = plan.showPrimaryAction,
                        )
                    }

                    if (state.showBodyScanCta) {
                        val scanA11y = movitText("home_a11y_body_scan")
                        MovitAccentBlock(
                            title = movitText("home_body_scan"),
                            subtitle = movitText("home_body_scan_subtitle"),
                            variant = MovitAccentVariant.Lime,
                            onClick = { onEvent(MovitHomeEvent.BodyScanClicked) },
                            modifier = Modifier.semantics { contentDescription = scanA11y },
                            trailing = {
                                MovitButton(
                                    text = movitText("home_start_scan"),
                                    onClick = { onEvent(MovitHomeEvent.BodyScanClicked) },
                                    variant = MovitButtonVariant.Dark,
                                    size = MovitButtonSize.Small,
                                )
                            },
                        )
                    }

                    if (state.showNoProgramEmpty) {
                        MovitEmptyState(
                            title = movitText("home_no_program"),
                            message = movitText("home_no_program_message"),
                            actionLabel = movitText("home_browse_programs"),
                            onActionClick = { onEvent(MovitHomeEvent.BrowseProgramsClicked) },
                        )
                    }

                    state.progress?.let { progress ->
                        HomeProgressSection(progress = progress)
                    }

                    if (state.journeyRows.isNotEmpty()) {
                        MovitSectionHeader(
                            title = movitText("home_your_journey"),
                            subtitle = movitText("home_progress"),
                            actionLabel = movitText("home_view_plan"),
                            onActionClick = { onEvent(MovitHomeEvent.ViewPlanClicked) },
                        )
                        MovitListGroup(
                            rows = state.journeyRows.map { row ->
                                {
                                    MovitListRow(
                                        title = row.title,
                                        subtitle = row.subtitle,
                                        icon = if (row.id == "timeline") {
                                            Icons.Default.EmojiEvents
                                        } else {
                                            Icons.Default.CalendarMonth
                                        },
                                        iconVariant = if (row.id == "timeline") {
                                            MovitIconBoxVariant.Lime
                                        } else {
                                            MovitIconBoxVariant.Primary
                                        },
                                        trailing = row.tag?.let {
                                            {
                                                MovitTag(text = it, variant = MovitTagVariant.Coral)
                                            }
                                        },
                                        showChevron = true,
                                        onClick = { onEvent(MovitHomeEvent.JourneyRowClicked(row.id)) },
                                    )
                                }
                            },
                        )
                    }

                    state.reportPreview?.let { preview ->
                        HomeReportPreview(
                            reportPreview = preview,
                            onOpenReports = { onEvent(MovitHomeEvent.ReportsClicked) },
                        )
                    }

                    if (state.recentActivities.isNotEmpty()) {
                        MovitSectionHeader(
                            title = movitText("home_recent_activity"),
                            subtitle = movitText("home_insights"),
                            actionLabel = movitText("home_all_reports"),
                            onActionClick = { onEvent(MovitHomeEvent.ReportsClicked) },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                            state.recentActivities.forEach { activity ->
                                MovitListRow(
                                    title = activity.title,
                                    subtitle = activity.subtitle,
                                    icon = Icons.Default.FitnessCenter,
                                    onClick = {
                                        onEvent(MovitHomeEvent.RecentActivityClicked(activity.id))
                                    },
                                )
                            }
                        }
                    }

                    HomeQuickActions(
                        actions = state.quickActions,
                        onActionClick = { onEvent(MovitHomeEvent.QuickActionClicked(it)) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun HomeActiveProgramSection(
    program: HomeActiveProgramUi,
    onViewProgram: () -> Unit,
) {
    val viewProgramA11y = movitText("home_a11y_view_program")
    MovitSectionHeader(
        title = movitText("home_active_program"),
        subtitle = movitText("home_program"),
    )
    MovitCard(variant = MovitCardVariant.Outlined) {
        Column(
            modifier = Modifier.padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Text(
                text = program.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.movitColors.textSecondary,
                fontWeight = FontWeight.W700,
            )
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = program.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
            if (program.showViewAction) {
                MovitButton(
                    text = program.actionLabel,
                    onClick = onViewProgram,
                    variant = MovitButtonVariant.Outlined,
                    size = MovitButtonSize.Small,
                    modifier = Modifier.semantics { contentDescription = viewProgramA11y },
                )
            }
        }
    }
}
