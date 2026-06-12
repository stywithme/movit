package com.movit.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBarChart
import com.movit.designsystem.components.MovitBarChartItem
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitFloatPill
import com.movit.designsystem.components.MovitFloatPillVariant
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun ReportDetailScreen(
    state: ReportDetailUiState,
    onBack: () -> Unit,
    onPageSelected: (ReportDetailPage) -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            when {
                state.isLoading -> MovitLoadingState(message = movitText("report_detail_loading"))
                state.errorMessage != null -> MovitErrorState(
                    title = movitText("common_error_title"),
                    message = state.errorMessage,
                    actionLabel = movitText("common_retry"),
                    onRetry = onRetry,
                )
                report != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(top = 56.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = MovitSpacing.lg),
                    ) {
                        ReportPageDots(
                            pageCount = ReportDetailPage.entries.size,
                            selectedIndex = state.selectedPage.ordinal,
                            onPageSelected = { index ->
                                onPageSelected(ReportDetailPage.entries[index])
                            },
                        )
                        when (state.selectedPage) {
                            ReportDetailPage.Overview -> ReportOverviewPage(report)
                            ReportDetailPage.Form -> ReportFormPage(report)
                            ReportDetailPage.Fatigue -> ReportFatiguePage(report)
                            ReportDetailPage.Tips -> ReportTipsPage(report, onExport = onExport)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitFloatPill(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                variant = MovitFloatPillVariant.Outline,
                contentDescription = movitText("report_detail_back"),
            )
            MovitFloatPill(
                onClick = onShare,
                icon = Icons.Default.Share,
                variant = MovitFloatPillVariant.Outline,
                contentDescription = movitText("report_detail_share"),
            )
        }
    }
}

@Composable
private fun ReportPageDots(
    pageCount: Int,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val movit = MaterialTheme.movitColors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MovitSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(8.dp)
                        .width(if (selected) 24.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else movit.stroke,
                        )
                        .clickable { onPageSelected(index) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MovitSpacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ReportDetailPage.entries.forEachIndexed { index, page ->
                val label = pageLabel(page)
                val selected = index == selectedIndex
                val tabDescription = movitText(
                    "report_detail_page_a11y",
                    label,
                    index + 1,
                    pageCount,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.W800 else FontWeight.W500,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        movit.textTertiary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                            contentDescription = tabDescription
                        }
                        .clickable { onPageSelected(index) }
                        .padding(horizontal = MovitSpacing.xs, vertical = MovitSpacing.xs),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun pageLabel(page: ReportDetailPage): String = when (page) {
    ReportDetailPage.Overview -> movitText("report_detail_page_overview")
    ReportDetailPage.Form -> movitText("report_detail_page_form")
    ReportDetailPage.Fatigue -> movitText("report_detail_page_fatigue")
    ReportDetailPage.Tips -> movitText("report_detail_page_tips")
}

@Composable
private fun ReportOverviewPage(report: ReportDetailUi) {
    val movit = MaterialTheme.movitColors
    val scoreDescription = movitText("report_detail_form_score_a11y", report.formScore)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = scoreDescription
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = movitText("report_detail_form_score"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W700,
                color = movit.textSecondary,
            )
            Text(
                text = report.formScore.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                fontWeight = FontWeight.W800,
                color = movit.success,
            )
            report.badgeLabel?.let {
                MovitTag(text = it, variant = MovitTagVariant.Lime)
            }
        }
        MovitStatTileRow(
            stats = listOf(
                MovitStatTileData(report.sets, movitText("report_detail_sets")),
                MovitStatTileData(report.reps, movitText("report_detail_reps")),
                MovitStatTileData(report.durationLabel, movitText("report_detail_duration")),
            ),
        )
        MovitInsightCard(
            title = report.overviewInsightTitle,
            message = report.overviewInsightMessage,
            icon = Icons.Default.Check,
            variant = MovitInsightVariant.Success,
        )
    }
}

@Composable
private fun ReportFormPage(report: ReportDetailUi) {
    val movit = MaterialTheme.movitColors
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg)) {
        MovitSectionHeader(title = movitText("report_detail_joint_analysis"))
        if (report.joints.isEmpty()) {
            val jointsMessageKey = when (report.jointsEmptyReason) {
                ReportJointsEmptyReason.ApiPending -> "report_detail_joints_api_pending"
                ReportJointsEmptyReason.SessionUntracked -> "report_detail_joints_session_untracked"
                ReportJointsEmptyReason.Generic -> "report_detail_joints_unavailable"
            }
            MovitCard(variant = MovitCardVariant.Filled) {
                Text(
                    text = movitText(jointsMessageKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = movit.textSecondary,
                    modifier = Modifier.padding(MovitSpacing.md),
                )
            }
        } else {
            MovitCard(variant = MovitCardVariant.Filled) {
                Column(
                    modifier = Modifier.padding(MovitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
                ) {
                    report.joints.forEach { joint ->
                        JointScoreRow(joint = joint)
                    }
                }
            }
        }
        MovitSectionHeader(title = movitText("report_detail_best_vs_worst"))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            report.repCompare.forEach { compare ->
                RepCompareCard(compare = compare)
            }
        }
    }
}

