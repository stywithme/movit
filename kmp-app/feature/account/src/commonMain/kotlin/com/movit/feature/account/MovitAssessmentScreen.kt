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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBackButton
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitMetricTile
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.movitColors
import com.movit.feature.account.assessment.AssessmentCameraHost
import com.movit.feature.account.assessment.MovitBodyMap
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
        AssessmentHeader(state = state, onEvent = onEvent)
        when (state.phase) {
            AssessmentPhase.PreScreening -> PreScreeningContent(state, onEvent)
            AssessmentPhase.BodyScan -> BodyScanContent(state, onEvent)
            AssessmentPhase.Results -> ResultsContent(state, onEvent)
        }
    }
}

@Composable
private fun AssessmentHeader(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
) {
    val title = when (state.phase) {
        AssessmentPhase.PreScreening -> movitText("assessment_health_check")
        AssessmentPhase.BodyScan -> if (state.isProgressionAssessment) {
            movitText("assessment_progression_scan")
        } else {
            movitText("assessment_body_scan")
        }
        AssessmentPhase.Results -> if (state.isProgressionAssessment) {
            movitText("assessment_progression_results_title")
        } else {
            movitText("assessment_results_title")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovitBackButton(
            onClick = { onEvent(MovitAssessmentEvent.BackClicked) },
            contentDescription = movitText("common_back"),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MovitSpacing.sm),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun PreScreeningContent(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
) {
    MovitInsightCard(
        title = movitText("assessment_parq_title"),
        message = movitText("assessment_parq_sub"),
        icon = Icons.Default.Warning,
        variant = MovitInsightVariant.Warning,
    )
    val parqProgressA11y = movitText(
        "assessment_parq_progress_a11y",
        state.parqAnswers.size,
        AssessmentDefaults.parqQuestions.size,
    )
    MovitProgressBar(
        progressPercent = state.parqProgressPercent,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = parqProgressA11y },
    )
    Text(
        text = movitText(
            "assessment_parq_progress",
            state.parqAnswers.size,
            AssessmentDefaults.parqQuestions.size,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.movitColors.textTertiary,
    )
    AssessmentDefaults.parqQuestions.forEachIndexed { index, key ->
        val answer = state.parqAnswers[index]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MovitSpacing.sm),
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
            if (index < AssessmentDefaults.parqQuestions.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = MovitSpacing.md),
                    color = MaterialTheme.movitColors.divider,
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
        text = if (state.isProgressionAssessment) {
            movitText("assessment_progression_scan_sub")
        } else {
            movitText("assessment_body_scan_sub")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.movitColors.textSecondary,
    )
    if (state.isGuidedScan) {
        MovitInsightCard(
            title = movitText("assessment_ios_guided_scan_title"),
            message = movitText("assessment_ios_guided_scan_message"),
            icon = Icons.Default.Scanner,
            variant = MovitInsightVariant.Warning,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(MovitRadius.xl))
            .background(MaterialTheme.movitColors.ink),
        contentAlignment = Alignment.Center,
    ) {
        AssessmentCameraHost(
            onPoseFrame = { onEvent(MovitAssessmentEvent.BodyScanFrameReceived(it)) },
            onCameraReady = { onEvent(MovitAssessmentEvent.BodyScanCameraReady) },
            onGuidedModeStarted = { onEvent(MovitAssessmentEvent.BodyScanGuidedModeStarted) },
            onError = { onEvent(MovitAssessmentEvent.BodyScanError(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val frameColor = MaterialTheme.movitColors.onInkVeil55
            Box(
                modifier = Modifier
                    .size(width = 180.dp, height = 280.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = frameColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                            ),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                        )
                    },
            )
            Text(
                text = movitText(
                    "assessment_scan_hint",
                    movitText(state.scanMovementKey),
                    state.scanMovementNumber,
                    state.scanMovementTotal,
                ),
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
                Icon(
                    imageVector = Icons.Default.Scanner,
                    contentDescription = movitText("assessment_camera_active"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                val cameraTitle = if (state.isPoseDetected) {
                    "assessment_camera_active"
                } else {
                    "assessment_camera_waiting"
                }
                val cameraSubtitle = if (state.isPoseDetected) {
                    "assessment_camera_active_sub"
                } else {
                    "assessment_camera_waiting_sub"
                }
                Text(text = movitText(cameraTitle), fontWeight = FontWeight.W700)
                Text(
                    text = movitText(cameraSubtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.scanProgressPercent / 100f },
                    modifier = Modifier.size(52.dp),
                )
                Text(
                    text = "${state.scanProgressPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                )
            }
        }
    }
    state.scanErrorMessage?.let { message ->
        MovitInsightCard(
            title = movitText("common_error_title"),
            message = movitText("assessment_scan_error", message),
            icon = Icons.Default.Warning,
            variant = MovitInsightVariant.Warning,
        )
    }
    MovitButton(
        text = movitText("assessment_view_results"),
        onClick = { onEvent(MovitAssessmentEvent.CompleteBodyScan) },
        enabled = state.isScanComplete && !state.isLoadingResults && !state.isResolvingTemplate,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultsContent(
    state: MovitAssessmentUiState,
    onEvent: (MovitAssessmentEvent) -> Unit,
) {
    if (state.isLoadingResults) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MovitSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val results = state.results
    if (!results.resultsSavedToServer) {
        MovitInsightCard(
            title = movitText("assessment_results_local_fallback_title"),
            message = movitText("assessment_results_local_fallback_message"),
            icon = Icons.Default.Warning,
            variant = MovitInsightVariant.Warning,
        )
    }
    MovitDashboardHero(
        eyebrow = movitText("assessment_body_score"),
        title = results.bodyScore.toString(),
        subtitle = results.levelLabel,
        progressPercent = results.bodyScore,
        inkStyle = false,
    )
    if (results.domains.isNotEmpty()) {
        Text(
            text = movitText("assessment_domain_scores"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            results.domains.forEach { domain ->
                DomainMetricTile(domain = domain)
            }
        }
    }
    if (results.regions.isNotEmpty()) {
        Text(
            text = movitText("assessment_body_map_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
        )
        MovitCard {
            MovitBodyMap(regions = results.regions)
            FlowRow(
                modifier = Modifier.padding(top = MovitSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            ) {
                results.regions.forEach { region ->
                    RegionTile(region = region, modifier = Modifier.fillMaxWidth(0.48f))
                }
            }
        }
    }
    if (results.safetyGates.isNotEmpty()) {
        Text(
            text = movitText("assessment_safety_gates_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
        )
        results.safetyGates.forEach { gate ->
            SafetyGateCard(gate = gate)
        }
    }
    Text(
        text = movitText("assessment_insights"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W800,
    )
    results.insights.forEach { insight ->
        MovitCard {
            Text(
                text = movitText(insight.titleKey, *insight.titleArgs.toTypedArray()),
                fontWeight = FontWeight.W700,
            )
            Text(
                text = movitText(insight.messageKey, *insight.messageArgs.toTypedArray()),
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
private fun DomainMetricTile(domain: AssessmentDomainUi, modifier: Modifier = Modifier) {
    val domainLabel = movitText("assessment_domain_${domain.domainKey}")
    val domainA11y = movitText("assessment_domain_score_a11y", domainLabel, domain.score)
    MovitMetricTile(
        label = domainLabel,
        value = domain.score.toString(),
        modifier = modifier
            .fillMaxWidth(0.48f)
            .semantics { contentDescription = domainA11y },
    )
}

@Composable
private fun SafetyGateCard(gate: AssessmentSafetyGateUi) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MovitRadius.lg),
        color = movit.warningTint,
        border = BorderStroke(1.dp, movit.warning),
    ) {
        Column(modifier = Modifier.padding(MovitSpacing.md)) {
            Text(
                text = movitText(gate.reasonKey, *gate.reasonArgs.toTypedArray()),
                fontWeight = FontWeight.W700,
                color = movit.warning,
            )
            if (gate.blockedExerciseTypes.isNotEmpty()) {
                Text(
                    text = movitText(
                        "assessment_safety_gate_blocked",
                        gate.blockedExerciseTypes.take(3).joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.textSecondary,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
            }
            if (gate.allowedAlternatives.isNotEmpty()) {
                Text(
                    text = movitText(
                        "assessment_safety_gate_alternatives",
                        gate.allowedAlternatives.take(3).joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.success,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun RegionTile(region: AssessmentRegionUi, modifier: Modifier = Modifier) {
    val movit = MaterialTheme.movitColors
    val regionLabel = movitText("assessment_region_${region.regionKey}")
    val regionA11y = movitText("assessment_region_score_a11y", regionLabel, region.score)
    val (background, border) = when (region.tone) {
        AssessmentRegionTone.Good -> movit.successTint to movit.success
        AssessmentRegionTone.Warning -> movit.warningTint to movit.warning
        AssessmentRegionTone.Neutral -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = modifier.semantics { contentDescription = regionA11y },
        shape = RoundedCornerShape(MovitRadius.lg),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        MovitMetricTile(
            label = regionLabel,
            value = region.score.toString(),
            modifier = Modifier.padding(MovitSpacing.md),
        )
    }
}
