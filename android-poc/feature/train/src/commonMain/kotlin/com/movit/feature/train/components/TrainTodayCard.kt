package com.movit.feature.train.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBanner
import com.movit.designsystem.components.MovitBannerVariant
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitIconBox
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitMetricItem
import com.movit.designsystem.components.MovitMetricRow
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitSessionCard
import com.movit.designsystem.components.MovitSessionItem
import com.movit.designsystem.components.MovitStepper
import com.movit.designsystem.movitColors
import com.movit.feature.train.TrainDashboardStatus
import com.movit.feature.train.TrainDashboardUi
import com.movit.feature.train.TrainTodayWorkoutUi
import com.movit.feature.train.TrainWorkoutItemUi
import com.movit.feature.train.TrainWorkoutSessionUi
import com.movit.resources.movitText

@Composable
fun TrainTodayCard(
    dashboard: TrainDashboardUi,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    onViewJourney: (() -> Unit)? = null,
    onWhatsNext: (() -> Unit)? = null,
) {
    val today = dashboard.today ?: noPlanWorkout()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        MovitSectionHeader(
            title = sectionTitle(dashboard.status, today),
            subtitle = sectionSubtitle(dashboard.status),
        )

        when {
            dashboard.status == TrainDashboardStatus.RestDay -> {
                RestDayCard(today = today, onPrimaryAction = onPrimaryAction)
            }
            dashboard.status == TrainDashboardStatus.ProgramComplete -> {
                ProgramCompleteCard(
                    today = today,
                    onViewJourney = onViewJourney ?: onPrimaryAction,
                    onWhatsNext = onWhatsNext ?: onPrimaryAction,
                )
            }
            today.sessions.isEmpty() -> {
                TodayStateCard(
                    today = today,
                    onPrimaryAction = onPrimaryAction,
                )
            }
            else -> {
                SessionList(
                    sessions = today.sessions,
                    status = dashboard.status,
                    onPrimaryAction = onPrimaryAction,
                )
                if (dashboard.status == TrainDashboardStatus.CompletedToday) {
                    MovitBanner(
                        title = movitText("train_day_complete"),
                        message = movitText("train_day_complete_summary", today.subtitle, today.durationLabel),
                        variant = MovitBannerVariant.Success,
                    )
                    MovitButton(
                        text = movitText("train_view_day_summary"),
                        onClick = onPrimaryAction,
                        variant = MovitButtonVariant.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<TrainWorkoutSessionUi>,
    status: TrainDashboardStatus,
    onPrimaryAction: () -> Unit,
) {
    val defaultExpanded = sessions.indexOfFirst { !it.isCompleted }.takeIf { it >= 0 } ?: 0
    var expandedIndex by remember(sessions) { mutableIntStateOf(defaultExpanded) }
    val setsState = remember { mutableStateMapOf<String, Int>() }

    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        sessions.forEachIndexed { index, session ->
            val sessionKey = session.title
            MovitSessionCard(
                title = session.title,
                subtitle = "${session.subtitle} · ${session.durationLabel}",
                items = session.items.map { it.toSessionItem() },
                expanded = expandedIndex == index,
                onToggle = {
                    expandedIndex = if (expandedIndex == index) -1 else index
                },
                isCompleted = session.isCompleted,
                thumbnailUrl = session.thumbnailUrl,
                thumbnailLabel = session.title.take(1).uppercase(),
                actionLabel = session.actionLabel,
                onActionClick = if (!session.isCompleted) onPrimaryAction else null,
                footerNote = if (session.isCompleted) {
                    movitText("train_form_footer_steady", 88)
                } else {
                    null
                },
                itemTrailing = { itemIndex, item ->
                    if (!item.isRest && status == TrainDashboardStatus.ActivePlan) {
                        val key = "$sessionKey-${item.title}"
                        val sets = setsState.getOrPut(key) { parseSets(item.subtitle) }
                        MovitStepper(
                            value = sets,
                            onDecrement = { setsState[key] = (sets - 1).coerceAtLeast(1) },
                            onIncrement = { setsState[key] = sets + 1 },
                            minValue = 1,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun RestDayCard(
    today: TrainTodayWorkoutUi,
    onPrimaryAction: () -> Unit,
) {
    MovitCard(variant = MovitCardVariant.Elevated) {
        MovitIconBox(
            icon = Icons.Default.Bedtime,
            variant = MovitIconBoxVariant.Lime,
        )
        androidx.compose.material3.Text(
            text = today.title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.W800,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        androidx.compose.material3.Text(
            text = today.subtitle,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.movitColors.textSecondary,
        )
        androidx.compose.material3.Text(
            text = movitText("train_tomorrow_preview", movitText("train_focus_program"), 4),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.W800,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitButton(
            text = today.primaryActionLabel,
            onClick = onPrimaryAction,
            variant = MovitButtonVariant.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
        )
    }
}

@Composable
private fun ProgramCompleteCard(
    today: TrainTodayWorkoutUi,
    onViewJourney: () -> Unit,
    onWhatsNext: () -> Unit,
) {
    MovitCard(variant = MovitCardVariant.Elevated) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.movitColors.limeTint,
                border = BorderStroke(3.dp, MaterialTheme.movitColors.success),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    MovitIconBox(
                        icon = Icons.Default.EmojiEvents,
                        variant = MovitIconBoxVariant.Lime,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Text(
                text = movitText("train_program_complete_banner"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.movitColors.success,
                textAlign = TextAlign.Center,
            )
            Text(
                text = movitText(
                    "train_program_complete_summary",
                    today.subtitle,
                    today.durationLabel,
                    today.exerciseCountLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.movitColors.textSecondary,
                textAlign = TextAlign.Center,
            )
            MovitButton(
                text = movitText("train_view_journey"),
                onClick = onViewJourney,
                variant = MovitButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            MovitButton(
                text = movitText("train_whats_next"),
                onClick = onWhatsNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TodayStateCard(
    today: TrainTodayWorkoutUi,
    onPrimaryAction: () -> Unit,
) {
    MovitCard(modifier = Modifier.fillMaxWidth(), variant = MovitCardVariant.Elevated) {
        MovitIconBox(
            icon = Icons.Default.FitnessCenter,
            variant = MovitIconBoxVariant.Lime,
        )
        androidx.compose.material3.Text(
            text = today.title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.W800,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        androidx.compose.material3.Text(
            text = today.subtitle,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.movitColors.textSecondary,
        )
        MovitMetricRow(
            items = listOf(
                MovitMetricItem(today.durationLabel, movitText("train_metric_time")),
                MovitMetricItem(today.exerciseCountLabel, movitText("train_metric_work")),
                MovitMetricItem(today.focusLabel, movitText("train_metric_focus")),
            ),
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitButton(
            text = today.primaryActionLabel,
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
            leadingIcon = Icons.Default.PlayArrow,
        )
    }
}

private fun TrainWorkoutItemUi.toSessionItem(): MovitSessionItem = MovitSessionItem(
    typeLabel = typeLabel,
    title = title,
    subtitle = subtitle,
    isRest = isRest,
)

private fun parseSets(subtitle: String): Int {
    val match = Regex("(\\d+)\\s*sets", RegexOption.IGNORE_CASE).find(subtitle)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 3
}

@Composable
private fun sectionTitle(status: TrainDashboardStatus, today: TrainTodayWorkoutUi): String = when (status) {
    TrainDashboardStatus.ActivePlan -> movitText("train_section_today_named", today.subtitle)
    TrainDashboardStatus.NoPlan -> movitText("train_start_training")
    TrainDashboardStatus.RestDay -> movitText("train_today_label")
    TrainDashboardStatus.CompletedToday -> movitText("train_section_today_named", today.subtitle)
    TrainDashboardStatus.ProgramComplete -> movitText("train_next_step")
}

@Composable
private fun sectionSubtitle(status: TrainDashboardStatus): String = when (status) {
    TrainDashboardStatus.ActivePlan -> movitText("train_section_review_session")
    TrainDashboardStatus.NoPlan -> movitText("train_section_pick_program")
    TrainDashboardStatus.RestDay -> movitText("train_section_optional_light")
    TrainDashboardStatus.CompletedToday -> movitText("train_section_sessions_complete")
    TrainDashboardStatus.ProgramComplete -> movitText("train_section_journey_review")
}

@Composable
private fun noPlanWorkout(): TrainTodayWorkoutUi = TrainTodayWorkoutUi(
    title = movitText("train_no_active_plan"),
    subtitle = movitText("train_no_plan_rhythm"),
    durationLabel = movitText("train_flexible"),
    exerciseCountLabel = movitText("train_guided_plan_label"),
    focusLabel = movitText("train_choose_goal"),
    primaryActionLabel = movitText("train_explore_programs"),
)