@Composable
private fun RowScope.RepCompareCard(compare: ReportRepCompareUi) {
    val movit = MaterialTheme.movitColors
    val background = if (compare.isBest) movit.successTint else movit.coralTint
    val scoreColor = if (compare.isBest) movit.success else MaterialTheme.colorScheme.tertiary
    val compareDescription = movitText(
        "report_detail_rep_compare_a11y",
        compare.label,
        compare.score,
    )
    Surface(
        modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) {
                contentDescription = compareDescription
            },
        shape = RoundedCornerShape(MovitRadius.xl),
        color = background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MovitSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = compare.label,
                style = MaterialTheme.typography.labelSmall,
                color = movit.textSecondary,
            )
            Text(
                text = compare.score.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                fontWeight = FontWeight.W800,
                color = scoreColor,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}

@Composable
private fun JointScoreRow(joint: ReportJointScoreUi) {
    val movit = MaterialTheme.movitColors
    val jointDescription = movitText(
        "report_detail_joint_score_a11y",
        joint.label,
        joint.scorePercent,
    )
    val barColor = when (joint.tone) {
        ReportScoreTone.Success -> movit.success
        ReportScoreTone.Primary -> MaterialTheme.colorScheme.primary
        ReportScoreTone.Warning -> movit.warning
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = jointDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Text(
            text = joint.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
            modifier = Modifier.width(70.dp),
        )
        MovitProgressBar(
            progressPercent = joint.scorePercent,
            modifier = Modifier.weight(1f),
            trackColor = movit.surface2,
            progressColor = barColor,
        )
        Text(
            text = joint.scorePercent.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W800,
        )
    }
}

@Composable
private fun ReportFatiguePage(report: ReportDetailUi) {
    val movit = MaterialTheme.movitColors
    val fatigueDescription = movitText(
        "report_detail_fatigue_a11y",
        report.fatigueTitle,
        report.fatigueProgressPercent,
    )
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg)) {
        MovitSectionHeader(title = movitText("report_detail_control_fatigue"))
        MovitCard(variant = MovitCardVariant.Filled) {
            Column(
                modifier = Modifier
                    .padding(MovitSpacing.lg)
                    .semantics(mergeDescendants = true) {
                        contentDescription = fatigueDescription
                    },
            ) {
                Text(
                    text = report.fatigueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W700,
                    color = movit.textSecondary,
                )
                Text(
                    text = report.fatigueTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.W800,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
                MovitProgressBar(
                    progressPercent = report.fatigueProgressPercent,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                    progressColor = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = report.fatigueMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.textSecondary,
                    modifier = Modifier.padding(top = MovitSpacing.sm),
                )
            }
        }
        MovitCard(variant = MovitCardVariant.Filled) {
            Column(modifier = Modifier.padding(MovitSpacing.md)) {
                Text(
                    text = movitText("report_detail_form_by_set"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                val chartItems = report.formBySetValues.mapIndexed { index, value ->
                    val label = report.formBySetLabels.getOrElse(index) {
                        movitText("report_detail_set_short", index + 1)
                    }
                    MovitBarChartItem(
                        value = value,
                        label = label,
                        highlighted = value == report.formBySetValues.maxOrNull(),
                    )
                }
                val chartDescription = buildString {
                    append(movitText("report_detail_form_by_set_a11y"))
                    append(": ")
                    append(
                        chartItems.joinToString(", ") { item ->
                            "${item.label} ${item.value.roundToInt()}"
                        },
                    )
                }
                MovitBarChart(
                    items = chartItems,
                    modifier = Modifier
                        .padding(top = MovitSpacing.md)
                        .semantics { contentDescription = chartDescription },
                    highlightColor = movit.success,
                )
            }
        }
    }
}

@Composable
private fun ReportTipsPage(
    report: ReportDetailUi,
    onExport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
        MovitSectionHeader(title = movitText("report_detail_coaching_tips"))
        report.tips.forEach { tip ->
            MovitCard(variant = MovitCardVariant.Outlined) {
                Column(modifier = Modifier.padding(MovitSpacing.lg)) {
                    Text(
                        text = tip.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.W800,
                    )
                    Text(
                        text = tip.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.movitColors.textSecondary,
                        modifier = Modifier.padding(top = MovitSpacing.xs),
                    )
                }
            }
        }
        MovitButton(
            text = movitText("report_detail_export"),
            onClick = onExport,
            variant = MovitButtonVariant.Outlined,
            leadingIcon = Icons.Default.Share,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
        )
    }
}
