package com.movit.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitAccentBlock
import com.movit.designsystem.components.MovitAccentVariant
import com.movit.designsystem.components.MovitActionDock
import com.movit.designsystem.components.MovitActivityRow
import com.movit.designsystem.components.MovitBanner
import com.movit.designsystem.components.MovitBannerVariant
import com.movit.designsystem.components.MovitBarChart
import com.movit.designsystem.components.MovitBarChartItem
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitFab
import com.movit.designsystem.components.MovitFloatPill
import com.movit.designsystem.components.MovitFloatPillVariant
import com.movit.designsystem.components.MovitHeroCard
import com.movit.designsystem.components.MovitIconBox
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitInnerPageHeader
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitKpiGrid
import com.movit.designsystem.components.MovitKpiItem
import com.movit.designsystem.components.MovitLineChart
import com.movit.designsystem.components.MovitListGroup
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitMetricItem
import com.movit.designsystem.components.MovitMetricRow
import com.movit.designsystem.components.MovitRingChart
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitSegmentedControl
import com.movit.designsystem.components.MovitSessionCard
import com.movit.designsystem.components.MovitSessionItem
import com.movit.designsystem.components.MovitSkeletonCard
import com.movit.designsystem.components.MovitStepper
import com.movit.designsystem.components.MovitStatBig
import com.movit.designsystem.components.MovitStatMini
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.components.MovitTimelineEntry
import com.movit.designsystem.components.MovitTimelineList
import com.movit.designsystem.components.MovitWeekDay
import com.movit.designsystem.components.MovitWeekDayState
import com.movit.designsystem.components.MovitWeekStrip
import com.movit.designsystem.components.MovitWeekStripLegend
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitCatalogIconBoxSection() {
    CatalogSection(title = "Icon boxes", subtitle = "Tinted icon containers") {
        Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitIconBox(Icons.Default.FitnessCenter, variant = MovitIconBoxVariant.Primary)
            MovitIconBox(Icons.Default.EmojiEvents, variant = MovitIconBoxVariant.Lime)
            MovitIconBox(Icons.Default.FlashOn, variant = MovitIconBoxVariant.Coral)
        }
    }
}

@Composable
fun MovitCatalogTagsSection() {
    CatalogSection(title = "Tags & badges") {
        Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitTag("Well done", variant = MovitTagVariant.Lime, icon = Icons.Default.Check)
            MovitTag("Tracking", variant = MovitTagVariant.Blue)
            MovitTag("Action", variant = MovitTagVariant.Coral)
            MovitTag("PRO", variant = MovitTagVariant.Gold, icon = Icons.Default.EmojiEvents)
        }
    }
}

@Composable
fun MovitCatalogSegmentedSection() {
    var selected by remember { mutableIntStateOf(0) }
    CatalogSection(title = "Segmented control") {
        MovitSegmentedControl(
            options = listOf("Day", "Week", "Month"),
            selectedIndex = selected,
            onOptionSelected = { selected = it },
        )
    }
}

