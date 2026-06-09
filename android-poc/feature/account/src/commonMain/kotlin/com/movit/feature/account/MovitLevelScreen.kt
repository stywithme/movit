package com.movit.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitAccentBlock
import com.movit.designsystem.components.MovitAccentVariant
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitLevelScreen(
    state: MovitLevelUiState,
    onEvent: (MovitLevelEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MovitSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            MovitFilterChip(
                label = movitText("level_tab_profile"),
                selected = state.selectedTab == LevelTab.LevelProfile,
                onClick = { onEvent(MovitLevelEvent.TabSelected(LevelTab.LevelProfile)) },
                modifier = Modifier.weight(1f),
            )
            MovitFilterChip(
                label = movitText("level_tab_plan"),
                selected = state.selectedTab == LevelTab.PlanOverview,
                onClick = { onEvent(MovitLevelEvent.TabSelected(LevelTab.PlanOverview)) },
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = movitText("level_loading"))
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
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
        inkStyle = false,
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
        profile.planPhases.forEach { phase ->
            PlanPhaseCard(phase = phase)
        }
    }
    MovitButton(
        text = movitText("level_browse_programs"),
        onClick = { onEvent(MovitLevelEvent.BrowseProgramsClicked) },
        variant = MovitButtonVariant.Outlined,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlanPhaseCard(phase: PlanPhaseUi) {
    val dotColor = when (phase.status) {
        PlanPhaseStatus.Done -> MaterialTheme.movitColors.success
        PlanPhaseStatus.Active -> MaterialTheme.colorScheme.primary
        PlanPhaseStatus.Upcoming -> MaterialTheme.movitColors.surface2
    }
    MovitCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(text = phase.title, fontWeight = FontWeight.W800)
        }
        Text(
            text = phase.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.movitColors.textSecondary,
            modifier = Modifier.padding(top = MovitSpacing.xs),
        )
        phase.progressPercent?.let { percent ->
            MovitProgressBar(
                progressPercent = percent,
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        }
        phase.highlight?.let { highlight ->
            Text(
                text = highlight,
                style = MaterialTheme.typography.bodySmall,
                color = if (phase.status == PlanPhaseStatus.Done) {
                    MaterialTheme.movitColors.success
                } else {
                    MaterialTheme.movitColors.textSecondary
                },
                modifier = Modifier.padding(top = MovitSpacing.sm),
            )
        }
    }
}
