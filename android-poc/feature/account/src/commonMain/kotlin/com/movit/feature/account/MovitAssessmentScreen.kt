package com.movit.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitMetricTile
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovitAssessmentScreen(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MovitSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
    ) {
        when (state.phase) {
            AssessmentPhase.PreScreening -> PreScreeningContent(state, onEvent)
            AssessmentPhase.BodyScan -> BodyScanContent(state, onEvent)
            AssessmentPhase.Results -> ResultsContent(state, onEvent)
        }
    }
}

@Composable
private fun PreScreeningContent(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
) {
    Text(
        text = movitText("assessment_health_check"),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.W800,
    )
    MovitInsightCard(
        title = movitText("assessment_parq_title"),
        message = movitText("assessment_parq_sub"),
        icon = Icons.Default.Warning,
        variant = MovitInsightVariant.Warning,
    )
    FakeAssessmentPreviewData.parqQuestions.forEachIndexed { index, key ->
        val answer = state.parqAnswers[index]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MovitSpacing.md),
        ) {
            Text(
                text = movitText(key),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
            )
            Row(
                modifier = Modifier.padding(top = MovitSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                MovitFilterChip(
                    label = movitText("assessment_no"),
                    selected = answer == false,
                    onClick = { onEvent(MovitAssessmentEvent.ParqAnswered(index, false)) },
                    modifier = Modifier.weight(1f),
                )
                MovitFilterChip(
                    label = movitText("assessment_yes"),
                    selected = answer == true,
                    onClick = { onEvent(MovitAssessmentEvent.ParqAnswered(index, true)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    MovitButton(
        text = movitText("assessment_continue_scan"),
        onClick = { onEvent(MovitAssessmentEvent.ContinueToBodyScan) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BodyScanContent(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
) {
    Text(
        text = movitText("assessment_body_scan"),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.W800,
    )
    Text(
        text = movitText("assessment_body_scan_sub"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.movitColors.textSecondary,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(MovitRadius.xl))
            .background(MaterialTheme.movitColors.ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 180.dp, height = 280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.movitColors.onInkVeil16),
            )
            Text(
                text = state.scanMovementLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.onInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MovitSpacing.lg, start = MovitSpacing.xl, end = MovitSpacing.xl),
            )
            Surface(
                modifier = Modifier.padding(top = MovitSpacing.md),
                shape = RoundedCornerShape(MovitRadius.full),
                color = MaterialTheme.movitColors.limeTint,
            ) {
                Text(
                    text = movitText("assessment_hold_position"),
                    modifier = Modifier.padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.xs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.movitColors.limeDeep,
                )
            }
        }
    }
    MovitCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(MovitRadius.md))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Scanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = movitText("assessment_camera_placeholder"), fontWeight = FontWeight.W700)
                Text(
                    text = movitText("assessment_camera_placeholder_sub"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            CircularProgressIndicator(
                progress = { state.scanProgressPercent / 100f },
                modifier = Modifier.size(52.dp),
            )
        }
    }
    MovitButton(
        text = movitText("assessment_view_results"),
        onClick = { onEvent(MovitAssessmentEvent.CompleteBodyScan) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultsContent(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
) {
    val results = state.results
    MovitDashboardHero(
        eyebrow = movitText("assessment_body_score"),
        title = results.bodyScore.toString(),
        subtitle = results.levelLabel,
        progressPercent = results.bodyScore,
        inkStyle = false,
    )
    Text(
        text = movitText("assessment_region_scores"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W800,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        results.regions.forEach { region ->
            RegionTile(region = region, modifier = Modifier.fillMaxWidth(0.48f))
        }
    }
    Text(
        text = movitText("assessment_insights"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W800,
    )
    results.insights.forEach { insight ->
        MovitCard {
            Text(text = insight.title, fontWeight = FontWeight.W700)
            Text(
                text = insight.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
        MovitButton(
            text = movitText("assessment_browse_programs"),
            onClick = { onEvent(MovitAssessmentEvent.BrowseProgramsClicked) },
            variant = MovitButtonVariant.Outlined,
            modifier = Modifier.weight(1f),
        )
        MovitButton(
            text = movitText("assessment_go_home"),
            onClick = { onEvent(MovitAssessmentEvent.GoHomeClicked) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RegionTile(region: AssessmentRegionUi, modifier: Modifier = Modifier) {
    val movit = MaterialTheme.movitColors
    val (background, border) = when (region.tone) {
        AssessmentRegionTone.Good -> movit.successTint to movit.success
        AssessmentRegionTone.Warning -> movit.warningTint to movit.warning
        AssessmentRegionTone.Neutral -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MovitRadius.lg),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        MovitMetricTile(
            label = movitText("assessment_region_${region.regionKey}"),
            value = region.score.toString(),
            modifier = Modifier.padding(MovitSpacing.md),
        )
    }
}