@Composable
fun MovitCatalogStatCardsSection() {
    CatalogSection(title = "Stat cards") {
        Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitStatMini("5", "This week", modifier = Modifier.weight(1f))
            MovitStatMini("87%", "Form score", modifier = Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.secondary)
            MovitStatMini("12", "Streak", modifier = Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.tertiary)
        }
        Row(
            modifier = Modifier.padding(top = MovitSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            MovitStatBig(
                name = "Calories",
                description = "Daily burn",
                value = "1,290",
                unit = "kcal",
                icon = Icons.Default.FlashOn,
                iconVariant = MovitIconBoxVariant.Coral,
                delta = "5%",
                modifier = Modifier.weight(1f),
            )
            MovitStatBig(
                name = "Weight",
                description = "Healthy range",
                value = "82",
                unit = "kg",
                icon = Icons.Default.TrendingUp,
                delta = "1.2",
                deltaUp = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun MovitCatalogChartsSection() {
    CatalogSection(title = "Charts & rings") {
        MovitRingChart(percent = 72, title = "Daily goal", subtitle = "7.2k / 10k steps", label = "Goal")
        MovitLineChart(
            points = listOf(0.7f, 0.6f, 0.3f, 0.42f, 0.54f, 0.28f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
        )
        MovitBarChart(
            items = listOf(
                MovitBarChartItem(0.45f, "M"),
                MovitBarChartItem(0.62f, "T"),
                MovitBarChartItem(0.38f, "W"),
                MovitBarChartItem(0.92f, "T", highlighted = true),
                MovitBarChartItem(0.70f, "F"),
                MovitBarChartItem(0.55f, "S"),
                MovitBarChartItem(0.48f, "S"),
            ),
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
    }
}

@Composable
fun MovitCatalogHeroAccentSection() {
    CatalogSection(title = "Hero & accent blocks") {
        MovitHeroCard(
            eyebrow = "Home program",
            title = "Your Home Workout\nStarts Here",
            membersLabel = "5.8k+ members",
            ctaLabel = "Start",
            onCtaClick = {},
            showPlayFab = true,
        )
        MovitAccentBlock(
            title = "Body Scan",
            subtitle = "Assess your mobility, balance & symmetry",
            variant = MovitAccentVariant.Lime,
            glyphIcon = Icons.Default.CenterFocusStrong,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitAccentBlock(
            title = "Hydration",
            subtitle = "Drinking enough water boosts energy and focus",
            variant = MovitAccentVariant.Blue,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitAccentBlock(
            title = movitText("catalog_accent_coral_title"),
            subtitle = movitText("catalog_accent_coral_subtitle"),
            variant = MovitAccentVariant.Coral,
            glyphIcon = Icons.Default.EmojiEvents,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
    }
}

@Composable
fun MovitCatalogWeekSessionSection() {
    CatalogSection(title = "Training week & sessions") {
        MovitWeekStrip(
            title = "June · Week 2",
            days = listOf(
                MovitWeekDay("Mon", "1", MovitWeekDayState.Done),
                MovitWeekDay("Tue", "2", MovitWeekDayState.Done),
                MovitWeekDay("Wed", "3", MovitWeekDayState.Missed),
                MovitWeekDay("Thu", "4", MovitWeekDayState.Today),
                MovitWeekDay("Fri", "5", MovitWeekDayState.Planned),
                MovitWeekDay("Sat", "6", MovitWeekDayState.Rest),
                MovitWeekDay("Sun", "7", MovitWeekDayState.Planned),
            ),
            legend = MovitWeekStripLegend(
                done = movitText("ds_week_legend_done"),
                today = movitText("ds_week_legend_today"),
                missed = movitText("ds_week_legend_missed"),
                rest = movitText("ds_week_legend_rest"),
            ),
        )
        val sets = remember { mutableStateListOf(3, 0, 3) }
        MovitSessionCard(
            title = "Morning Workout",
            subtitle = "5 exercises · ~20 min",
            items = listOf(
                MovitSessionItem("EX", "Squat", "3 sets · 10 reps"),
                MovitSessionItem("REST", "Rest", "60 sec", isRest = true),
                MovitSessionItem("EX", "Lunge", "3 sets · 12 reps"),
            ),
            expanded = true,
            onToggle = {},
            isCompleted = true,
            completedLabel = movitText("session_done"),
            actionLabel = "Continue session",
            onActionClick = {},
            itemTrailing = { index, item ->
                if (!item.isRest) {
                    MovitStepper(
                        value = sets[index],
                        onDecrement = { sets[index] = (sets[index] - 1).coerceAtLeast(1) },
                        onIncrement = { sets[index] = sets[index] + 1 },
                        minValue = 1,
                    )
                }
            },
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
    }
}

@Composable
fun MovitCatalogFloatingControlsSection() {
    CatalogSection(
        title = "Floating controls",
        subtitle = "Inner-page headers, pills & FAB",
    ) {
        MovitCard(variant = MovitCardVariant.Flat) {
            Text(
                "Inner page · back + action",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.movitColors.textTertiary,
            )
            MovitInnerPageHeader(
                onBack = {},
                onAction = {},
                actionLabel = "Start",
                actionIcon = Icons.Default.PlayArrow,
                modifier = Modifier.padding(top = MovitSpacing.sm),
            )
        }
        MovitCard(variant = MovitCardVariant.Flat, modifier = Modifier.padding(top = MovitSpacing.md)) {
            Text(
                "Inner page · back + title + more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.movitColors.textTertiary,
            )
            MovitInnerPageHeader(
                onBack = {},
                title = "Exercise Detail",
                onAction = {},
                actionIcon = Icons.Default.MoreVert,
                actionVariant = MovitFloatPillVariant.Ink,
                modifier = Modifier.padding(top = MovitSpacing.sm),
            )
        }
        Row(
            modifier = Modifier.padding(top = MovitSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitFloatPill(onClick = {}, label = "Back", icon = Icons.AutoMirrored.Filled.ArrowBack)
            MovitFloatPill(onClick = {}, icon = Icons.Default.MoreVert, contentDescription = "More")
            MovitFloatPill(
                onClick = {},
                label = "Start",
                icon = Icons.Default.PlayArrow,
                variant = MovitFloatPillVariant.Action,
            )
            MovitFab(icon = Icons.Default.Add, onClick = {})
        }
    }
}

@Composable
fun MovitCatalogFeedbackSection() {
    CatalogSection(title = "Feedback & activity") {
        MovitInsightCard(
            title = "Great progress!",
            message = "Your form improved 12% this week. Keep it up.",
            icon = Icons.Default.Check,
            variant = MovitInsightVariant.Success,
        )
        MovitBanner(
            title = "Day complete",
            message = "Training is complete for today.",
            variant = MovitBannerVariant.Success,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitActivityRow(
            value = "28m 45s",
            label = "Today's routine",
            icon = Icons.Default.FitnessCenter,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
    }
}

@Composable
fun MovitCatalogPremiumSection() {
    CatalogSection(title = "Premium patterns", subtitle = "Dashboard hero · KPI · dock") {
        MovitDashboardHero(
            eyebrow = "Today's focus",
            title = "Upper Body Strength",
            subtitle = "Week 2 · Day 3 · 5 exercises · ~38 min",
            progressPercent = 62,
            actionLabel = "View program",
            onActionClick = {},
        )
        MovitMetricRow(
            items = listOf(
                MovitMetricItem("5", "This week"),
                MovitMetricItem("87%", "Form avg", MaterialTheme.colorScheme.secondary),
                MovitMetricItem("12", "Streak", MaterialTheme.colorScheme.tertiary),
            ),
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitKpiGrid(
            items = listOf(
                MovitKpiItem("87%", "Form score", highlighted = true),
                MovitKpiItem("24", "Sessions"),
                MovitKpiItem("1,290", "kcal burned", valueColor = MaterialTheme.colorScheme.tertiary),
                MovitKpiItem("4.2", "Avg rating"),
            ),
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitTimelineList(
            entries = listOf(
                MovitTimelineEntry("Level 2 unlocked", "Jun 2 · Building phase started"),
                MovitTimelineEntry("First body scan", "May 28 · Score 62", MaterialTheme.colorScheme.secondary),
            ),
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitActionDock(
            timerText = "01:30",
            title = "Rest",
            subtitle = "Next: Lunge",
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
    }
}

@Composable
fun MovitCatalogButtonsExtendedSection() {
    CatalogSection(title = "Buttons extended") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitButton("Start Session", {}, leadingIcon = Icons.Default.PlayArrow, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                MovitButton("Tonal", {}, variant = MovitButtonVariant.Tonal, modifier = Modifier.weight(1f))
                MovitButton("Outline", {}, variant = MovitButtonVariant.Outlined, modifier = Modifier.weight(1f))
            }
            MovitButton(
                "Start Free Trial",
                {},
                variant = MovitButtonVariant.Dark,
                trailingIcon = Icons.Default.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
            MovitButton("Small", {}, size = MovitButtonSize.Small)
        }
    }
}

@Composable
fun MovitCatalogListRowsSection() {
    CatalogSection(title = "List rows") {
        MovitListGroup(
            rows = listOf(
                {
                    MovitListRow(
                        title = "Squat",
                        subtitle = "Legs · Bodyweight · 12 reps",
                        icon = Icons.Default.FitnessCenter,
                        onClick = {},
                    )
                },
                {
                    MovitListRow(
                        title = "Push-up",
                        subtitle = "Chest · Bodyweight · 15 reps",
                        icon = Icons.Default.FitnessCenter,
                        iconVariant = MovitIconBoxVariant.Lime,
                        onClick = {},
                    )
                },
                {
                    MovitListRow(
                        title = "Active minutes",
                        subtitle = "Today",
                        icon = Icons.Default.Favorite,
                        iconVariant = MovitIconBoxVariant.Coral,
                        trailingValue = "28",
                        trailingUnit = "m",
                        showChevron = false,
                    )
                },
            ),
        )
    }
}

@Composable
fun MovitCatalogSkeletonSection() {
    CatalogSection(title = "Skeleton loading") {
        MovitSkeletonCard()
    }
}

@Composable
fun MovitCatalogExtendedTokensSection() {
    val movit = MaterialTheme.movitColors
    CatalogSection(title = "Extended tokens", subtitle = "Text shades · tints · veils") {
        MovitCard(variant = MovitCardVariant.Flat) {
            Text("textSecondary", color = movit.textSecondary)
            Text("textTertiary", color = movit.textTertiary, modifier = Modifier.padding(top = MovitSpacing.xs))
            Text("textQuaternary", color = movit.textQuaternary, modifier = Modifier.padding(top = MovitSpacing.xs))
            Row(
                modifier = Modifier.padding(top = MovitSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                MovitTag("primaryTint", variant = MovitTagVariant.Blue)
                MovitTag("limeTint", variant = MovitTagVariant.Lime)
                MovitTag("coralTint", variant = MovitTagVariant.Coral)
            }
        }
    }
}
