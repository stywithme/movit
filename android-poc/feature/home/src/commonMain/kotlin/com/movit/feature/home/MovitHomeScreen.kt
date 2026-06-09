package com.movit.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitAccentBlock
import com.movit.designsystem.components.MovitAccentVariant
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitListGroup
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitHomeScreen(
    state: MovitHomeUiState,
    onEvent: (MovitHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitScaffold(
        modifier = modifier,
        title = null,
        userName = state.userName,
        onProfileClick = { onEvent(MovitHomeEvent.ProfileClicked) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            when {
                state.isLoading && state.metricTiles.isEmpty() && state.todayPlan == null -> {
                    MovitLoadingState(message = movitText("home_loading"))
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitHomeEvent.RetryClicked) },
                    )
                }
                else -> {
                    MovitDashboardHero(
                        eyebrow = state.greetingEyebrow,
                        title = state.greetingTitle,
                        subtitle = state.greetingSubtitle,
                        progressPercent = 0,
                        inkStyle = true,
                    )

                    if (state.metricTiles.isNotEmpty()) {
                        MovitStatTileRow(
                            stats = state.metricTiles.map {
                                MovitStatTileData(value = it.value, label = it.label)
                            },
                        )
                    }

                    state.levelCard?.let { level ->
                        MovitDashboardHero(
                            eyebrow = level.eyebrow,
                            title = level.title,
                            subtitle = level.subtitle,
                            progressPercent = level.progressPercent,
                            inkStyle = false,
                            onActionClick = { onEvent(MovitHomeEvent.ProfileClicked) },
                            actionLabel = movitText("home_view_level"),
                        )
                    }

                    state.alert?.let { alert ->
                        MovitInsightCard(
                            title = alert.title,
                            message = alert.message,
                            icon = Icons.Default.Warning,
                            variant = MovitInsightVariant.Warning,
                        )
                    } ?: state.insightMessage?.let { message ->
                        MovitInsightCard(
                            title = movitText("home_plan_adjusted"),
                            message = message,
                            icon = Icons.Default.Warning,
                            variant = MovitInsightVariant.Warning,
                        )
                    }

                    state.activeProgram?.let { program ->
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
                                MovitButton(
                                    text = program.actionLabel,
                                    onClick = { onEvent(MovitHomeEvent.ViewProgramClicked) },
                                    variant = MovitButtonVariant.Outlined,
                                    size = MovitButtonSize.Small,
                                )
                            }
                        }
                    }

                    state.todayPlan?.let { plan ->
                        MovitSectionHeader(
                            title = movitText("home_todays_plan"),
                            subtitle = movitText("home_today"),
                        )
                        MovitCard(variant = MovitCardVariant.Outlined) {
                            Column(
                                modifier = Modifier.padding(MovitSpacing.lg),
                                verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                            ) {
                                Text(
                                    text = plan.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.movitColors.textSecondary,
                                    fontWeight = FontWeight.W700,
                                )
                                Text(
                                    text = plan.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.W800,
                                )
                                Text(
                                    text = plan.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.movitColors.textSecondary,
                                )
                                MovitButton(
                                    text = plan.primaryActionLabel,
                                    onClick = { onEvent(MovitHomeEvent.StartTodayPlanClicked) },
                                    variant = MovitButtonVariant.Filled,
                                    leadingIcon = Icons.Default.PlayArrow,
                                )
                                Text(
                                    text = plan.statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.movitColors.textSecondary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    if (state.showBodyScanCta) {
                        MovitAccentBlock(
                            title = movitText("home_body_scan"),
                            subtitle = movitText("home_body_scan_subtitle"),
                            variant = MovitAccentVariant.Lime,
                            onClick = { onEvent(MovitHomeEvent.BodyScanClicked) },
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

                    if (state.journeyRows.isNotEmpty()) {
                        MovitSectionHeader(
                            title = movitText("home_your_journey"),
                            subtitle = movitText("home_progress"),
                            actionLabel = movitText("home_view_plan"),
                            onActionClick = { onEvent(MovitHomeEvent.ViewProgramClicked) },
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
                                        trailing = row.tag?.let {
                                            {
                                                MovitTag(text = it, variant = MovitTagVariant.Coral)
                                            }
                                        },
                                        showChevron = true,
                                        onClick = { onEvent(MovitHomeEvent.ViewProgramClicked) },
                                    )
                                }
                            },
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
                                    onClick = { onEvent(MovitHomeEvent.ReportsClicked) },
                                )
                            }
                        }
                    }

                    if (state.quickActions.isNotEmpty()) {
                        MovitSectionHeader(title = movitText("home_quick_actions"))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        ) {
                            state.quickActions.take(2).forEach { action ->
                                MovitCard(
                                    modifier = Modifier.weight(1f),
                                    variant = MovitCardVariant.Outlined,
                                    onClick = { onEvent(MovitHomeEvent.QuickActionClicked(action.id)) },
                                ) {
                                    Column(
                                        modifier = Modifier.padding(MovitSpacing.md),
                                        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                                    ) {
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.W700,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
