package com.movit.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitAccentBlock
import com.movit.designsystem.components.MovitAccentVariant
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitDashboardHeroVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitFloatPill
import com.movit.designsystem.components.MovitFloatPillVariant
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitListCard
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitLevelScreen(
    state: MovitLevelUiState,
    onEvent: (MovitLevelEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MovitSpacing.lg)
                .padding(bottom = MovitSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = movitText("level_loading"))
                state.errorMessage != null -> {
                    MovitErrorState(
                        title = movitText("common_error_title"),
                        message = state.errorMessage,
                        actionLabel = movitText("common_retry"),
                        onRetry = { onEvent(MovitLevelEvent.RetryClicked) },
                    )
                }
                state.profile != null -> {
                    when (state.selectedTab) {
                        LevelTab.LevelProfile -> LevelProfileContent(state.profile, onEvent)
                        LevelTab.PlanOverview -> PlanOverviewContent(state.profile, onEvent)
                    }
                }
            }
        }

        LevelFloatingHeader(
            selectedTab = state.selectedTab,
            onBack = onBack,
            onTabSelected = { onEvent(MovitLevelEvent.TabSelected(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
        )
    }
}

@Composable
private fun LevelFloatingHeader(
    selectedTab: LevelTab,
    onBack: () -> Unit,
    onTabSelected: (LevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitFloatPill(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            variant = MovitFloatPillVariant.Ink,
            contentDescription = movitText("profile_back"),
        )
        MovitFloatPill(
            onClick = { onTabSelected(LevelTab.LevelProfile) },
            label = movitText("level_tab_profile"),
            variant = if (selectedTab == LevelTab.LevelProfile) {
                MovitFloatPillVariant.Action
            } else {
                MovitFloatPillVariant.Ink
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        MovitFloatPill(
            onClick = { onTabSelected(LevelTab.PlanOverview) },
            label = movitText("level_tab_plan"),
            variant = if (selectedTab == LevelTab.PlanOverview) {
                MovitFloatPillVariant.Action
            } else {
                MovitFloatPillVariant.Ink
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun LevelProfileContent(
    profile: LevelProfileUi,
    onEvent: (MovitLevelEvent) -> Unit,
) {
    MovitDashboardHero(
        eyebrow = movitText("level_current"),
        title = movitText("level_named", profile.levelNumber, profile.levelName),
        subtitle = movitText(
            "level_progress_sub",
            profile.pointsToNext,
            profile.levelNumber + 1,
            profile.reassessmentLabel,
        ),
        progressPercent = profile.progressToNext,
        variant = MovitDashboardHeroVariant.Lime,
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LevelRing(score = profile.bodyScore)
    }
    Text(
        text = movitText("level_domain_breakdown"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W800,
    )
    MovitCard {
        profile.domains.forEach { domain ->
            DomainRow(domain = domain)
        }
    }
    MovitAccentBlock(
        title = movitText("level_retake_scan"),
        subtitle = movitText("level_retake_scan_sub"),
        variant = MovitAccentVariant.Lime,
        onClick = { onEvent(MovitLevelEvent.StartScanClicked) },
        trailing = {
            MovitButton(
                text = movitText("level_start_scan"),
                onClick = { onEvent(MovitLevelEvent.StartScanClicked) },
                variant = MovitButtonVariant.Dark,
                size = MovitButtonSize.Small,
            )
        },
    )
    MovitListCard {
        MovitListRow(
            title = movitText("level_recommended_programs"),
            subtitle = movitText("level_recommended_programs_sub", profile.levelNumber),
            icon = Icons.Default.EmojiEvents,
            iconVariant = MovitIconBoxVariant.Lime,
            onClick = { onEvent(MovitLevelEvent.BrowseProgramsClicked) },
            showChevron = true,
        )
    }
}

@Composable
private fun LevelRing(score: Int) {
    val ringDescription = movitText("level_body_score_ring_a11y", score)
    Box(
        modifier = Modifier
            .size(140.dp)
            .semantics { contentDescription = ringDescription },
        contentAlignment = Alignment.Center,
    ) {
        val track = MaterialTheme.movitColors.surface2
        val progress = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = progress,
                startAngle = -90f,
                sweepAngle = 360f * (score / 100f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = movitText("level_body_score"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.movitColors.textSecondary,
            )
        }
    }
}

@Composable
private fun DomainRow(domain: LevelDomainUi) {
    val rowDescription = movitText("level_domain_score_a11y", domain.name, domain.score)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MovitSpacing.md)
            .semantics { contentDescription = rowDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Text(
            text = domain.name,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
        )
        MovitProgressBar(
            progressPercent = domain.score,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = domain.score.toString(),
            fontWeight = FontWeight.W700,
        )
    }
}

@Composable
private fun PlanOverviewContent(
    profile: LevelProfileUi,
    onEvent: (MovitLevelEvent) -> Unit,
) {
    Text(
        text = movitText("level_plan_title"),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.W800,
    )
    Text(
        text = movitText("level_plan_sub"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.movitColors.textSecondary,
    )
    if (profile.planPhases.isEmpty()) {
        MovitCard {
            Text(
                text = movitText("level_plan_empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.movitColors.textSecondary,
            )
        }
    } else {
        PlanTimeline(phases = profile.planPhases)
    }
    MovitButton(
        text = movitText("level_browse_programs"),
        onClick = { onEvent(MovitLevelEvent.BrowseProgramsClicked) },
        variant = MovitButtonVariant.Outlined,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanTimeline(phases: List<PlanPhaseUi>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        phases.forEachIndexed { index, phase ->
            PlanTimelineItem(
                phase = phase,
                showConnector = index < phases.lastIndex,
            )
        }
    }
}

@Composable
private fun PlanTimelineItem(
    phase: PlanPhaseUi,
    showConnector: Boolean,
) {
    val movit = MaterialTheme.movitColors
    val dividerColor = movit.divider

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp)
            .drawBehind {
                if (showConnector) {
                    val x = (-16).dp.toPx()
                    drawLine(
                        color = dividerColor,
                        start = Offset(x, 20.dp.toPx()),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            },
    ) {
        TimelineDot(
            status = phase.status,
            modifier = Modifier
                .offset(x = (-22).dp)
                .padding(top = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = phase.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = phase.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = movit.textTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
            PlanPhaseStateCard(phase = phase)
        }
    }
}

@Composable
private fun TimelineDot(
    status: PlanPhaseStatus,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    val dotSize = 14.dp
    val surfaceColor = MaterialTheme.colorScheme.surface

    when (status) {
        PlanPhaseStatus.Active -> {
            Box(
                modifier = modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(movit.primaryTint),
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, surfaceColor, CircleShape),
                )
            }
        }
        PlanPhaseStatus.Done -> {
            Box(
                modifier = modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(movit.success),
            )
        }
        PlanPhaseStatus.Upcoming -> {
            val borderColor = movit.stroke
            Box(
                modifier = modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(movit.surface2)
                    .border(3.dp, surfaceColor, CircleShape)
                    .border(1.dp, borderColor, CircleShape),
            )
        }
    }
}

@Composable
private fun PlanPhaseStateCard(phase: PlanPhaseUi) {
    val movit = MaterialTheme.movitColors
    val hasDoneHighlight = phase.status == PlanPhaseStatus.Done && phase.highlight != null
    val hasActiveProgress = phase.status == PlanPhaseStatus.Active &&
        (phase.progressPercent != null || phase.highlight != null)

    if (!hasDoneHighlight && !hasActiveProgress) return

    Surface(
        shape = RoundedCornerShape(MovitRadius.md),
        color = movit.surface2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MovitSpacing.sm),
    ) {
        Column(modifier = Modifier.padding(MovitSpacing.md)) {
            when (phase.status) {
                PlanPhaseStatus.Done -> {
                    Text(
                        text = movitText("level_body_score_completion"),
                        style = MaterialTheme.typography.labelSmall,
                        color = movit.textSecondary,
                    )
                    Text(
                        text = phase.highlight.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W800,
                        color = movit.success,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                PlanPhaseStatus.Active -> {
                    phase.progressPercent?.let { percent ->
                        MovitProgressBar(progressPercent = percent)
                    }
                    phase.highlight?.let { highlight ->
                        Text(
                            text = highlight,
                            style = MaterialTheme.typography.bodySmall,
                            color = movit.textSecondary,
                            modifier = Modifier.padding(
                                top = if (phase.progressPercent != null) MovitSpacing.sm else 0.dp,
                            ),
                        )
                    }
                }
                PlanPhaseStatus.Upcoming -> Unit
            }
        }
    }
}
